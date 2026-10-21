#!/usr/bin/env node
// 界面截图：用本机 Chrome 的无头模式与调试协议（只用 Node 自带模块与 WebSocket），按角色登录后
// 对页面清单在指定宽度与深色/浅色主题下截图，存成 PNG。只访问本机地址；口令从状态目录读取，不打印。
//
// 用法：node deploy/tests/ui/shots.mjs --out <目录> [--base http://localhost:5666] [--themes dark,light]
//        [--widths 1440,1024] [--roles anon,admin,teacher,student] [--pages workspace,correct,名字=/路径]
//        --pages 里写“名字=/路径”可以临时加一页（对 --roles 里的每个角色都截）；--prefix 给文件名加前缀
import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, value, index, all) => {
    if (value.startsWith('--')) pairs.push([value.slice(2), all[index + 1]]);
    return pairs;
  }, []),
);
const BASE = args.base || 'http://localhost:5666';
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE)) {
  console.error('只允许本机地址');
  process.exit(2);
}
const OUT = args.out;
if (!OUT) {
  console.error('缺少 --out');
  process.exit(2);
}
const STATE = process.env.NUS_E2E_STATE || join(homedir(), '.cache', 'nus-e2e');
const THEMES = (args.themes || 'dark,light').split(',');
const WIDTHS = (args.widths || '1440').split(',').map(Number);
const ROLES = (args.roles || 'anon,admin,teacher,student').split(',');
const PAGE_ARGS = args.pages ? args.pages.split(',') : [];
const ONLY = PAGE_ARGS.length > 0 ? new Set(PAGE_ARGS.filter((item) => !item.includes('='))) : null;
const CUSTOM = PAGE_ARGS.filter((item) => item.includes('=')).map((item) => item.split('='));
const PREFIX = args.prefix ? `${args.prefix}-` : '';
const CHROME = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
// 开发服务器用 history 路由；生产构建用 hash 路由（加 --hash true）
const HASH = args.hash === 'true';
const urlOf = (path) => `${BASE}${HASH ? '/#' : ''}${path}`;

const ids = JSON.parse(readFileSync(join(STATE, 'demo-ids.json'), 'utf8'));
const accounts = JSON.parse(readFileSync(join(STATE, 'demo-accounts.json'), 'utf8'));
const credentials = {
  admin: ['admin', readFileSync(join(STATE, 'admin.pw'), 'utf8').trimEnd()],
  teacher: ['demoteacher', accounts.demoteacher.password],
  student: ['demostudent1', accounts.demostudent1.password],
};
const [hw1, hw2, hw3] = ids.homeworkIds;
const PAGES = {
  anon: [['login', '/auth/login']],
  admin: [
    ['workspace', '/workspace'],
    ['class', '/edu/class'],
    ['teacher-list', '/edu/teacher'],
    ['student-list', '/edu/student'],
    ['semester', '/edu/semester'],
    ['course', '/capacity/course_manager/course'],
    ['homework', '/capacity/homework_manager/homework'],
    ['user', '/system/user'],
    ['menu', '/system/menu'],
    ['job', '/infra/job'],
    ['ai-chat', '/ai/chat'],
  ],
  teacher: [
    ['workspace', '/workspace'],
    ['course', '/capacity/course_manager/course'],
    ['course-detail', `/capacity/course/detail/${ids.courseId}`],
    ['homework', '/capacity/homework_manager/homework'],
    ['homework-detail', `/capacity/homework_manager/homework/detail/${hw1}`],
    ['correct', `/capacity/homework_manager/homework/correct/${hw1}`],
    ['message', '/edu/message'],
  ],
  student: [
    ['workspace', '/workspace'],
    ['course', '/student/course'],
    ['course-detail', `/student/course/detail/${ids.courseId}`],
    ['homework', '/student/homework'],
    ['submit', `/student/homework/submit/${hw3}`],
    ['feedback', `/student/homework/feedback/${hw1}`],
    ['resubmit', `/student/homework/submit/${hw2}`],
    ['capacity', '/student/capacity'],
    ['message', '/student/message'],
    ['news', '/student/news'],
    ['profile', '/student/profile'],
  ],
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class Cdp {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.seq = 0;
    this.pending = new Map();
    this.listeners = new Set();
    this.ws.addEventListener('message', (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
      } else if (msg.method) {
        for (const listener of this.listeners) listener(msg);
      }
    });
  }
  ready() {
    return new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true });
      this.ws.addEventListener('error', reject, { once: true });
    });
  }
  send(method, params = {}, sessionId) {
    const id = ++this.seq;
    this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
}

async function openPage(cdp) {
  const { browserContextId } = await cdp.send('Target.createBrowserContext');
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank', browserContextId });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  const page = {
    send: (method, params) => cdp.send(method, params, sessionId),
    inflight: new Set(),
    close: () => cdp.send('Target.disposeBrowserContext', { browserContextId }),
  };
  cdp.listeners.add((msg) => {
    if (msg.sessionId !== sessionId) return;
    if (msg.method === 'Network.requestWillBeSent') page.inflight.add(msg.params.requestId);
    if (msg.method === 'Network.loadingFinished' || msg.method === 'Network.loadingFailed') {
      page.inflight.delete(msg.params.requestId);
    }
  });
  await page.send('Page.enable');
  await page.send('Network.enable');
  await page.send('Runtime.enable');
  return page;
}

