#!/bin/sh
# Standalone Ubuntu 24.04 domain migration for NUS behind a school HTTPS gateway.
# No backup is created. Run --help or --self-test before use.
set -eu
command -v python3 >/dev/null 2>&1 || { echo '缺少 python3，请先安装 Ubuntu 的 python3。' >&2; exit 1; }
exec python3 - "$@" <<'PY'
import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.parse import urlsplit

DEFAULT_SITE = 'https://nus.zknu.edu.cn'
DEST = Path('/srv/nus/config/domain-nginx')
# Only application-managed file references, never API providers, secrets or audit logs.
FIELDS = {
    'infra_file': {'url'},
    'system_users': {'avatar'},
    'system_user_profile': {'avatar'},
    'system_social_user': {'avatar'},
    'capacity_course': {'course_image_url', 'course_introduction'},
    'edu_course_resource': {'file_url', 'standard_audio_url'},
    'edu_homework': {'content', 'description'},
    'edu_homework_draft': {'content', 'attachment_urls'},
    'edu_homework_record': {'content', 'attachment_urls', 'comment'},
    'edu_import_batch': {'source_file_url', 'error_file_url'},
    'ai_chat_message': {'content', 'attachment_urls'},
    'ai_chat_role': {'avatar'},
    'ai_image': {'pic_url'},
    'edu_message': {'content'},
    'system_notice': {'content'},
}


def require(ok, msg):
    if not ok:
        raise RuntimeError(msg)


def origin(value, https=False):
    p = urlsplit(value.strip())
    require(p.scheme in (('https',) if https else ('http', 'https')),
            '地址必须使用 ' + ('https://' if https else 'http:// 或 https://'))
    require(p.hostname and re.fullmatch(r'[A-Za-z0-9.-]+', p.hostname)
            and not p.username and not p.password and p.path in ('', '/')
            and not p.query and not p.fragment, '请输入完整站点地址，不带路径、账号或查询参数')
    port = p.port
    require(port is None or 1 <= port <= 65535, '端口不正确')
    return p.scheme + '://' + p.hostname.lower() + ((':' + str(port)) if port else '')


