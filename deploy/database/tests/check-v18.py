#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对 V18 与基线 17（可选基线 18）。

运行：python3 deploy/database/tests/check-v18.py [--baseline17 /path/to/ry261-test.sql] [--baseline18 /path/to/ry261-test.sql]
仅用本地镜像（--pull=never）；每次使用独立库、tmpfs 与一次性容器，绝不连接现有数据库。
V18 在运行时读取（迁移目录中唯一的 V18__*.sql），新表、配置、模板、任务和权限标识都从脚本里解析，
后续追加的小节无需改本脚本；只有业务规则（归一、去空白、可空、授权对象）按发布决定写死。
场景：
- up17：基线 17 加代表性业务数据后执行 V18；核对新表、结构与索引只增不减、未涉及的表校验和不变、
  不删任何行、只在 V18 有 UPDATE 的表里改已有行、新配置/模板/任务/按钮各一行、授权对象、代表性数据结果、
  新增文案术语扫描；
- rerun：再执行一次 V18，全部表校验和、列与索引定义不变；
- customized：学校改过的配置值与说明、任务变更模板、已有同名配置、已自建的审批按钮、admin 角色；
- fly17：Flyway 以 17 为基线迁移到 18（-target=18），再迁移无动作，validate 通过，与直接执行的结果一致；
- fresh18：基线 18 与「基线 17 + V18」逐表比较结构和数据（忽略 create_time、update_time），并确认 V18 在
  基线 18 上无改动。基线 18 取 --baseline18、NUS_BASELINE18_SQL 或工作区已标记为 18 的文件，都没有时跳过。
基线 17 优先用工作区文件；工作区已同步到更高版本时读单仓库历史中标记为 17 的最新提交，或指定 --baseline17 / NUS_BASELINE17_SQL；新仓库没有旧历史时须显式提供文件。
"""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
DOC = ROOT / 'NUS-doc'
BASELINE_REL = '3-数据库文件/ry261-test.sql'
MIGRATIONS = ROOT / 'deploy/database/migrations'
MYSQL_IMAGE = os.environ.get('NUS_TEST_MYSQL_IMAGE', 'mysql:8.4.6')
FLYWAY_IMAGE = os.environ.get('NUS_TEST_FLYWAY_IMAGE', 'flyway/flyway:10.20.1-alpine')
NAME = 'nus-v18-check-' + uuid.uuid4().hex[:10]
TIME_COLUMNS = ('create_time', 'update_time')  # 基线写死同步日期或导入时刻，迁移写入执行时刻
# 逐行列出差异的配置表；其余表只给行数与摘要，不输出行内容（含用户表）
WATCHED = ('system_menu', 'system_role_menu', 'system_notify_template', 'infra_config', 'infra_job')

CJK = '[一-鿿]'
LINT = {
    '非标准术语': re.compile('作业|评阅|批阅|阅卷|退回|返工|站内信|新增|创建|您|模版|登陆|EDU'),
    '通知写成消息': re.compile('消息'),
    '感叹号': re.compile('[!！]'),
    '中英文之间缺空格': re.compile(f'{CJK}[A-Za-z0-9]|[A-Za-z0-9]{CJK}'),
    '半角标点': re.compile(f'{CJK}[,;:?()]|[,;:?()]{CJK}'),
    '引号': re.compile('[“”‘’"]'),
}
# 新增或改动行中用户可见的文字列
VISIBLE = {'system_menu': ('name',), 'system_notify_template': ('name', 'content'),
           'infra_config': ('name', 'remark'), 'infra_job': ('name',)}

# 代表性业务数据：旧「AI 自动批改」任务、带空格与全角逗号的教师 ID 串、提交与 AI 初评记录
REPRESENTATIVE = """
INSERT INTO capacity_course (id, indicator_id, course_name, teacher_id, teach_assistant_ids, is_public_course, status, deleted)
  VALUES (90001, 1, '升级验收课程', 1, ' 101 ，102　,101,abc', 1, 0, b'0'),
         (90002, 1, '升级验收课程二', 1, '103,104', 1, 0, b'0'),
         (90003, 1, '已删除课程', 1, '105', 1, 0, b'1');
INSERT INTO edu_class_course (id, class_id, course_id, semester_name, assistant_teacher_ids, status, deleted)
  VALUES (90001, 90001, 90001, '升级验收学期', '201, 202，203', 0, b'0'),
         (90002, 90002, 90001, '升级验收学期', NULL, 0, b'0'),
         (90003, 90003, 90001, '升级验收学期', '204', 0, b'1');
