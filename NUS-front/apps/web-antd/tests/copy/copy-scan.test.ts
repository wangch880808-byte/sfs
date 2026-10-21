import type { CopyEntry } from './copyScan.ts';

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { COPY_RULES_VERSION, pageOf } from './copyRules.ts';
import { checkEntry, fingerprint, scan, scanSource } from './copyScan.ts';

/**
 * 界面文案检查（V1.0.1.6，docs/ui-copy.md §4）：扫描页面、组件、布局、工具函数、路由、表格与表单适配、登录状态
 * 与中文语言包里用户看得到的文字；称呼「您」、感叹号、宣传腔与行话、开发占位与内部引用、内部标识、英文残留、
 * 非标准术语、中文旁的半角标点与空格一律算命中。规则在 copyRules.ts，扫描在 copyScan.ts。
 *
 * 分批改文案期间，还没改到的地方按 copyBaseline.json 逐条放行（只许删、不许加）；新文案一律不许有命中。
 * 改掉存量问题或基线对不上时重新生成：node apps/web-antd/tests/copy/inventory.ts --write-baseline
 */
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..', '..', '..');
const BASELINE: string[] = JSON.parse(
  readFileSync(join(HERE, 'copyBaseline.json'), 'utf8'),
).findings;
const REGENERATE =
  'node apps/web-antd/tests/copy/inventory.ts --write-baseline';

const counts = (xs: string[]) => {
  const m = new Map<string, number>();
  for (const x of xs) m.set(x, (m.get(x) ?? 0) + 1);
  return m;
};

describe('界面文案（docs/ui-copy.md §4）', () => {
  const result = scan(ROOT);
  const now = counts(result.findings.map((f) => fingerprint(f)));
  const allowed = counts(BASELINE);

  it('扫到了页面、组件、布局、工具函数、路由与语言包里的文案', () => {
    expect(COPY_RULES_VERSION).toBe('nus-ui-copy-rules-v1');
    expect(result.files.length).toBeGreaterThan(250);
    expect(result.entries.length).toBeGreaterThan(3000);
    for (const dir of [
      'views',
      'components',
      'layouts',
      'utils',
      'router',
      'adapter',
      'store',
      'locales/langs/zh-CN',
    ])
      expect(
        result.files.some((f) => f.startsWith(`apps/web-antd/src/${dir}/`)),
      ).toBe(true);
    expect(result.files.filter((f) => /\.(?:spec|test)\.ts$/.test(f))).toEqual(
      [],
    );
    expect(
      result.files.filter((f) => !f.startsWith('apps/web-antd/src/')),
    ).toEqual([]);
    expect(
      result.entries.some(
        (e) => e.where === 'page.auth.login' && e.text === '登录',
      ),
    ).toBe(true);
    expect(pageOf('apps/web-antd/src/views/edu/homework/index.vue')).toBe(
      '实训任务',
    );
    expect(pageOf('apps/web-antd/src/views/system/user/index.vue')).toBe(
      '系统管理',
    );
    expect(
      pageOf('apps/web-antd/src/views/edu/student/homework/submit.vue'),
    ).toBe('学生 · 任务中心');
    expect(pageOf('apps/web-antd/src/views/edu/message/index.vue')).toBe(
      '留言互动',
    );
    expect(pageOf('apps/web-antd/src/locales/langs/zh-CN/page.json')).toBe(
      '语言包',
    );
  });

  it('用户看得到的文字没有新的违规（基线以外的命中）', () => {
    const extra = new Set<string>();
    for (const f of result.findings) {
      const key = fingerprint(f);
      if ((now.get(key) ?? 0) > (allowed.get(key) ?? 0))
        extra.add(
          `${f.file}:${f.line} ${f.rule} ${f.hit}：${f.text.slice(0, 80)}${f.hint ? `（${f.hint}）` : ''}`,
        );
    }
    expect([...extra], '改文案；确属例外时写进 copyRules.ts 的 EXEMPT').toEqual(
      [],
    );
  });

  it('基线只许减少：基线里的每一条都还在（改掉了就重新生成基线）', () => {
    const stale = [...allowed]
      .filter(([key, n]) => (now.get(key) ?? 0) < n)
      .map(([key]) => key);
    expect(stale, `运行 ${REGENERATE}`).toEqual([]);
  });
});