async function evaluate(page, expression) {
  const result = await page.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
  return result.result.value;
}

// 等待页面请求全部结束并稳定 800ms，加载动画消失；最多等 15 秒
async function settle(page) {
  const deadline = Date.now() + 15000;
  let quietSince = Date.now();
  while (Date.now() < deadline) {
    const busy = page.inflight.size > 0 ||
      (await evaluate(page, `!!document.querySelector('.ant-spin-spinning, .vxe-loading.is--visible, #__app-loading__')`));
    if (busy) quietSince = Date.now();
    else if (Date.now() - quietSince > 800) return;
    await sleep(150);
  }
}

async function go(page, path) {
  await page.send('Page.navigate', { url: urlOf(path) });
  await sleep(600);
  await settle(page);
}

async function login(page, [username, password]) {
  await page.send('Page.navigate', { url: urlOf('/auth/login') });
  await sleep(1500);
  await settle(page);
  const filled = await evaluate(page, `(() => {
    const set = (el, v) => {
      const desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
      desc.set.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    const user = document.querySelector('input:not([type=password]):not([type=hidden]):not([type=checkbox])');
    const pass = document.querySelector('input[type=password]');
    if (!user || !pass) return false;
    set(user, ${JSON.stringify(username)});
    set(pass, ${JSON.stringify(password)});
    return true;
  })()`);
  if (!filled) throw new Error('登录表单未找到');
  await sleep(200);
  await evaluate(page, `(() => {
    const button = [...document.querySelectorAll('button')].find((b) => /登\\s*录/.test(b.textContent || ''));
    button && button.click();
    return !!button;
  })()`);
  for (let i = 0; i < 60; i++) {
    await sleep(250);
    const where = await evaluate(page, 'location.pathname + location.hash');
    if (!where.includes('/auth/')) break;
  }
  await settle(page);
}

async function setTheme(page, mode) {
  const changed = await evaluate(page, `(() => {
    const key = Object.keys(localStorage).find((k) => k.endsWith('-preferences'));
    if (!key) return false;
    const stored = JSON.parse(localStorage.getItem(key));
    const prefs = stored && stored.value ? stored.value : stored;
    prefs.theme = { ...(prefs.theme || {}), mode: ${JSON.stringify(mode)} };
    localStorage.setItem(key, JSON.stringify(stored));
    return true;
  })()`);
  if (changed) {
    await page.send('Page.reload', { ignoreCache: false });
    await sleep(1200);
    await settle(page);
  }
}

async function main() {
  mkdirSync(OUT, { recursive: true });
  const profile = mkdtempSync(join(tmpdir(), 'nus-shots-'));
  const port = 9300 + Math.floor(Math.random() * 500);
  const chrome = spawn(CHROME, [
    '--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, '--no-first-run',
    '--no-default-browser-check', '--no-proxy-server', '--hide-scrollbars', '--force-color-profile=srgb',
    '--lang=zh-CN', 'about:blank',
  ], { stdio: 'ignore' });
  let cdp;
  try {
    let endpoint;
    for (let i = 0; i < 50 && !endpoint; i++) {
      await sleep(200);
      endpoint = await fetch(`http://127.0.0.1:${port}/json/version`).then((r) => r.json())
        .then((v) => v.webSocketDebuggerUrl).catch(() => undefined);
    }
    if (!endpoint) throw new Error('Chrome 调试端口未就绪');
    cdp = new Cdp(endpoint);
    await cdp.ready();
    const written = [];
    for (const role of ROLES) {
      const pages = [
        ...(PAGES[role] || []).filter(([name]) => ONLY && ONLY.size > 0 ? ONLY.has(name) : CUSTOM.length === 0),
        ...(role === 'anon' ? [] : CUSTOM),
      ];
      if (pages.length === 0) continue;
      const page = await openPage(cdp);
      await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
      if (role === 'anon') {
        await page.send('Page.navigate', { url: urlOf('/auth/login') });
        await sleep(1500);
        await settle(page);
      } else {
        await login(page, credentials[role]);
      }
      for (const theme of THEMES) {
        await setTheme(page, theme);
        for (const width of WIDTHS) {
          await page.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
          for (const [name, path] of pages) {
            if (role !== 'anon') await go(page, path);
            else await settle(page);
            const { data } = await page.send('Page.captureScreenshot', { format: 'png' });
            const file = join(OUT, `${PREFIX}${role}-${name}-${theme}-${width}.png`);
            writeFileSync(file, Buffer.from(data, 'base64'));
            written.push(file);
          }
        }
      }
      await page.close();
    }
    console.log(`截图 ${written.length} 张，目录：${OUT}`);
  } finally {
    try { cdp && cdp.ws.close(); } catch {}
    chrome.kill('SIGTERM');
    await sleep(300);
    rmSync(profile, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error('截图失败：', error.message);
  process.exit(1);
});