def parse_env(text):
    result = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        require('=' in line, '环境文件存在非 KEY=VALUE 行')
        k, v = line.split('=', 1)
        require(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', k), '环境变量名不合法')
        require(k not in result, '环境文件有重复字段：' + k)
        parts = shlex.split(v, comments=False, posix=True)
        require(len(parts) <= 1, '环境文件值需要引号：' + k)
        result[k] = parts[0] if parts else ''
    return result


def update_env(text, changes):
    lines = [s for s in text.splitlines() if s.split('=', 1)[0] not in changes]
    return '\n'.join(lines + [k + '=' + v for k, v in changes.items()]) + '\n'


def nginx_config(text, site, monitor):
    text = text.replace('include mime.types;', 'include /etc/nginx/mime.types;')
    marker = '  server {\n    listen 8082;'
    require(text.count(marker) == 1 and text.count('    listen 8080;') == 1,
            'Nginx 结构不匹配，停止以免覆盖自定义配置')
    platform, monitoring = text.split(marker, 1)
    require('proxy_pass http://backend:48080;' in platform, '没有找到平台反向代理')
    # Dedicated HTTPS gateway deployment: use fixed canonical origin, not arbitrary headers.
    platform = re.sub(r'proxy_set_header X-Forwarded-Proto [^;]+;',
                      'proxy_set_header X-Forwarded-Proto https;', platform)
    platform = re.sub(r'proxy_set_header Host [^;]+;',
                      'proxy_set_header Host ' + urlsplit(site).netloc + ';', platform)
    platform = re.sub(r'\s*proxy_set_header X-Forwarded-(?:Host|Port) [^;]+;', '', platform)
    platform = platform.replace('proxy_set_header X-Forwarded-Proto https;',
        'proxy_set_header X-Forwarded-Proto https;\n'
        '      proxy_set_header X-Forwarded-Host ' + urlsplit(site).netloc + ';\n'
        '      proxy_set_header X-Forwarded-Port ' + str(urlsplit(site).port or 443) + ';')
    if monitor:
        monitoring = re.sub(r'proxy_set_header X-Forwarded-Proto [^;]+;',
                           'proxy_set_header X-Forwarded-Proto https;', monitoring)
        monitoring = re.sub(r'proxy_set_header Host [^;]+;',
                           'proxy_set_header Host ' + urlsplit(monitor).netloc + ';', monitoring)
        monitoring = re.sub(r'\s*proxy_set_header X-Forwarded-(?:Host|Port) [^;]+;', '', monitoring)
        monitoring = monitoring.replace('proxy_set_header X-Forwarded-Proto https;',
            'proxy_set_header X-Forwarded-Proto https;\n'
            '      proxy_set_header X-Forwarded-Host ' + urlsplit(monitor).netloc + ';\n'
            '      proxy_set_header X-Forwarded-Port ' + str(urlsplit(monitor).port or 443) + ';')
    return platform + marker + monitoring


def compose_config(text):
    match = re.search(r'^  frontend:\n(.*?)(?=^  [\w-]+:\n|^[^ \n#]|\Z)', text, re.M | re.S)
    require(match, 'Compose 没有 frontend 服务')
    block = match.group(0)
    mount = '      - /srv/nus/config/domain-nginx:/etc/nus-domain:ro\n'
    command = '    command: ["nginx", "-c", "/etc/nus-domain/nginx.conf", "-g", "daemon off;"]\n'
    if '    command:' in block:
        require(command in block, 'frontend 有自定义 command，停止以免覆盖')
    else:
        block = block.replace('  frontend:\n', '  frontend:\n' + command, 1)
    if mount not in block:
        require('/etc/nus-domain' not in block.replace(command, ''), '存在冲突的域名配置挂载')
        if '    volumes:\n' in block:
            block = block.replace('    volumes:\n', '    volumes:\n' + mount, 1)
        else:
            require('    networks:\n' in block, 'Compose 网络结构不匹配')
            block = block.replace('    networks:\n', '    volumes:\n' + mount + '    networks:\n', 1)
    return text[:match.start()] + block + text[match.end():]


def lit(s):
    return "CONVERT(0x" + s.encode('utf-8').hex() + " USING utf8mb4)" if s else "''"


def rewrite_expression(column, old, new):
    expr = column
    for path in ('/admin-api/infra/file/', '/app-api/infra/file/'):
        for escaped in (False, True):
            a, b = old + path, new + path
            if escaped:
                a, b = a.replace('/', '\\/'), b.replace('/', '\\/')
            expr = 'REPLACE(' + expr + ',' + lit(a) + ',' + lit(b) + ')'
    return expr


def atomic(path, text, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.' + path.name + '.')
    try:
        with os.fdopen(fd, 'w') as f:
            f.write(text)
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def confirm_apply(terminal='/dev/tty'):
    # Buffered r+ streams require seeking, which terminals do not support.
    # stdin holds the embedded Python program, so read confirmation from the TTY.
    with open(terminal, 'r') as reader, open(terminal, 'w') as writer:
        writer.write('确认无需备份并应用以上修改？输入 YES：')
        writer.flush()
        require(reader.readline().strip() == 'YES', '已取消，未应用修改')


def self_test():
    e = 'SECRET=\'abc$xyz\'\nNUS_SITE_URL=http://10.249.1.63\n'
    assert parse_env(e)['SECRET'] == 'abc$xyz'
    assert parse_env(update_env(e, {'NUS_SITE_URL': DEFAULT_SITE}))['SECRET'] == 'abc$xyz'
    for bad in ('https://a/b', 'https://a;rm/', 'https://u:p@a', 'https://a/?q=1', 'http://a'):
        try:
            origin(bad, True)
        except (ValueError, RuntimeError):
            pass
        else:
            raise AssertionError(bad)
    n = 'http {\n  server {\n    listen 8080;\nproxy_pass http://backend:48080;\nproxy_set_header Host $host;\nproxy_set_header X-Forwarded-Proto $scheme;\n}\n  server {\n    listen 8082;\nproxy_set_header X-Forwarded-Proto $scheme;\n}\n}\n'
    out = nginx_config(n, DEFAULT_SITE, None)
    assert out == nginx_config(out, DEFAULT_SITE, None)
    assert out.count('X-Forwarded-Proto https;') == 1
    c = 'services:\n  frontend:\n    image: test\n    networks:\n      - test\nnetworks:\n  test:\n'
    out = compose_config(c)
    assert out == compose_config(out)
    assert 'networks:\n  test:' in out
    assert origin(DEFAULT_SITE + '/') == DEFAULT_SITE
    print('PASS：参数校验、密钥保留、Nginx/Compose 重复运行、监控隔离测试。')


def main():
    ap = argparse.ArgumentParser(prog='configure-domain.sh', description='学校网关 HTTPS 域名切换，不备份、不签发证书、不操作学校网关。')
    ap.add_argument('--site-url', default=DEFAULT_SITE)
    ap.add_argument('--old-url', action='append', default=['http://10.249.1.63'],
                    help='补充历史站点地址，可重复指定；默认包含本次学校内网 IP')
    ap.add_argument('--monitor-url', help='已配置好、独立的 HTTPS 监控入口，不能与平台同源')
    ap.add_argument('--keep-monitor', action='store_true', help='保留现有监控地址和 Cookie 设置')
    ap.add_argument('--yes', action='store_true', help='跳过最终执行确认')
    ap.add_argument('--dry-run', action='store_true', help='仅预检查，不改服务器配置和数据库')
    ap.add_argument('--self-test', action='store_true', help='无 Docker、无服务器修改的本地测试')
    args = ap.parse_args()
    if args.self_test:
        self_test(); return
    require(os.geteuid() == 0, '请使用 sudo bash configure-domain.sh')
    for executable in ('docker', 'curl'):
        require(shutil.which(executable), '缺少命令：' + executable)
    import fcntl
    lock = open('/run/nus-configure-domain.lock', 'w')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    require(not (args.keep_monitor and args.monitor_url), '监控参数不能同时指定')
    site = origin(args.site_url, True)
    env_file = Path('/srv/nus/config/.env')
    env_text = env_file.read_text()
    cfg = parse_env(env_text)
    require(cfg.get('DATA_ROOT', '/srv/nus') == '/srv/nus', '仅支持默认 /srv/nus 部署目录')
    release = Path('/srv/nus/current').resolve(strict=True)
    cp = release / 'runtime/compose.yaml'
    project = cfg.get('COMPOSE_PROJECT_NAME', 'nus')
    old = origin(cfg['NUS_SITE_URL'])
    monitor = origin(args.monitor_url, True) if args.monitor_url else None
    require(not monitor or monitor != site, '监控必须是独立域名或端口，不能填写平台同源地址')
    changes = {'NUS_SITE_URL': site}
    if monitor:
        changes.update(NUS_SKYWALKING_URL=monitor + '/', NUS_MONITOR_COOKIE_SECURE='true')
    new_env = update_env(env_text, changes)
    new_cfg = parse_env(new_env)
    child_env = {**os.environ, **cfg}

    def run(cmd, data=None, env=None):
        r = subprocess.run(cmd, input=data, text=True, capture_output=True, env=env or child_env)
        if r.returncode:
            # Never expose the environment, SQL or a resolved Compose document on failure.
            err = r.stderr[-2400:]
            for key, value in cfg.items():
                if value and any(x in key for x in ('PASSWORD', 'KEY', 'SECRET', 'TOKEN')):
                    err = err.replace(value, '[REDACTED]')
            raise RuntimeError('命令执行失败：' + cmd[0] + '\n' + err)
        return r.stdout

    def dc(*parts, compose_path=cp, env_path=env_file):
        return run(['docker', 'compose', '--project-name', project, '--env-file', str(env_path),
                    '-f', str(compose_path), *parts])

    def cid(service):
        ids = dc('ps', '-a', '-q', service).split()
        require(len(ids) == 1, service + ' 必须有一个已创建的容器')
        return ids[0]

    mysql, backend, frontend = (cid(s) for s in ('mysql', 'backend', 'frontend'))
    db = cfg.get('NUS_DB_NAME', 'nus')
    require(re.fullmatch(r'[A-Za-z0-9_]+', db), '数据库名不符合预期')

    def sql(query):
        # Password travels via subprocess environment, not the command-line argument.
        return run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', mysql, 'mysql', '-uroot',
                    '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4', db],
                   query, {**child_env, 'MYSQL_PWD': cfg['NUS_DB_ROOT_PASSWORD']})

    require(sql('SELECT 1;').strip() == '1', '数据库不可用')
    sql('SELECT metadata_key,metadata_value FROM nus_deployment_metadata LIMIT 0;')
    require(sql('SELECT COUNT(*) FROM infra_file_config WHERE master=1 AND storage=10 AND deleted=0;').strip() == '1',
            '没有唯一的本地主文件配置，停止修改')
    rows = sql("SELECT c.TABLE_NAME,c.COLUMN_NAME,t.ENGINE FROM information_schema.COLUMNS c JOIN information_schema.TABLES t ON c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND c.DATA_TYPE IN ('varchar','char','text','mediumtext','longtext','json');")
    allcols = [r.split('\t') for r in rows.splitlines()]
    targets = [(t, c) for t, c, engine in allcols if c in FIELDS.get(t, set())]
    for t, c, engine in allcols:
        if (t, c) in targets or t in ('infra_file_config', 'infra_config', 'nus_deployment_metadata'):
            require(engine == 'InnoDB', '发现非事务表：' + t)
    # Remember origins only, not backups; permits retries after a partially applied deployment.
    state = DEST / 'origins.json'
    origins = json.loads(state.read_text()) if state.exists() else []
    origins = sorted(set(origin(u) for u in origins + [old] + args.old_url))
    # Include the preexisting master domain if it differs from .env.
    file_origin = sql("SELECT JSON_UNQUOTE(JSON_EXTRACT(config,'$.domain')) FROM infra_file_config WHERE master=1 AND storage=10 AND deleted=0;").strip()
    if file_origin and file_origin != 'NULL':
        origins = sorted(set(origins + [origin(file_origin)]))
    print('平台：' + old + ' → ' + site)
    print('监控：' + (monitor or cfg.get('NUS_SKYWALKING_URL', '未配置')))
    if not monitor:
        print('监控保留原地址与 Cookie 设置；其外部 HTTPS 接入本次不完成。')
    print('历史文件链接检查范围：' + ', '.join(sorted({t for t, _ in targets})))
    print('不备份；不修改审计日志/API 密钥/上传文件内容；预计短暂中断前后端。')

    with tempfile.TemporaryDirectory(prefix='nus-domain-') as tmp:
        stage = Path(tmp)
        os.chmod(stage, 0o755)
        if (DEST / 'nginx.conf').exists():
            original_nginx = (DEST / 'nginx.conf').read_text()
        else:
            run(['docker', 'cp', frontend + ':/etc/nginx/nginx.conf', str(stage / 'original.conf')])
            original_nginx = (stage / 'original.conf').read_text()
        new_nginx = nginx_config(original_nginx, site, monitor)
        new_compose = compose_config(cp.read_text())
        atomic(stage / 'nginx.conf', new_nginx, 0o644)
        atomic(stage / 'compose.yaml', new_compose)
        atomic(stage / 'site.env', new_env)
        # Compose validation with the future environment, no service mutations.
        run(['docker', 'compose', '--project-name', project, '--env-file', str(stage / 'site.env'),
             '-f', str(stage / 'compose.yaml'), 'config', '--quiet'], env={**os.environ, **new_cfg})
        fi = json.loads(run(['docker', 'inspect', frontend]))[0]
        if not args.dry_run:
            # Syntax validation only; dummy DNS permits retries with stopped services.
            run(['docker', 'run', '--rm', '--pull=never', '--network', 'none',
                 '--add-host', 'backend:127.0.0.1', '--add-host', 'skywalking-ui:127.0.0.1',
                 '--mount', 'type=bind,src=' + str(stage) + ',dst=/etc/nus-domain,readonly',
                 '--entrypoint', 'nginx', fi['Image'], '-t', '-c', '/etc/nus-domain/nginx.conf'])
        print('配置预检查完成。')
        if args.dry_run:
            print('只读预检查结束：未应用配置/数据库，未重启服务；未运行容器内 nginx -t。'); return
        if not args.yes:
            confirm_apply()
        # Persist retry metadata before changing URLs. This contains no credentials/data backup.
        atomic(state, json.dumps(origins) + '\n')
        dc('stop', 'frontend', 'backend')
        print('已暂停业务写入，开始事务更新。')
        queries = ['SET SESSION sql_mode=CONCAT_WS(\',\',@@sql_mode,\'STRICT_ALL_TABLES\');', 'START TRANSACTION;']
        queries += ['UPDATE infra_file_config SET config=JSON_SET(config,\'$.domain\',' + lit(site) + ') WHERE master=1 AND storage=10 AND deleted=0;']
        queries += ["INSERT INTO nus_deployment_metadata(metadata_key,metadata_value) VALUES ('site.url'," + lit(site) + ") ON DUPLICATE KEY UPDATE metadata_value=VALUES(metadata_value);"]
        if monitor:
            queries += ["UPDATE infra_config SET value=" + lit(monitor + '/') + ",update_time=CURRENT_TIMESTAMP WHERE config_key='url.skywalking' AND deleted=0;"]
        for t, c in targets:
            require(re.fullmatch(r'[A-Za-z0-9_]+', t + c), '表字段名不合法')
            column = '`' + c + '`'
            expr = column
            for old_url in origins:
                if old_url != site:
                    expr = rewrite_expression(expr, old_url, site)
            if expr != column:
                queries += [f'UPDATE `{t}` SET {column}={expr} WHERE BINARY {column}<>BINARY {expr};']
        queries += ['COMMIT;']
        sql('\n'.join(queries))
        print('数据库事务已提交。')
        atomic(DEST / 'nginx.conf', new_nginx, 0o644)
        atomic(env_file, new_env)
        atomic(cp, new_compose)
        child_env.update(new_cfg)
        services = ['backend'] + (['skywalking-ui'] if monitor else [])
        dc('up', '-d', '--pull', 'never', '--no-deps', '--force-recreate', *services)

        def wait(service):
            print('等待 ' + service + ' 健康检查通过（最长 10 分钟）……', flush=True)
            for _ in range(300):
                i = cid(service)
                status = json.loads(run(['docker', 'inspect', i]))[0]['State']
                if status.get('Health', {}).get('Status') == 'healthy':
                    return
                if status.get('Status') in ('exited', 'dead'):
                    raise RuntimeError(service + ' 已退出，请检查 nusctl logs')
                time.sleep(2)
            raise RuntimeError(service + ' 健康检查超时，请检查日志后重新运行脚本')

        for service in services:
            wait(service)
        # Recreate frontend last so nginx resolves the new backend container address.
        dc('up', '-d', '--pull', 'never', '--no-deps', '--force-recreate', 'frontend')
        wait('frontend')
        port = new_cfg.get('HTTP_PORT', '80')
        run(['curl', '--noproxy', '*', '--fail', '--silent', '--show-error', '--max-time', '15',
             '-H', 'Host: ' + urlsplit(site).netloc, 'http://127.0.0.1:' + port + '/nginx-health'])
        html = run(['curl', '--noproxy', '*', '--fail', '--silent', '--show-error', '--max-time', '15',
                    '-H', 'Host: ' + urlsplit(site).netloc, 'http://127.0.0.1:' + port + '/'])
        require('<html' in html.lower() or '<!doctype html' in html.lower(), '首页没有返回 HTML')
        print(dc('ps'))
        print('主平台切换完成：' + site)
        print('请从外部浏览器验收登录、新旧附件、图片和下载；网关/WAF 不由本脚本修改。')
        print('后续应用离线升级包会覆盖发布目录的 Compose；升级后重新执行本脚本恢复挂载。')
        if not monitor:
            print('待办：独立监控 HTTPS 入口尚未提供，监控仍按原内网方式访问。')

if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as e:
        print('ERROR: ' + str(e), file=sys.stderr)
        print('已停止。若修改已开始，不会自动回滚或恢复写入；排查后可重新运行。未创建备份。', file=sys.stderr)
        sys.exit(1)
PY
