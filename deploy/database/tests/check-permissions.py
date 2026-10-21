#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对接口权限矩阵。

先在 NUS-backend 更新并审查 endpoint-permissions.tsv，再运行：
python3 deploy/database/tests/check-permissions.py --update
日常核对去掉 --update；--self-test 只检查表达式解析器，不需要 Docker。
"""

import argparse
import difflib
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[3]
BASELINE = ROOT / 'NUS-doc/3-数据库文件/ry261-test.sql'
MIGRATIONS = ROOT / 'deploy/database/migrations'
EXPECTED = Path(__file__).resolve().parent / 'permissions/expected-role-endpoints.tsv'
DEFAULT_SNAPSHOT = ROOT / 'NUS-backend/nus-server/src/test/resources/security/endpoint-permissions.tsv'
BASELINE_MARKER = re.compile(r'^-- Flyway baseline version: (\d+)$', re.M)
MIGRATION_NAME = re.compile(r'^V(\d+)__.+\.sql$')
ROLE_IDS = (1, 159, 160, 161, 200)
MYSQL_IMAGE = os.environ.get('NUS_TEST_MYSQL_IMAGE', 'mysql:8.4.6')
DATABASE = 'nus_permissions'


def run(*args, input_text=None):
    result = subprocess.run([str(arg) for arg in args], input=input_text, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f'{" ".join(map(str, args[:3]))} 失败 (exit {result.returncode}): '
                           f'{result.stderr.strip()}')
    return result.stdout


def sql(container, statement, database=DATABASE):
    return run('docker', 'exec', '-i', container, 'mysql', '--default-character-set=utf8mb4',
               '-uroot', '-N', '-B', database, input_text=statement)


def baseline_version(text):
    found = BASELINE_MARKER.findall(text)
    if len(found) != 1:
        raise ValueError(f'基线文件缺少唯一的 Flyway 基线版本标记：{BASELINE}')
    return int(found[0])


def migrations(after_version):
    """与 Flyway 一致：只按版本号依次执行比基线新的迁移。"""
    selected = {}
    for script in MIGRATIONS.glob('V*__*.sql'):
        match = MIGRATION_NAME.match(script.name)
        if not match:
            raise ValueError(f'无法识别的迁移文件名：{script.name}')
        version = int(match.group(1))
        if version in selected:
            raise ValueError(f'迁移版本重复：{selected[version].name}、{script.name}')
        selected[version] = script
    latest = max(selected, default=0)
    if latest < after_version:
        raise ValueError(f'基线版本 {after_version} 高于最新迁移 V{latest}')
    scripts = [selected[version] for version in sorted(selected) if version > after_version]
    for script in scripts:
        print(f'执行迁移：{script}', file=sys.stderr)
    return scripts


def tokenize(expression):
    pattern = re.compile(r"\s*(?:(@ss\.(?:hasPermission|hasAnyPermissions|hasRole|hasAnyRoles))|"
                         r"('(?:[^'\\]|\\.)*')|([(),])|(and\b|or\b))")
    result = []
    position = 0
    while position < len(expression):
        match = pattern.match(expression, position)
        if not match:
            raise ValueError(f'无法解析权限表达式：{expression!r}，位置 {position}')
        result.append(next(part for part in match.groups() if part is not None))
        position = match.end()
    return result


def parse(expression):
    """返回 AST；词法或语法不认识时立刻失败，避免推测权限。"""
    tokens = tokenize(expression)
    index = 0

    def accept(token):
        nonlocal index
        if index < len(tokens) and tokens[index] == token:
            index += 1
            return True
        return False

    def require(token):
        if not accept(token):
            raise ValueError(f'无法解析权限表达式：{expression!r}，预期 {token!r}，位置 {index}')

    def atom():
        nonlocal index
        if accept('('):
            node = disjunction()
            require(')')
            return node
        if index >= len(tokens) or not tokens[index].startswith('@ss.'):
            raise ValueError(f'无法解析权限表达式：{expression!r}，位置 {index}')
        function = tokens[index][4:]
        index += 1
        require('(')
        args = []
        while index < len(tokens) and tokens[index].startswith("'"):
            args.append(tokens[index][1:-1].replace("\\'", "'"))
            index += 1
            if not accept(','):
                break
        require(')')
        if not args or (function in ('hasPermission', 'hasRole') and len(args) != 1):
            raise ValueError(f'权限函数参数无效：{expression!r}')
        return ('call', function, tuple(args))

    def conjunction():
        node = atom()
        while accept('and'):
            node = ('and', node, atom())
        return node

    def disjunction():
        node = conjunction()
        while accept('or'):
            node = ('or', node, conjunction())
        return node

    tree = disjunction()
    if index != len(tokens):
        raise ValueError(f'权限表达式有未解析的内容：{expression!r}，位置 {index}')
    return tree


def evaluate(tree, role_code, permissions):
    kind = tree[0]
    if kind == 'and':
        return evaluate(tree[1], role_code, permissions) and evaluate(tree[2], role_code, permissions)
    if kind == 'or':
        return evaluate(tree[1], role_code, permissions) or evaluate(tree[2], role_code, permissions)
    _, function, values = tree
    if function in ('hasRole', 'hasAnyRoles'):
        return role_code in values
    # 与 PermissionServiceImpl.hasAnyPermissions 一致：超管对权限标识全部放行，角色判断仍按 code。
    return role_code == 'super_admin' or any(value in permissions for value in values)


def self_test():
    cases = [
        ("@ss.hasPermission('a')", 'teacher', {'a'}, True),
        ("@ss.hasPermission('a')", 'teacher', set(), False),
        ("@ss.hasAnyPermissions('a', 'b')", 'teacher', {'b'}, True),
        ("@ss.hasRole('student') and @ss.hasPermission('submit')", 'student', {'submit'}, True),
        ("@ss.hasRole('student') and @ss.hasPermission('submit')", 'student', set(), False),
        ("@ss.hasAnyRoles('admin','ADMIN','super_admin')", 'super_admin', set(), True),
        ("@ss.hasRole('student') or @ss.hasPermission('query')", 'teacher', {'query'}, True),
        ("@ss.hasPermission('a') or @ss.hasPermission('b') and @ss.hasRole('student')",
         'teacher', {'a'}, True),
        ("(@ss.hasPermission('a') or @ss.hasPermission('b')) and @ss.hasRole('student')",
         'teacher', {'a'}, False),
        ("@ss.hasPermission('missing')", 'super_admin', set(), True),
    ]
    for expression, role, permissions, expected in cases:
        actual = evaluate(parse(expression), role, permissions)
        assert actual == expected, (expression, role, actual, expected)
    for invalid in ("hasRole('student')", "@ss.hasScope('x')", "@ss.hasPermission('a') trailing",
                    "@ss.hasPermission()", "@ss.hasRole('a','b')", "@ss.hasPermission('a') and"):
        try:
            parse(invalid)
        except ValueError:
            continue
        raise AssertionError(f'未知表达式未被拒绝：{invalid}')
    print('表达式解析自测通过（含 and/or 优先级、超管及拒绝未知形式）')


def read_snapshot(path):
    if not path.exists():
        raise FileNotFoundError(f'接口快照不存在：{path}；先运行 Java 快照测试生成')
    endpoints = []
    for number, row in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        parts = row.split('\t')
        if len(parts) != 3 or not all(parts):
            raise ValueError(f'{path}:{number} 格式应为 HTTP方法<TAB>路径<TAB>安全要求')
        method, uri, security = parts
        tree = None if security in ('PERMIT_ALL', 'AUTHENTICATED') else parse(security)
        endpoints.append((method, uri, security, tree))
    if not endpoints or endpoints != sorted(endpoints, key=lambda item: item[:3]):
        raise ValueError(f'{path} 为空或未排序；请先运行 Java 快照测试')
    if len({(method, uri) for method, uri, _, _ in endpoints}) != len(endpoints):
        raise ValueError(f'{path} 存在重复接口映射')
    return endpoints


def read_roles(container):
    role_rows = sql(container, 'SELECT id, code FROM system_role WHERE id IN (1,159,160,161,200) '
                    'AND deleted = 0 AND status = 0 ORDER BY id')
    roles = {int(role_id): code for role_id, code in (row.split('\t') for row in role_rows.splitlines())}
    if tuple(roles) != ROLE_IDS:
        raise ValueError(f'基线/迁移中的有效角色不完整：{roles}')
    grants = sql(container, 'SELECT rm.role_id, m.permission FROM system_role_menu rm '
                 'JOIN system_menu m ON m.id = rm.menu_id '
                 'WHERE rm.deleted = 0 AND m.deleted = 0 AND m.status = 0 '
                 'AND rm.role_id IN (1,159,160,161,200) '
                 "AND m.permission IS NOT NULL AND m.permission <> ''")
    permissions = {role_id: set() for role_id in ROLE_IDS}
    for row in grants.splitlines():
        role_id, permission = row.split('\t', 1)
        permissions[int(role_id)].add(permission)
    return roles, permissions


def matrix(endpoints, roles, permissions):
    lines = ['METHOD\tPATH\tSECURITY\t' + '\t'.join(f'{role_id}:{roles[role_id]}' for role_id in ROLE_IDS)]
    allowed = {role_id: [] for role_id in ROLE_IDS}
    for method, path, security, tree in endpoints:
        cells = []
        for role_id in ROLE_IDS:
            can_call = tree is None or evaluate(tree, roles[role_id], permissions[role_id])
            cells.append('1' if can_call else '0')
            if can_call:
                allowed[role_id].append((method, path, security))
        lines.append('\t'.join((method, path, security, *cells)))
    return '\n'.join(lines) + '\n', allowed


def report(endpoints, roles, permissions, allowed, version, scripts):
    lines = ['# 角色可调用接口矩阵', '', f'接口总数：{len(endpoints)}；数据库基线：{version}；'
             f'迁移：{", ".join(script.name for script in scripts) or "无"}。', '',
             '| 角色 | 菜单权限标识数 | 可调用接口数 |', '| --- | ---: | ---: |']
    for role_id in ROLE_IDS:
        lines.append(f'| {role_id} {roles[role_id]} | {len(permissions[role_id])} | {len(allowed[role_id])} |')
    for role_id in ROLE_IDS:
        lines.extend(['', f'## {role_id} {roles[role_id]} 可调用接口', ''])
        for method, path, _ in allowed[role_id]:
            lines.append(f'- `{method} {path}`')
    lines.extend(['', '## 学生可调用的非学生端接口（需人工审查）', ''])
    suspect = [(method, path, security) for method, path, security in allowed[200]
               if not path.startswith('/edu/student/')]
    if suspect:
        for method, path, security in suspect:
            lines.append(f'- `{method} {path}` — `{security}`')
    else:
        lines.append('无。')
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument('--baseline', type=Path, default=BASELINE, help='只读基线 SQL 路径（使用其他基线路径时指定）')
    parser.add_argument('--update', action='store_true', help='重写人工审查后的预期矩阵')
    parser.add_argument('--report', type=Path, help='将 Markdown 报告写入指定文件；默认为标准输出')
    parser.add_argument('--self-test', action='store_true', help='只测试表达式解析器，不启动 Docker')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    endpoints = read_snapshot(args.snapshot)
    baseline = args.baseline.read_text(encoding='utf-8')
    version = baseline_version(baseline)
    scripts = migrations(version)
    container = 'nus-permission-check-' + uuid.uuid4().hex[:10]
    started = False
    try:
        run('docker', 'run', '-d', '--rm', '--pull=never', '--name', container,
            '--network', 'none', '--tmpfs', '/var/lib/mysql',
            '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', MYSQL_IMAGE)
        started = True
        for _ in range(120):
            # 初始化阶段的临时实例也会响应 ping，需等初始化完成后的正式实例
            logs = subprocess.run(['docker', 'logs', container], text=True, capture_output=True)
            if 'MySQL init process done. Ready for start up.' not in logs.stdout + logs.stderr:
                time.sleep(1)
                continue
            try:
                run('docker', 'exec', container, 'mysqladmin', 'ping', '-uroot')
                break
            except RuntimeError:
                time.sleep(1)
        else:
            raise RuntimeError('一次性 MySQL 容器未就绪')
        run('docker', 'exec', container, 'mysql', '-uroot', '-e', f'CREATE DATABASE {DATABASE}')
        sql(container, baseline)
        for script in scripts:
            sql(container, script.read_text(encoding='utf-8'))
        roles, permissions = read_roles(container)
        actual, allowed = matrix(endpoints, roles, permissions)
    finally:
        if started:
            subprocess.run(['docker', 'rm', '-f', container], capture_output=True, text=True)
    readable = report(endpoints, roles, permissions, allowed, version, scripts)
    if args.report:
        args.report.write_text(readable, encoding='utf-8')
    else:
        print(readable, end='')
    if args.update:
        EXPECTED.parent.mkdir(parents=True, exist_ok=True)
        EXPECTED.write_text(actual, encoding='utf-8')
        print(f'已更新矩阵：{EXPECTED}', file=sys.stderr)
    elif not EXPECTED.exists():
        raise FileNotFoundError(f'预期矩阵不存在：{EXPECTED}；审查后加 --update 生成')
    else:
        expected = EXPECTED.read_text(encoding='utf-8')
        if expected != actual:
            diff = difflib.unified_diff(expected.splitlines(keepends=True),
                                        actual.splitlines(keepends=True),
                                        fromfile=str(EXPECTED), tofile='实际角色接口矩阵')
            print(''.join(diff), file=sys.stderr)
            raise RuntimeError('角色接口矩阵与预期不一致')
        print('角色接口矩阵与预期一致', file=sys.stderr)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f'权限矩阵校验失败：{error}', file=sys.stderr)
        sys.exit(1)