describe('文案取词', () => {
  it('模板取文字、显示属性与表达式里的字符串，跳过比较、console、import、字段映射、样式与日期格式', () => {
    const sfc = [
      '<script setup lang="ts">',
      "import { message } from 'ant-design-vue';",
      "import dayjs from 'dayjs';",
      '',
      'const props = defineProps<{ status: string }>();',
      "const columns = [{ field: 'name', title: '名称' }, { field: 'id', title: 'Owner' }];",
      "const tree = { fieldNames: { label: 'name', value: 'id' } };",
      "const tooltip = { extraCssText: 'border-radius: 4px;' };",
      "const KIND_COLOR_MAP = { text: 'blue' };",
      'function onSave() {',
      "  if (props.status === '已归档') return '';",
      "  console.warn('调试输出');",
      "  message.success('Saved');",
      "  message.success('保存成功');",
      "  return dayjs().format('YYYY年M月D日');",
      '}',
      '</script>',
      '',
      '<template>',
      '  <div>',
      '    共 {{ total }} 条，已选 {{ selected.length }} 条',
      '    <a-input placeholder="Search" class="w-full" />',
      `    <a-button title="刷新" @click="onSave">{{ loading ? '保存中…' : 'Save' }}</a-button>`,
      `    <span v-if="status === '已打回'">重做</span>`,
      '    <a-date-picker value-format="YYYY年MM月DD日" />',
      '  </div>',
      '</template>',
    ].join('\n');
    const entries = scanSource('apps/web-antd/src/views/edu/demo.vue', sfc);
    expect(entries.map((e) => [e.kind, e.where, e.text])).toEqual([
      ['text', '', '共 {…} 条，已选 {…} 条'],
      ['attr', 'placeholder', 'Search'],
      ['attr', 'title', '刷新'],
      ['expr', '{{ }}', '保存中…'],
      ['expr', '{{ }}', 'Save'],
      ['text', '', '重做'],
      ['script', 'title:', '名称'],
      ['script', 'title:', 'Owner'],
      ['script', 'message.success()', 'Saved'],
      ['script', 'message.success()', '保存成功'],
    ]);
    expect(entries[0]?.line).toBe(21);
    expect(entries.at(-1)?.line).toBe(14);
  });

  it('.ts 只取显示位置的英文；语言包取每个字符串值，位置写语言包键', () => {
    const ts = [
      "export const getStatusLabel = (s: number) => (s === 1 ? 'Done' : '进行中');",
      "const skinName = () => 'oxide-dark';",
    ].join('\n');
    expect(
      scanSource('apps/web-antd/src/utils/demo.ts', ts).map((e) => e.text),
    ).toEqual(['Done', '进行中']);
    const json = '{\n  "auth": {\n    "login": "登录 {0}"\n  }\n}\n';
    expect(
      scanSource('apps/web-antd/src/locales/langs/zh-CN/page.json', json),
    ).toMatchObject([
      { kind: 'script', line: 3, text: '登录 {…}', where: 'page.auth.login' },
    ]);
  });
});