INSERT INTO edu_homework (id, homework_name, course_id, class_id, deadline, auto_grade, ai_review_mode, status)
  VALUES (90001, '旧 AI 自动批改任务', 90001, 90001, '2026-12-31 23:59:59', 1, 0, 1),
         (90002, '人工批改任务', 90001, 90001, '2026-12-31 23:59:59', 0, 0, 1),
         (90003, 'AI 初评任务', 90001, 90001, '2026-12-31 23:59:59', 1, 1, 1);
INSERT INTO edu_homework_record (id, homework_id, student_id, content, status, score, submit_time, redo_count)
  VALUES (90001, 90001, 1, 'v18', 1, NULL, '2026-10-01 10:00:00', 0),
         (90002, 90002, 1, 'v18', 2, 88, '2026-10-01 10:00:00', 0),
         (90003, 90003, 1, 'v18', 3, 70, '2026-10-01 10:00:00', 1);
INSERT INTO edu_homework_ai_review (id, homework_record_id, homework_id, student_id, generation_token, status)
  VALUES (90001, 90003, 90003, 1, 'v18-check-token', 2);
"""

# 学校自定义：改过的配置值与说明、改过的任务变更模板、已经存在的新配置、自建的审批按钮、admin 角色
CUSTOMIZE = """
UPDATE infra_config SET value = '0.6', remark = '本校：上一等级六成达标即可解锁' WHERE config_key = 'edu.course.unlock-ratio';
UPDATE system_notify_template SET content = '本校：任务「{homeworkName}」有更新。', params = '["homeworkName"]'
  WHERE code = 'edu.student.homework.updated';
INSERT INTO infra_config (category, type, name, config_key, value, visible, remark, creator, updater)
  VALUES ('edu', 2, '批改撤销时长（秒）', 'edu.grading.undo-seconds', '30', b'1', '本校设置', 'school', 'school'),
         ('ai', 2, 'AI 每日令牌上限', 'ai.daily-token-quota', '50000', b'1', '本校设置', 'school', 'school');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status)
  SELECT 90010, '本校公共课审批', 'capacity:course:audit', 3, 9, id, '', '', '', 0
  FROM system_menu WHERE component = 'capacity/course/index' AND type = 2 AND deleted = 0 ORDER BY id LIMIT 1;
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type)
  VALUES (301, '平台管理员', 'admin', 10, 1, '', 0, 2);