describe('文案规则', () => {
  const HOMEWORK = 'apps/web-antd/src/views/edu/homework/index.vue';
  const entry = (text: string, file = HOMEWORK): CopyEntry => ({
    file,
    kind: 'text',
    line: 1,
    page: pageOf(file),
    text,
    where: '',
  });
  const hits = (text: string, file?: string) =>
    checkEntry(entry(text, file)).map((f) => `${f.rule}:${f.hit}`);
  const hintOf = (text: string, file?: string) =>
    checkEntry(entry(text, file))[0]?.hint ?? '';

  it('称呼用「你」，不用感叹号', () => {
    expect(hits('请确认您的新密码')).toEqual(['honorific:您']);
    expect(hits('保存成功！')).toEqual(['exclamation:！']);
    expect(hits('删除成功!')).toEqual(['exclamation:!']);
    expect(hits('请确认你的新密码')).toEqual([]);
  });

  it('宣传腔与行话；等级闯关的「解锁」不算', () => {
    expect(hits('一站式实训平台')).toEqual(['tone:一站式']);
    expect(hits('开启你的学习之旅')).toEqual(['tone:开启你的学习之旅']);
    expect(hits('口径一致，无需校准')).toEqual(['tone:口径']);
    expect(hits('对接教务系统')).toEqual(['tone:对接']);
    expect(hintOf('对接教务系统')).toContain('接入');
    expect(hits('完成后解锁更多功能')).toEqual(['tone:解锁更多']);
    expect(hits('已解锁')).toEqual([]);
    expect(hits('上一等级达标后自动解锁')).toEqual([]);
  });

  it('开发占位与内部引用；「开发中」只认占位说法', () => {
    expect(hits('趋势图待接入后端数据')).toEqual(['internal-ref:待接入']);
    expect(hits('当前为测试数据')).toEqual(['internal-ref:测试数据']);
    expect(hits('功能开发中，敬请期待')).toEqual(['internal-ref:功能开发中']);
    expect(hits('TODO 补充说明')).toEqual(['internal-ref:TODO']);
    expect(hits('使用 mock 数据')).toEqual(['internal-ref:mock']);
    expect(hits('按 PRD 实现')).toEqual(['internal-ref:PRD']);
    expect(hits('V1.0.1.6 新增的提示')).toContain('internal-ref:V1.0.1.6');
    expect(hits('培养软件开发中的协作能力')).toEqual([]);
  });

  it('内部标识：字段名、枚举原值与原样显示的空值', () => {
    expect(hits('状态 record_status 无效')).toEqual([
      'internal-id:record_status',
    ]);
    expect(hits('错误码 HOMEWORK_REDO')).toEqual(['internal-id:HOMEWORK_REDO']);
    expect(hits('请输入 accessKey')).toEqual(['internal-id:accessKey']);
    expect(hits('成绩：null')).toEqual(['internal-id:null']);
  });

  it('英文残留；规范 §2 的保留英文、单位、按键名、文件扩展名不算，「ID」只在管理端可用', () => {
    expect(hits('查看 payload')).toEqual(['english:payload']);
    expect(hits('选择 YAML 文件')).toEqual(['english:YAML']);
    // 规范 §2 定稿后，XLSX、JSON 等文件格式名属于保留英文
    expect(hits('选择 XLSX 文件')).toEqual([]);
    expect(hits('按 JSON 格式填写')).toEqual([]);
    expect(hits('AI 初评')).toEqual([]);
    expect(hits('导出 PDF、Word 与 Excel')).toEqual([]);
    expect(hits('OpenAI、DeepSeek 与 Ollama 模型')).toEqual([]);
    expect(hits('OAuth 2.0 登录，H5 与 App 可用')).toEqual([]);
    expect(hits('支持 PDF/Word 文件')).toEqual([]);
    expect(hits('单个文件不超过 500 MB')).toEqual([]);
    expect(hits('按 Enter 发送，Shift + Enter 换行')).toEqual([]);
    expect(hits('下载题库导入模板.xlsx')).toEqual([]);
    expect(hits('支持 .jpg、.png 格式')).toEqual([]);
    expect(hits('任务 ID 缺失')).toEqual(['english:ID']);
    expect(
      hits('用户 ID', 'apps/web-antd/src/views/system/user/data.ts'),
    ).toEqual([]);
  });

  it('非标准术语给出标准写法；「消息」「会话」只在留言模块里查', () => {
    expect(hits('新增作业')).toEqual(['variant:作业', 'variant:新增']);
    expect(hits('实训作业列表')).toEqual(['variant:实训作业']);
    expect(hintOf('实训作业列表')).toContain('「实训任务」');
    expect(hits('已退回')).toEqual(['variant:退回']);
    expect(hintOf('已退回')).toContain('「打回」');
    expect(hits('教师评阅')).toEqual(['variant:评阅']);
    expect(hintOf('教师评阅')).toContain('「批改」');
    expect(hits('查看作答记录')).toEqual(['variant:作答记录']);
    expect(hits('归档课程')).toEqual(['variant:归档']);
    expect(hits('站内信')).toEqual(['variant:站内信']);
    expect(hits('技能达标率')).toEqual(['variant:技能']);
    expect(hintOf('技能达标率')).toContain('「能力」');
    const message = 'apps/web-antd/src/views/edu/message/labels.ts';
    expect(hits('关闭会话', message)).toEqual(['variant:会话']);
    expect(hits('暂无消息记录', message)).toEqual(['variant:消息']);
    expect(hits('关闭会话')).toEqual([]);
    expect(
      hits('新建会话', 'apps/web-antd/src/views/ai/chat/index/index.vue'),
    ).toEqual([]);
    expect(hits('创建时间')).toEqual([]);
    expect(hits('还原默认设置')).toEqual([]);
  });

  it('标点与空格：半角标点、括号、三个点、引号，中文与英文、数字之间与数字和单位之间的空格', () => {
    expect(hits('AI初评')).toEqual(['punct:AI初']);
    expect(hits('第1版')).toEqual(['punct:第1']);
    expect(hits('单个文件不超过 500MB')).toEqual(['punct:500MB']);
    expect(hits('请选择班级,再导入')).toEqual(['punct:级,']);
    expect(hits('{…}, 开始今天的工作')).toEqual(['punct:, 开']);
    expect(hits('权重(%)')).toEqual(['punct:重(']);
    expect(hits('加载中...')).toEqual(['punct:...']);
    expect(hits('点"保存"')).toEqual(['punct:"保存"']);
    expect(hits('点“保存”')).toEqual(['punct:“']);
    expect(hits('全部淘汰， 或填写原因')).toEqual([
      'punct:多余空格「淘汰， 或」',
    ]);
    expect(hits('提交 记录')).toEqual(['punct:多余空格「提交 记录」']);
  });

  it('地址、路径、插值、时间、百分比与「·」分隔不误报', () => {
    expect(hits('例如 http://127.0.0.1:18000；不带用户名')).toEqual([]);
    expect(hits('外部链接以 http:// 或 https:// 开头')).toEqual([]);
    expect(hits('文件保存在 /data/upload/2026 目录')).toEqual([]);
    expect(hits('小程序页面 pages/auth/scan/index')).toEqual([]);
    expect(hits('共 {…} 条，已选 {…} 条')).toEqual([]);
    expect(hits('第{…}版')).toEqual([]);
    expect(hits('截止时间 2026-10-06 19:46')).toEqual([]);
    expect(hits('达标率 83.2%，均分 88 分，3 份待批改')).toEqual([]);
    expect(hits('Web 前端开发实训 · 软件工程 2025 级 1 班')).toEqual([]);
    expect(hits('入选 / 排除，第 1/2 页')).toEqual([]);
    expect(hits('<div class="copy">复制</div>')).toEqual([]);
  });
});