"""
CUSTOM_CONFIG = {'edu.course.unlock-ratio': ('0.6', '本校：上一等级六成达标即可解锁'),
                 'edu.grading.undo-seconds': ('30', '本校设置'), 'ai.daily-token-quota': ('50000', '本校设置')}


def run(*args, input_text=None):
    result = subprocess.run([str(a) for a in args], input=input_text, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'{" ".join(map(str, args[:3]))} failed: {result.stderr.strip()[:1000]}')
    return result.stdout


def sql(query, database):
    return run('docker', 'exec', '-i', NAME, 'mysql', '--default-character-set=utf8mb4',
               '-uroot', '-N', '-B', database, input_text=query)


def scalar(query, database):
    return sql(query, database).strip()


def equal(actual, expected, label):
    if actual == expected:
        return
    if isinstance(actual, dict) and isinstance(expected, dict):
        lines = []
        for key in sorted(set(actual) | set(expected), key=str):
            if actual.get(key) != expected.get(key):
                old = str(expected.get(key)).splitlines()
                new = str(actual.get(key)).splitlines()
                if len(old) == 1 and len(new) == 1:
                    # 单行（如建表语句）只给出第一处差异附近的片段
                    at = next((i for i, (a, b) in enumerate(zip(old[0], new[0])) if a != b), min(len(old[0]), len(new[0])))
                    old, new = [old[0][max(0, at - 80):at + 80]], [new[0][max(0, at - 80):at + 80]]
                diff = list(difflib.unified_diff(old, new, 'expected', 'actual', lineterm='', n=0))
                lines.append(f'[{key}]\n' + '\n'.join(diff[:40] or [f'expected={expected.get(key)!r}',
                                                                   f'actual={actual.get(key)!r}']))
        raise AssertionError(f'{label}:\n' + '\n'.join(lines))
    raise AssertionError(f'{label}: expected {expected!r}, got {actual!r}')


def check(condition, label):
    if not condition:
        raise AssertionError(label)


def read_baseline(version, path=None, *, docs=None, history=True, revision=None, worktree=True):
    """只读单仓库基线路径；revision 可指定 HEAD 或任意新仓库提交，不依赖旧提交号。

    返回 (SQL, 来源)。显式文件优先于环境变量；指定 revision 时只读该提交，
    否则先查工作区，再查 HEAD 及该路径的历史。旧历史缺失时要求提供文件。
    """
    marker = re.compile(rf'^-- Flyway baseline version: {version}$', re.M)
    candidate = path or os.environ.get(f'NUS_BASELINE{version}_SQL')
    if candidate:
        file = Path(candidate)
        try:
            text = file.read_text(encoding='utf-8')
        except OSError as error:
            raise RuntimeError(f'基线 {version} 文件不存在或不可读：{file}；请指定 --baseline{version}') from error
        check(bool(marker.search(text)), f'{file} 未标记为基线 {version}')
        return text, str(file)
    docs = (DOC,) if docs is None else docs
    for doc in docs:
        file = Path(doc) / BASELINE_REL
        if worktree and revision is None and file.is_file():
            text = file.read_text(encoding='utf-8')
            if marker.search(text):
                return text, str(file)
    if history or revision is not None:
        for doc in docs:
            try:
                # 从仓库根目录读取完整路径，NUS-doc 是普通目录，无需自己的 .git。
                root = Path(run('git', '-C', doc, 'rev-parse', '--show-toplevel').strip())
                relative = (Path(doc).resolve() / BASELINE_REL).relative_to(root.resolve()).as_posix()
                commits = [revision] if revision is not None else ['HEAD', *run(
                    'git', '-C', root, 'log', '--format=%H', '--', relative).split()]
            except (OSError, ValueError, RuntimeError):
                continue
            for commit in dict.fromkeys(commits):
                try:
                    text = run('git', '-C', root, 'show', f'{commit}:{relative}')
                except (OSError, RuntimeError):
                    continue  # 删除过该文件或历史不完整时继续寻找，最终给出文件参数提示。
                if marker.search(text):
                    return text, f'{root}@{commit}:{relative}'
    source = f'（提交 {revision}）' if revision is not None else '（工作区及可用历史）'
    raise RuntimeError(f'找不到基线 {version}{source}；新仓库不含旧基线时，请用 --baseline{version} /path/to/file.sql '
                       f'显式传入旧基线文件，或设置 NUS_BASELINE{version}_SQL')


def baseline(version, worktree=False, path=None, revision=None):
    return read_baseline(version, path, worktree=worktree, revision=revision)[0]


def baseline18(path):
    """基线 18 可选：参数、环境变量或工作区已同步的文件；返回 (文本, 来源) 或 (None, 原因)。"""
    marker = '\n-- Flyway baseline version: 18\n'
    candidate = path or os.environ.get('NUS_BASELINE18_SQL')
    if candidate:
        file = Path(candidate)
        if not file.is_file():
            return None, f'基线 18 文件不存在：{file}'
        text = file.read_text(encoding='utf-8')
        check(marker in text, f'{file} 未标记为基线 18')
        return text, str(file)
    text = (DOC / BASELINE_REL).read_text(encoding='utf-8')
    if marker in text:
        return text, f'NUS-doc 工作区 {BASELINE_REL}'
    return None, '未提供基线 18（--baseline18 或 NUS_BASELINE18_SQL），工作区基线也不是 18'


def v18_script():
    scripts = sorted(MIGRATIONS.glob('V18__*.sql'))
    check(len(scripts) == 1, f'迁移目录中应有且只有一个 V18：{[s.name for s in scripts]}')
    return scripts[0], scripts[0].read_text(encoding='utf-8')


def facts(text):
    """从 V18 文本解析它声明会创建、改动或插入的对象。"""
    return {
        'created': set(re.findall(r'CREATE TABLE `(\w+)`', text)),
        'inserted': set(re.findall(r'INSERT INTO\s+`?(\w+)`?', text, re.I)),
        'updated': set(re.findall(r'\bUPDATE\s+`?(\w+)`?\s+SET\b', text, re.I)),
        'altered': set(re.findall(r'ALTER TABLE\s+`?(\w+)`?', text, re.I)),
        'configs': set(re.findall(r"config_key\s*=\s*'([\w.\-]+)'", text)),
        'templates': set(re.findall(r"`code`\s*=\s*'([a-z]+\.[\w.\-]+)'", text)),
        'jobs': set(re.findall(r"`handler_name`\s*=\s*'(\w+)'", text)),
        'permissions': set(re.findall(r"'([a-z][\w-]*(?::[\w-]+){2})'", text)),
        'destructive': re.findall(r'\b(DELETE\s+FROM|TRUNCATE|DROP\s+TABLE|DROP\s+COLUMN)\b', text, re.I),
    }


def create(database, text):
    run('docker', 'exec', NAME, 'mysql', '-uroot', '-e', f'CREATE DATABASE `{database}`;')
    sql(text, database)


def tables(database):
    return sql("SELECT table_name FROM information_schema.tables "
               f"WHERE table_schema = '{database}' AND table_type = 'BASE TABLE' ORDER BY table_name", database).split()


def column_exprs(database, table, skip=()):
    exprs, names = [], []
    for line in sql("SELECT column_name, data_type FROM information_schema.columns "
                    f"WHERE table_schema = '{database}' AND table_name = '{table}' ORDER BY ordinal_position",
                    database).splitlines():
        name, kind = line.split('\t')
        if name in skip:
            continue
        if kind == 'bit':
            expr = f'CAST(`{name}` AS UNSIGNED)'
        elif kind in ('datetime', 'timestamp'):
            # 统一到微秒，V18 把时间列改成毫秒精度时值不算变化
            expr = f"IFNULL(CAST(`{name}` AS DATETIME(6)), '<NULL>')"
        elif kind in ('binary', 'varbinary', 'tinyblob', 'blob', 'mediumblob', 'longblob'):
            expr = f"IFNULL(HEX(`{name}`), '<NULL>')"
        else:
            expr = f"IFNULL(`{name}`, '<NULL>')"
        exprs.append(expr)
        names.append(name)
    return names, exprs


def rows(database, table):
    """整表逐行读出，按主键 id 索引；值中的换行转义，避免拆行。"""
    names, exprs = column_exprs(database, table)
    if 'id' not in names:
        return None
    select = ', '.join(f"REPLACE(REPLACE({e}, '\\\\', '\\\\\\\\'), '\\n', '\\\\n')" for e in exprs)
    result = {}
    for line in sql(f'SELECT {select} FROM `{table}` ORDER BY id', database).splitlines():
        record = dict(zip(names, line.split('\t')))
        result[int(record['id'])] = record
    return result


def snapshot(database):
    """全部表的校验和、列与索引定义；用于证明重复执行一行不改。"""
    names = tables(database)
    checksums = dict(line.split('\t') for line in
                     sql('CHECKSUM TABLE ' + ', '.join(f'`{t}`' for t in names) + ';', database).splitlines())
    schema = sql("SELECT table_name, column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), "
                 "extra, IFNULL(collation_name, '<NULL>'), column_comment FROM information_schema.columns "
                 f"WHERE table_schema = '{database}' ORDER BY table_name, ordinal_position", database)
    indexes = sql("SELECT table_name, index_name, seq_in_index, IFNULL(column_name, '<expr>'), "
                  "IFNULL(expression, '<NULL>'), non_unique FROM information_schema.statistics "
                  f"WHERE table_schema = '{database}' ORDER BY table_name, index_name, seq_in_index", database)
    return {'checksums': checksums, 'schema': schema, 'indexes': indexes}


def by_table(text):
    result = {}
    for line in text.splitlines():
        table, rest = line.split('\t', 1)
        result.setdefault(table, []).append(rest)
    return result


def structure(database):
    """全部业务表的建表语句（含注释、字符集与表选项），忽略自增计数。"""
    names = [t for t in tables(database) if t != 'flyway_schema_history']
    ddl = sql(''.join(f'SHOW CREATE TABLE `{t}`;\n' for t in names), database).splitlines()
    # 排序规则已决定字符集：不同导出工具对同一列会写成「COLLATE x」或「CHARACTER SET y COLLATE x」，统一成前者
    normalize = lambda text: re.sub(r' CHARACTER SET (\w+) COLLATE (\1_\w+)', r' COLLATE \2',  # noqa: E731
                                    re.sub(r' AUTO_INCREMENT=\d+', '', text))
    return {line.split('\t', 1)[0]: normalize(line.split('\t', 1)[1]) for line in ddl}


def contents(database):
    """逐表读出全部行（含已逻辑删除的行），不比较 TIME_COLUMNS。
    配置类表按 id 逐行列出便于定位；其余表只给行数与摘要，不输出行内容（含用户表）。"""
    names = [t for t in tables(database) if t != 'flyway_schema_history']
    script = ''
    for table in names:
        _, exprs = column_exprs(database, table, TIME_COLUMNS)
        order = ' ORDER BY id' if table in WATCHED else ''
        script += f"SELECT '#table {table}';\nSELECT {', '.join(exprs)} FROM `{table}`{order};\n"
    found = {t: [] for t in names}
    current = None
    for line in sql(script, database).splitlines():
        if line.startswith('#table '):
            current = line[len('#table '):]
        else:
            found[current].append(line)
    result = {}
    for table, lines in found.items():
        if table in WATCHED:
            result[table] = '\n'.join(lines)
        else:
            digest = hashlib.sha256('\n'.join(sorted(lines)).encode('utf-8')).hexdigest()[:16]
            result[table] = f'{len(lines)} rows, sha256 {digest}'
    return result


def projections(database):
    """按业务键比较可见配置；授权按 (角色, 菜单) 比较，不比较时间。"""
    queries = {
        'menus': "SELECT id, name, permission, type, parent_id, path, component, status FROM system_menu "
                 "WHERE deleted = 0 ORDER BY id",
        'grants': "SELECT role_id, menu_id FROM system_role_menu WHERE deleted = 0 ORDER BY role_id, menu_id",
        'notify': "SELECT code, name, content, params, IFNULL(remark, '<NULL>'), status FROM system_notify_template "
                  "WHERE deleted = 0 ORDER BY code",
        'configs': "SELECT config_key, category, type, name, value, IFNULL(remark, '<NULL>'), CAST(visible AS UNSIGNED) "
                   "FROM infra_config WHERE deleted = 0 ORDER BY config_key",
        'jobs': "SELECT handler_name, name, status, cron_expression FROM infra_job WHERE deleted = 0 ORDER BY handler_name",
        'schema': "SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns "
                  "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
                  "ORDER BY table_name, ordinal_position",
    }
    return {name: sql(query, database) for name, query in queries.items()}


def flyway(database, *args):
    return run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{NAME}',
               '-v', f'{MIGRATIONS}:/flyway/sql:ro', FLYWAY_IMAGE,
               f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
               '-user=root', '-password=', '-locations=filesystem:/flyway/sql', '-target=18', *args)


def placeholders(text):
    return sorted(re.findall(r'\{(\w+)\}', text))


def full_rows(database, names):
    return {t: rows(database, t) for t in names}


def assert_upgrade(database, fact, before, after, before_rows, after_rows):
    """V18 前后：结构只增不减、未涉及的表不变、不删行、只在有 UPDATE 的表里改已有行。返回改动摘要。"""
    old_tables, new_tables = set(before['checksums']), set(after['checksums'])
    short = lambda names: {n.split('.')[-1] for n in names}  # noqa: E731  CHECKSUM 返回 库.表
    equal(sorted(short(new_tables) - short(old_tables)), sorted(fact['created']), f'{database}: 新表恰为 V18 建的表')
    equal(sorted(short(old_tables) - short(new_tables)), [], f'{database}: 没有删表')
    touched = fact['created'] | fact['inserted'] | fact['updated'] | fact['altered']
    untouched = {t: c for t, c in before['checksums'].items() if t.split('.')[-1] not in touched}
    equal({t: c for t, c in after['checksums'].items() if t in untouched}, untouched,
          f'{database}: V18 未涉及的表校验和不变')

    old_columns, new_columns = by_table(before['schema']), by_table(after['schema'])
    changes = []
    for table, columns in old_columns.items():
        removed = sorted(set(columns) - set(new_columns.get(table, [])))
        added = sorted(set(new_columns.get(table, [])) - set(columns))
        if removed or added:
            check(table in fact['altered'], f'{database}: 表 {table} 的列定义变化，但 V18 没有 ALTER 它')
            old_names = {c.split('\t')[0] for c in columns}
            new_names = {c.split('\t')[0] for c in new_columns[table]}
            check(old_names <= new_names, f'{database}: 表 {table} 删除了列 {sorted(old_names - new_names)}')
            changes.append(f'{table} 列：' + ', '.join(sorted({c.split(chr(9))[0] for c in added})))
    old_indexes, new_indexes = by_table(before['indexes']), by_table(after['indexes'])
    for table, items in old_indexes.items():
        check(set(items) <= set(new_indexes.get(table, [])), f'{database}: 表 {table} 的索引被删除或改动')
        extra = sorted({i.split('\t')[0] for i in set(new_indexes.get(table, [])) - set(items)})
        if extra:
            check(table in fact['altered'], f'{database}: 表 {table} 新增索引，但 V18 没有 ALTER 它')
            changes.append(f'{table} 索引：' + ', '.join(extra))

    for table, old in before_rows.items():
        new = after_rows[table]
        if old is None:
            continue
        check(set(old) <= set(new), f'{database}: 表 {table} 删除了行 {sorted(set(old) - set(new))[:10]}')
        modified = {i: {c: (old[i][c], new[i][c]) for c in old[i] if old[i][c] != new[i].get(c)}
                    for i in old if any(old[i][c] != new[i].get(c) for c in old[i])}
        if modified:
            check(table in fact['updated'], f'{database}: 表 {table} 有已有行被改，但 V18 没有 UPDATE 它：'
                                            f'{sorted(modified)[:10]}')
            changes.append(f'{table} 改动已有行 {len(modified)} 行')
        added = set(new) - set(old)
        if added:
            check(table in fact['inserted'], f'{database}: 表 {table} 新增了行，但 V18 没有 INSERT 它')
    return changes


def assert_objects(database, fact):
    """配置、模板、任务各一行；权限标识不重复；模板占位符与参数一致。"""
    for key in sorted(fact['configs']):
        found = sql(f"SELECT category, type FROM infra_config WHERE config_key = '{key}' AND deleted = 0", database)
        lines = found.splitlines()
        equal(len(lines), 1, f'{database}: 配置 {key} 恰有一行')
        equal(lines[0], f"{key.split('.')[0]}\t2", f'{database}: 配置 {key} 的分类与类型')
    for code in sorted(fact['templates']):
        equal(scalar(f"SELECT COUNT(*) FROM system_notify_template WHERE code = '{code}' AND deleted = 0", database),
              '1', f'{database}: 通知模板 {code} 恰有一行')
    for line in sql("SELECT code, content, params FROM system_notify_template WHERE deleted = 0", database).splitlines():
        code, content, params = line.split('\t')
        equal(placeholders(content), sorted(json.loads(params)), f'{database}: 模板 {code} 占位符与参数一致')
    for handler in sorted(fact['jobs']):
        equal(scalar(f"SELECT COUNT(*) FROM infra_job WHERE handler_name = '{handler}' AND deleted = 0", database),
              '1', f'{database}: 定时任务 {handler} 恰有一行')


def assert_new_menus(database, fact, before_rows, after_rows):
    """V18 新建的按钮：权限标识都写在 V18 里，且每个标识只有一个未删除的菜单。"""
    added = [r for i, r in after_rows['system_menu'].items()
             if i not in before_rows['system_menu'] and r['deleted'] == '0']
    permissions = sorted(r['permission'] for r in added if r['permission'] not in ('', '<NULL>'))
    check(set(permissions) <= fact['permissions'], f'{database}: 新按钮的权限标识不在 V18 里：{permissions}')
    for permission in permissions:
        equal(scalar(f"SELECT COUNT(*) FROM system_menu WHERE permission = '{permission}' AND deleted = 0", database),
              '1', f'{database}: 权限标识 {permission} 只有一个菜单')
    return len(added)


def new_grants(database, before_rows, after_rows):
    """新增授权：{角色编码: {权限标识或组件}}。"""
    roles = dict(line.split('\t') for line in sql('SELECT id, code FROM system_role', database).splitlines())
    labels = {i: {v for v in (r['permission'], r['component']) if v not in ('', '<NULL>')} or {f'#{i}'}
              for i, r in after_rows['system_menu'].items()}
    result = {}
    old = before_rows['system_role_menu']
    for i, row in after_rows['system_role_menu'].items():
        if i not in old and row['deleted'] == '0':
            result.setdefault(roles.get(row['role_id'], row['role_id']), set()).update(
                labels.get(int(row['menu_id']), {f'#{row["menu_id"]}'}))
    return result


def assert_grants(database, grants, audit_role='school_admin'):
    for code in ('college_admin', 'student'):
        check(code not in grants, f'{database}: {code} 不应获得任何新授权：{sorted(grants.get(code, []))}')
    check({'capacity:course:audit', 'capacity:indicator:query', 'capacity/course/index'} <= grants.get(audit_role, set()),
          f'{database}: {audit_role} 应获得公共课审批按钮、课程查询与课程页面：{sorted(grants.get(audit_role, []))}')
    check({'edu:homework:extend', 'edu:homework:remind', 'edu:homework:copy'} <= grants.get('teacher', set()),
          f'{database}: 教师应获得个别延期、催交与复制任务：{sorted(grants.get("teacher", []))}')


def lint(database, before_rows, after_rows):
    """V18 新增或改动的配置文字（菜单、模板、参数配置、定时任务）按界面术语表扫描。"""
    hits = []
    for table, columns in VISIBLE.items():
        old = before_rows[table]
        for i, row in after_rows[table].items():
            if row.get('deleted') != '0':
                continue
            for column in columns:
                text = row[column]
                if i in old and old[i][column] == text:
                    continue
                for rule, pattern in LINT.items():
                    if pattern.search(text):
                        hits.append(f'{table} {i} {column}「{text[:60]}」：{rule}')
    return hits


def assert_representative(database):
    """代表性数据按发布规则变化：旧「AI 自动批改」归一、ID 串去空白、截止时间可空，其余不动。"""
    equal(sql('SELECT id, auto_grade, ai_review_mode FROM edu_homework WHERE id >= 90001 ORDER BY id', database).split('\n')[:3],
          ['90001\t1\t1', '90002\t0\t0', '90003\t1\t1'], f'{database}: 旧 AI 自动批改任务改为 AI 初评 + 教师复核')
    equal(sql('SELECT id, teach_assistant_ids FROM capacity_course WHERE id >= 90001 ORDER BY id', database).splitlines(),
          ['90001\t101,102,101,abc', '90002\t103,104', '90003\t105'], f'{database}: 课程团队 ID 串去空白、统一半角逗号')
    equal(sql("SELECT id, IFNULL(assistant_teacher_ids, '<NULL>') FROM edu_class_course WHERE id >= 90001 ORDER BY id",
              database).splitlines(), ['90001\t201,202,203', '90002\t<NULL>', '90003\t204'],
          f'{database}: 班课助教 ID 串去空白')
    equal(scalar("SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() "
                 "AND table_name = 'edu_homework' AND column_name = 'deadline'", database), 'YES',
          f'{database}: 任务截止时间可空（复制出的草稿）')
    equal(sql("SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
              "AND table_name = 'edu_homework_record' AND non_unique = 0 AND index_name <> 'PRIMARY' "
              "GROUP BY index_name", database).split(), ['uk_hw_stu'], f'{database}: 提交记录唯一键保持 uk_hw_stu')


def assert_members(database, fact):
    """P2a 关联表（V18 建了才核对）：按旧 ID 串回填，去重、跳过非数字片段和已删除的课程与班课，顺序与原串一致。"""
    checks = []
    if 'capacity_course_member' in fact['created']:
        checks.append(("SELECT course_id, GROUP_CONCAT(user_id ORDER BY id) FROM capacity_course_member "
                       "WHERE course_id >= 90001 AND deleted = 0 GROUP BY course_id ORDER BY course_id",
                       ['90001\t101,102', '90002\t103,104'], '课程团队'))
    if 'edu_class_course_assistant' in fact['created']:
        checks.append(("SELECT class_course_id, GROUP_CONCAT(teacher_id ORDER BY id) FROM edu_class_course_assistant "
                       "WHERE class_course_id >= 90001 AND deleted = 0 GROUP BY class_course_id ORDER BY class_course_id",
                       ['90001\t201,202,203'], '班课助教'))
    for query, expected, label in checks:
        equal(sql(query, database).splitlines(), expected, f'{database}: {label}关联表按旧 ID 串回填')
    return [label for _, _, label in checks]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline17', help='旧基线 17 的 SQL 文件；新仓库没有旧历史时必须提供')
    parser.add_argument('--baseline18', help='基线 18 的 ry261-test.sql 路径；不提供且工作区不是 18 时跳过 fresh18')
    args = parser.parse_args()
    script, v18 = v18_script()
    digest = hashlib.sha256(v18.encode('utf-8')).hexdigest()

    def unchanged():
        # Flyway 从迁移目录读文件：检查期间 V18 被改写时，各场景用的不是同一份脚本
        check(hashlib.sha256(script.read_text(encoding='utf-8').encode('utf-8')).hexdigest() == digest,
              f'{script.name} 在检查期间被修改，请重跑')
    fact = facts(v18)
    equal(fact['destructive'], [], f'{script.name} 不应含删除数据或删表、删列语句')
    base17 = baseline(17, worktree=True, path=args.baseline17)
    base18, base18_source = baseline18(args.baseline18)
    notes = []
    try:
        run('docker', 'run', '-d', '--rm', '--pull=never', '--name', NAME, '--network', 'none',
            '--tmpfs', '/var/lib/mysql', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', MYSQL_IMAGE)
        for _ in range(120):
            logs = subprocess.run(['docker', 'logs', NAME], text=True, capture_output=True)
            if 'MySQL init process done. Ready for start up.' in logs.stdout + logs.stderr:
                try:
                    run('docker', 'exec', NAME, 'mysqladmin', 'ping', '-uroot')
                    break
                except RuntimeError:
                    pass
            time.sleep(1)
        else:
            raise RuntimeError('一次性 MySQL 容器未就绪')

        # 1. up17：基线 17 + 代表性数据 + V18 -----------------------------------------------------
        create('up17', base17)
        sql(REPRESENTATIVE, 'up17')
        watched = sorted(set(tables('up17')))
        before, before_rows = snapshot('up17'), full_rows('up17', watched)
        sql(v18, 'up17')
        after, after_rows = snapshot('up17'), full_rows('up17', watched)
        changes = assert_upgrade('up17', fact, before, after, before_rows, after_rows)
        assert_objects('up17', fact)
        menu_count = assert_new_menus('up17', fact, before_rows, after_rows)
        assert_grants('up17', new_grants('up17', before_rows, after_rows))
        assert_representative('up17')
        members = assert_members('up17', fact)
        # 术语扫描不中断后续场景，最后统一报告
        lint_hits = lint('up17', before_rows, after_rows)
        equal(scalar("SELECT COUNT(*) FROM system_notify_template WHERE code = 'edu.student.homework.updated' "
                     "AND deleted = 0 AND content LIKE '%{changes}%'", 'up17'), '1',
              'up17: 仍为 V17 默认的任务变更模板改为写明变更内容')
        notes.append(f'up17 新表 {len(fact["created"])} 张、新菜单 {menu_count} 个；' + '；'.join(changes)
                     + (f'；回填 {"、".join(members)}关联表' if members else ''))

        # 2. rerun：再执行一次一行不改 ---------------------------------------------------------------
        sql(v18, 'up17')
        equal(snapshot('up17'), after, 'up17 重复执行 V18：全部表校验和、列与索引不变')

        # 3. customized：学校改过的值保留 ------------------------------------------------------------
        create('customized', base17)
        sql(REPRESENTATIVE + CUSTOMIZE, 'customized')
        watched = sorted(set(tables('customized')))
        before, before_rows = snapshot('customized'), full_rows('customized', watched)
        sql(v18, 'customized')
        after, after_rows = snapshot('customized'), full_rows('customized', watched)
        assert_upgrade('customized', fact, before, after, before_rows, after_rows)
        assert_objects('customized', fact)
        assert_new_menus('customized', fact, before_rows, after_rows)
        grants = new_grants('customized', before_rows, after_rows)
        assert_grants('customized', grants)
        check({'capacity:course:audit', 'capacity:indicator:query'} <= grants.get('admin', set()),
              f'customized: admin 角色应获得公共课审批：{sorted(grants.get("admin", []))}')
        equal(sql('SELECT id FROM system_menu WHERE permission = \'capacity:course:audit\' AND deleted = 0', 'customized').split(),
              ['90010'], 'customized: 学校自建的审批按钮不重复创建')
        for key, (value, remark) in CUSTOM_CONFIG.items():
            equal(sql(f"SELECT value, remark FROM infra_config WHERE config_key = '{key}' AND deleted = 0",
                      'customized').splitlines(), [f'{value}\t{remark}'], f'customized: 配置 {key} 保留学校的值和说明')
        equal(sql("SELECT content, params FROM system_notify_template WHERE code = 'edu.student.homework.updated' "
                  "AND deleted = 0", 'customized').splitlines(),
              ['本校：任务「{homeworkName}」有更新。\t["homeworkName"]'], 'customized: 学校改过的任务变更模板保留')
        assert_representative('customized')
        assert_members('customized', fact)
        sql(v18, 'customized')
        equal(snapshot('customized'), after, 'customized 重复执行 V18')

        # 4. fly17：Flyway 17 -> 18 ---------------------------------------------------------------
        create('plain17', base17)
        sql(v18, 'plain17')
        if subprocess.run(['docker', 'image', 'inspect', FLYWAY_IMAGE], capture_output=True).returncode == 0:
            unchanged()
            create('fly17', base17)
            flyway('fly17', 'baseline', '-baselineVersion=17', '-baselineDescription=ry261-test.sql')
            flyway('fly17', 'migrate')
            equal(sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank', 'fly17').splitlines(),
                  ['17\t1', '18\t1'], 'Flyway 17 -> 18')
            state = snapshot('fly17')
            flyway('fly17', 'migrate')
            flyway('fly17', 'validate')
            equal(snapshot('fly17'), state, 'Flyway 18 再次 migrate 无改动')
            unchanged()
            equal(projections('fly17'), projections('plain17'), 'Flyway 17 -> 18 与直接执行 V18 一致')
            notes.append('fly17 通过')
        else:
            notes.append(f'fly17 跳过（本地没有镜像 {FLYWAY_IMAGE}）')

        # 5. fresh18：基线 18 与基线 17 + V18 一致 ----------------------------------------------------
        if base18 is None:
            notes.append(f'fresh18 跳过：{base18_source}')
        else:
            create('fresh18', base18)
            fresh = snapshot('fresh18')
            sql(v18, 'fresh18')
            equal(snapshot('fresh18'), fresh, 'fresh18: V18 在基线 18 上无改动')
            equal(structure('fresh18'), structure('plain17'), '基线 18 与基线 17 + V18：全部表结构')
            equal(contents('fresh18'), contents('plain17'), '基线 18 与基线 17 + V18：逐表逐行数据')
            equal(projections('fresh18'), projections('plain17'), '基线 18 与基线 17 + V18：可见配置')
            notes.append(f'fresh18 通过（{base18_source}）')
        equal(lint_hits, [], f'up17: V18 新增或改动文字的术语扫描（其余场景已通过：{"; ".join(notes)}）')
        print(f'PASS {script.name}: up17、rerun、customized; ' + '; '.join(notes))
    finally:
        subprocess.run(['docker', 'rm', '-f', NAME], capture_output=True, text=True)


if __name__ == '__main__':
    try:
        main()
    except AssertionError as error:
        print(f'FAIL {error}', file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, RuntimeError) as error:
        print(f'ERROR {error}', file=sys.stderr)
        sys.exit(2)
