#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对 V17 与基线 16、基线 17。

运行：python3 deploy/database/tests/check-v17.py
仅用本地镜像（--pull=never）；每次使用独立库、tmpfs 与一次性容器，绝不连接现有数据库。
场景：up16（基线 16 + V17，逐行比对术语与授权表，其余各表校验和不变）、重复执行无改动、
customized（学校改过的名称与模板、同级重名、授权修复的边界）、
fresh17（基线 17 上 V17 无改动；与 up16 比较全部表结构，并逐表比较全部行，只忽略导入或迁移时刻写入的
create_time、update_time）；本地有 Flyway 镜像时增加 fly16、fly15、fly17。
基线 17 优先用工作区文件；工作区已同步到更高版本时改读历史中标记为 17 的最新提交，或指定 NUS_BASELINE17_SQL。
基线 16、15 从单仓库历史中读取标记为该版本的最新提交，或指定 NUS_BASELINE16_SQL、NUS_BASELINE15_SQL。
--baseline15 /path/to/file.sql 可显式提供基线 15，原 NUS_BASELINE15_SQL 环境变量仍可用。
--baseline16 /path/to/file.sql 可显式提供基线 16，原 NUS_BASELINE16_SQL 环境变量仍可用。
--baseline17 /path/to/file.sql 可显式提供基线 17，原 NUS_BASELINE17_SQL 环境变量仍可用。
"""
import difflib
import hashlib
import json
import argparse
import importlib.util
import sys
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('baseline_helpers', Path(__file__).with_name('check-v18.py'))
baseline_helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline_helpers)

ROOT = Path(__file__).resolve().parents[3]
DOC = ROOT / 'NUS-doc'
BASELINE_REL = '3-数据库文件/ry261-test.sql'
MIGRATIONS = ROOT / 'deploy/database/migrations'
V17 = (MIGRATIONS / 'V17__v1_0_1_6_ui_terminology.sql').read_text(encoding='utf-8')
MYSQL_IMAGE = os.environ.get('NUS_TEST_MYSQL_IMAGE', 'mysql:8.4.6')
FLYWAY_IMAGE = os.environ.get('NUS_TEST_FLYWAY_IMAGE', 'flyway/flyway:10.20.1-alpine')
NAME = 'nus-v17-check-' + uuid.uuid4().hex[:10]
# 逐行比对的表；其余表只允许校验和完全不变
WATCHED = ('system_menu', 'system_role_menu', 'system_dict_type', 'system_dict_data', 'system_notify_template')

MENU_RENAMES = {
    111: ('SQL与连接池监控', 'SQL 与连接池监控'),
    1002: ('用户新增', '用户新建'), 1009: ('角色新增', '角色新建'), 1014: ('菜单新增', '菜单新建'),
    1018: ('部门新增', '部门新建'), 1022: ('岗位新增', '岗位新建'), 1027: ('字典新增', '字典新建'),
    1032: ('配置新增', '配置新建'), 1037: ('公告新增', '公告新建'), 1050: ('任务新增', '任务新建'),
    1239: ('文件配置创建', '文件配置新建'), 1265: ('客户端创建', '客户端新建'),
    2144: ('站内信管理', '通知管理'), 2146: ('站内信模板查询', '通知模板查询'),
    2147: ('站内信模板创建', '通知模板新建'), 2148: ('站内信模板更新', '通知模板更新'),
    2149: ('站内信模板删除', '通知模板删除'), 2150: ('发送测试站内信', '发送测试通知'),
    2151: ('消息记录', '通知记录'), 2152: ('站内信消息查询', '通知记录查询'),
    2450: ('三方应用创建', '三方应用新建'), 2739: ('消息中心', '通知中心'),
    2763: ('API 密钥创建', 'API 密钥新建'), 2769: ('聊天模型创建', '聊天模型新建'),
    2775: ('聊天角色创建', '聊天角色新建'), 2922: ('工具创建', '工具新建'),
    5051: ('班级新增', '班级新建'), 5057: ('指标新增', '指标新建'),
    5077: ('创建任务', '新建任务'), 5080: ('查询作业', '查询任务'), 5083: ('提交作业', '提交任务'),
    5084: ('批改作业', '批改任务'), 5096: ('AI评阅', 'AI 辅助'), 5097: ('AI批改', 'AI 初评'),
    5098: ('AI分析', 'AI 分析'), 5099: ('AI对话', 'AI 对话'), 5100: ('AI生成', 'AI 生成'),
    5123: ('课程新增', '课程新建'),
}
DICT_TYPE_RENAMES = {
    104: ('登陆结果', '登录结果'), 116: ('登陆日志的类型', '登录日志的类型'),
    167: ('站内信模版的类型', '通知模板的类型'), 2009: ('EDU教育层次', '教育层次'),
    2010: ('EDU教师职称', '教师职称'), 2011: ('EDU作业类型', '实训任务类型'),
    2012: ('EDU作业等级', '实训任务等级'), 2013: ('EDU作业状态', '实训任务状态'),
}
DICT_TYPE_REMARK_FOLLOWS = {104, 116, 167}  # 备注与旧名称相同，随名称一起改
DICT_DATA_RELABELS = {18: ('新增', '新建'), 1228: ('系统消息', '系统通知')}
NOTIFY = {  # 编码: (新名称, 新内容)
    'edu.student.class.joined': ('学生被加入班级', '你已被加入班级「{className}」。'),
    'edu.student.course.assigned': ('学生被分配课程', '你所在班级「{className}」已分配课程「{courseName}」。'),
    'edu.student.homework.created': ('新任务发布', '课程「{courseName}」发布了新任务「{homeworkName}」，截止时间：{deadline}。'),
    'edu.student.homework.updated': ('任务变更', '课程「{courseName}」的任务「{homeworkName}」已更新，请及时查看，截止时间：{deadline}。'),
    'edu.student.homework.corrected': ('任务批改完成', '任务「{homeworkName}」已批改完成，得分：{score} 分，评语：{comment}。'),
    'edu.student.homework.redo': ('任务打回重做', '任务「{homeworkName}」已被打回重做，请根据要求重新提交。'),
    'edu.student.homework.remind': ('任务催交', '任务「{homeworkName}」尚未提交，请在 {deadline} 前完成提交。'),
    'edu.student.message.reply': ('教师回复留言', '教师回复了你在课程「{courseName}」的留言：{content}'),
    'edu.teacher.message.created': ('学生发起留言', '{studentName}在课程「{courseName}」留言：{content}'),
    'edu.admin.public-course.pending': ('公共课待审批', '公共课「{courseName}」已提交审批，提交教师：{teacherName}。'),
    'edu.teacher.public-course.audit-result': ('公共课审批结果', '公共课「{courseName}」审批结果：{auditResult}，审批意见：{auditRemark}。'),
}
NOTIFY_REMARK = '教学业务通知模板'
# 基线 16 上的授权修复：教师补「查询班课」；只有页面没有上级目录的角色补目录（超管本就可见全部菜单）
BASELINE_GRANTS = {(161, 5066), (159, 5055), (1, 5047), (1, 5055), (1, 5061)}

CJK = '[一-鿿]'
LINT = {
    '非标准术语': re.compile('作业|评阅|批阅|阅卷|退回|返工|站内信|新增|创建|您|模版|登陆|EDU'),
    '通知写成消息': re.compile('消息'),
    '感叹号': re.compile('[!！]'),
    '中英文之间缺空格': re.compile(f'{CJK}[A-Za-z0-9]|[A-Za-z0-9]{CJK}'),
    '半角标点': re.compile(f'{CJK}[,;:?()]|[,;:?()]{CJK}'),
    '引号': re.compile('[“”‘’"]'),
}
LINT_EXEMPT = {('menu', '2781', '通知写成消息'), ('menu', '2782', '通知写成消息')}  # AI 对话里的「消息」指对话内容


def run(*args, input_text=None, env=None):
    result = subprocess.run([str(a) for a in args], input=input_text, text=True, capture_output=True, env=env)
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
        # 只报告有差异的键，避免整库快照淹没真正的差异
        lines = []
        for key in sorted(set(actual) | set(expected), key=str):
            if actual.get(key) != expected.get(key):
                old = str(expected.get(key)).splitlines()
                new = str(actual.get(key)).splitlines()
                diff = list(difflib.unified_diff(old, new, 'expected', 'actual', lineterm='', n=0))
                lines.append(f'[{key}]\n' + '\n'.join(diff[:40] or [f'expected={expected.get(key)!r}', f'actual={actual.get(key)!r}']))
        raise AssertionError(f'{label}:\n' + '\n'.join(lines))
    raise AssertionError(f'{label}: expected {expected!r}, got {actual!r}')


def baseline(version, worktree=False, path=None):
    return baseline_helpers.read_baseline(version, path, docs=(DOC,))[0]


def create(database, text):
    run('docker', 'exec', NAME, 'mysql', '-uroot', '-e', f'CREATE DATABASE `{database}`;')
    sql(text, database)


def tables(database):
    return sql("SELECT table_name FROM information_schema.tables "
               f"WHERE table_schema = '{database}' AND table_type = 'BASE TABLE' ORDER BY table_name", database).split()


def rows(database, table):
    """整表逐行读出（bit 列转成数字），按主键 id 索引。"""
    columns = sql("SELECT column_name, data_type FROM information_schema.columns "
                  f"WHERE table_schema = '{database}' AND table_name = '{table}' ORDER BY ordinal_position",
                  database).splitlines()
    names = [line.split('\t')[0] for line in columns]
    select = ', '.join(f'CAST(`{n}` AS UNSIGNED)' if t == 'bit' else f'IFNULL(`{n}`, \'<NULL>\')'
                       for n, t in (line.split('\t') for line in columns))
    result = {}
    for line in sql(f'SELECT {select} FROM `{table}` ORDER BY id', database).splitlines():
        record = dict(zip(names, line.split('\t')))
        result[int(record['id'])] = record
    return result


def snapshot(database):
    """被监视表的全部行、所有表的校验和与结构；用于证明除预期行外没有任何改动。"""
    names = tables(database)
    checksums = dict(line.split('\t') for line in
                     sql('CHECKSUM TABLE ' + ', '.join(f'`{t}`' for t in names) + ';', database).splitlines())
    schema = sql("SELECT table_name, column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), "
                 "extra, IFNULL(collation_name, '<NULL>') FROM information_schema.columns "
                 f"WHERE table_schema = '{database}' ORDER BY table_name, ordinal_position", database)
    indexes = sql("SELECT table_name, index_name, seq_in_index, column_name, non_unique "
                  f"FROM information_schema.statistics WHERE table_schema = '{database}' "
                  "ORDER BY table_name, index_name, seq_in_index", database)
    return {'rows': {table: rows(database, table) for table in WATCHED},
            'checksums': checksums, 'schema': schema, 'indexes': indexes}


def structure(database):
    """全部业务表的建表语句（含注释、字符集与表选项），忽略自增计数。"""
    names = [t for t in tables(database) if t != 'flyway_schema_history']
    ddl = sql(''.join(f'SHOW CREATE TABLE `{t}`;\n' for t in names), database).splitlines()
    return {line.split('\t', 1)[0]: re.sub(r' AUTO_INCREMENT=\d+', '', line.split('\t', 1)[1]) for line in ddl}


TIME_COLUMNS = ('create_time', 'update_time')  # 基线写死同步日期或导入时刻，迁移写入执行时刻


def contents(database):
    """逐表读出全部行（含已逻辑删除的行），不比较 TIME_COLUMNS。
    被监视的术语与授权表按 id 逐行列出，便于定位差异；其余表只给出行数与摘要，不输出行内容（含用户表）。"""
    names = [t for t in tables(database) if t != 'flyway_schema_history']
    columns = {}
    for line in sql("SELECT table_name, column_name, data_type FROM information_schema.columns "
                    f"WHERE table_schema = '{database}' ORDER BY table_name, ordinal_position", database).splitlines():
        table, name, kind = line.split('\t')
        if name in TIME_COLUMNS:
            continue
        if kind == 'bit':
            expr = f'CAST(`{name}` AS UNSIGNED)'
        elif kind in ('binary', 'varbinary', 'tinyblob', 'blob', 'mediumblob', 'longblob'):
            expr = f"IFNULL(HEX(`{name}`), '<NULL>')"
        else:
            expr = f"IFNULL(`{name}`, '<NULL>')"
        columns.setdefault(table, []).append(expr)
    script = ''.join(f"SELECT '#table {t}';\nSELECT {', '.join(columns[t])} FROM `{t}`"
                     f"{' ORDER BY id' if t in WATCHED else ''};\n" for t in names)
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


def changes(before, after, ignore=('update_time',)):
    """返回 (改动的列, 新增行, 删除行)；update_time 随改动自动刷新，不计入。"""
    changed = {}
    for key in before.keys() & after.keys():
        columns = {c: (before[key][c], after[key][c]) for c in before[key]
                   if c not in ignore and before[key][c] != after[key][c]}
        if columns:
            changed[key] = columns
    added = {key: after[key] for key in after.keys() - before.keys()}
    return changed, added, sorted(before.keys() - after.keys())


def placeholders(text):
    return sorted(re.findall(r'\{(\w+)\}', text))


def assert_upgrade(before, after, database, menus, dict_types, dict_data, notify, grants, notify_remarks):
    """核对 V17 前后的逐行差异恰好是预期的改名和授权，其余各表一行未动。"""
    rows_before, rows_after = before['rows'], after['rows']
    equal(after['schema'], before['schema'], f'{database}: V17 没有结构变更')
    equal(after['indexes'], before['indexes'], f'{database}: V17 没有索引变更')
    untouched = {t: c for t, c in before['checksums'].items() if t.split('.')[-1] not in WATCHED}
    equal({t: c for t, c in after['checksums'].items() if t in untouched}, untouched,
          f'{database}: 业务表及其余配置表校验和不变')

    changed, added, removed = changes(rows_before['system_menu'], rows_after['system_menu'])
    equal(changed, {i: {'name': names} for i, names in menus.items()}, f'{database}: system_menu 只改预期名称')
    equal((added, removed), ({}, []), f'{database}: system_menu 无增删')

    changed, added, removed = changes(rows_before['system_dict_type'], rows_after['system_dict_type'])
    expected = {}
    for i, names in dict_types.items():
        expected[i] = {'name': names}
        if i in DICT_TYPE_REMARK_FOLLOWS:
            expected[i]['remark'] = names
    equal(changed, expected, f'{database}: system_dict_type 只改预期名称和备注')
    equal((added, removed), ({}, []), f'{database}: system_dict_type 无增删')

    changed, added, removed = changes(rows_before['system_dict_data'], rows_after['system_dict_data'])
    equal(changed, {i: {'label': labels} for i, labels in dict_data.items()},
          f'{database}: system_dict_data 只改预期标签，值不变')
    equal((added, removed), ({}, []), f'{database}: system_dict_data 无增删')

    changed, added, removed = changes(rows_before['system_notify_template'], rows_after['system_notify_template'])
    code_of = {i: r['code'] for i, r in rows_before['system_notify_template'].items()}
    by_code = {code_of[i]: columns for i, columns in changed.items()}
    equal(by_code, notify, f'{database}: system_notify_template 只改预期名称、内容和备注')
    equal((added, removed), ({}, []), f'{database}: system_notify_template 无增删')
    for record in rows_after['system_notify_template'].values():
        if record['deleted'] == '0':
            equal(placeholders(record['content']), sorted(json.loads(record['params'])),
                  f'{database}: 模板 {record["code"]} 占位符与参数一致')
    for record_id, columns in changed.items():
        if 'content' in columns:
            old, new = columns['content']
            equal(placeholders(new), placeholders(old), f'{database}: 模板 {code_of[record_id]} 占位符不变')
    remarks = {r['code']: r['remark'] for r in rows_after['system_notify_template'].values()}
    equal({code: remarks[code] for code in notify_remarks}, notify_remarks, f'{database}: 模板备注')

    changed, added, removed = changes(rows_before['system_role_menu'], rows_after['system_role_menu'])
    equal((changed, removed), ({}, []), f'{database}: 已有授权一行未动')
    equal(sorted((int(r['role_id']), int(r['menu_id'])) for r in added.values()), sorted(grants),
          f'{database}: 新增授权恰为预期')
    equal({(r['creator'], r['updater'], r['deleted']) for r in added.values()}, {('system', 'system', '0')},
          f'{database}: 新增授权的创建者与删除标记')


def lint(database):
    """V17 之后配置数据的术语扫描：菜单、启用的字典类型与标签、通知模板。"""
    sources = {
        'menu': "SELECT id, name FROM system_menu WHERE deleted = 0",
        'dict_type': "SELECT id, name FROM system_dict_type WHERE deleted = 0 AND status = 0",
        'dict_data': "SELECT d.id, d.label FROM system_dict_data d JOIN system_dict_type t ON t.type = d.dict_type "
                     "AND t.deleted = 0 AND t.status = 0 WHERE d.deleted = 0 AND d.status = 0",
        'notify_name': "SELECT id, name FROM system_notify_template WHERE deleted = 0",
        'notify_content': "SELECT id, content FROM system_notify_template WHERE deleted = 0",
    }
    hits = []
    for kind, query in sources.items():
        for line in sql(query, database).splitlines():
            record_id, text = line.split('\t', 1)
            for rule, pattern in LINT.items():
                if pattern.search(text) and (kind, record_id, rule) not in LINT_EXEMPT:
                    hits.append(f'{kind} {record_id} {text}：{rule}')
    equal(hits, [], f'{database}: 配置数据术语扫描')


def projections(database):
    """按业务键比较可见配置；授权按 (角色, 菜单) 比较，不比较自增 ID 与时间。"""
    queries = {
        'menus': "SELECT id, name, permission, type, parent_id, path, status FROM system_menu WHERE deleted = 0 ORDER BY id",
        'grants': "SELECT role_id, menu_id FROM system_role_menu WHERE deleted = 0 ORDER BY role_id, menu_id",
        'dict_type': "SELECT id, type, name, status, IFNULL(remark, '<NULL>') FROM system_dict_type WHERE deleted = 0 ORDER BY id",
        'dict_data': "SELECT id, dict_type, value, label, status FROM system_dict_data WHERE deleted = 0 ORDER BY id",
        'notify': "SELECT code, name, content, params, IFNULL(remark, '<NULL>'), status FROM system_notify_template "
                  "WHERE deleted = 0 ORDER BY code",
    }
    return {name: sql(query, database) for name, query in queries.items()}


def flyway(database, *args):
    return run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{NAME}',
               '-e', 'FLYWAY_PASSWORD', '-v', f'{MIGRATIONS}:/flyway/sql:ro', FLYWAY_IMAGE,
               f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
               '-user=root', '-locations=filesystem:/flyway/sql', '-target=17', *args, env={**os.environ, 'FLYWAY_PASSWORD': ''})


def notify_changes(remark_from, overrides=None):
    """各模板预期的列改动；名称或内容与默认值相同的列不出现在差异里。"""
    old = {
        'edu.student.class.joined': ('学生被加入班级', '你已被加入班级：{className}。'),
        'edu.student.course.assigned': ('学生被分配课程', '你所在班级 {className} 已分配课程：{courseName}。'),
        'edu.student.homework.created': ('新作业发布', '课程 {courseName} 发布了新作业：{homeworkName}，截止时间：{deadline}。'),
        'edu.student.homework.updated': ('作业变更', '课程 {courseName} 的作业 {homeworkName} 已更新，请及时查看，截止时间：{deadline}。'),
        'edu.student.homework.corrected': ('作业批改完成', '作业 {homeworkName} 已批改完成，得分：{score}，评语：{comment}。'),
        'edu.student.homework.redo': ('作业打回重做', '作业 {homeworkName} 已被打回重做，请根据要求重新提交。'),
        'edu.student.homework.remind': ('作业催交', '作业 {homeworkName} 尚未提交，请在 {deadline} 前完成提交。'),
        'edu.student.message.reply': ('教师回复留言', '课程 {courseName} 收到教师回复：{content}'),
        'edu.teacher.message.created': ('学生发起留言', '学生 {studentName} 在课程 {courseName} 发起留言：{content}'),
        'edu.admin.public-course.pending': ('公共课待审批', '公共课 {courseName} 已提交审批，提交教师：{teacherName}。'),
        'edu.teacher.public-course.audit-result': ('公共课审批结果', '公共课 {courseName} 审批结果：{auditResult}，审批意见：{auditRemark}。'),
    }
    result = {}
    for code, (new_name, new_content) in NOTIFY.items():
        old_name, old_content = old[code]
        columns = {'content': (old_content, new_content), 'remark': (remark_from, NOTIFY_REMARK)}
        if old_name != new_name:
            columns['name'] = (old_name, new_name)
        result[code] = columns
    for code, columns in (overrides or {}).items():
        result[code] = {k: v for k, v in {**result[code], **columns}.items() if v is not None}
    return result


CUSTOMIZE = """
-- 学校改过的菜单名称
UPDATE system_menu SET name = '查询本校作业' WHERE id = 5080;
UPDATE system_menu SET name = '本校站内信' WHERE id = 2144;
-- 学校在「实训任务管理」下自建了同名按钮「新建任务」，5077 改名会造成同级重名
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status)
  VALUES (90001, '新建任务', 'edu:homework:create-school', 3, 99, 5076, '', '', '', 0);
-- 字典：学校改过名称与标签；另有学校字典已叫「教育层次」
UPDATE system_dict_type SET name = '作业类别' WHERE id = 2011;
INSERT INTO system_dict_type (id, name, type, status, remark) VALUES (90001, '教育层次', 'school_education_level', 0, '');
UPDATE system_dict_data SET label = '添加' WHERE id = 18;
-- 通知模板：模拟经 V7 补建的库（ID 不同、备注为旧默认值），并有学校改写
UPDATE system_notify_template SET id = id + 100, remark = '教学业务站内信模板';
UPDATE system_notify_template SET content = '本校：作业 {homeworkName} 被打回。' WHERE code = 'edu.student.homework.redo';
UPDATE system_notify_template SET name = '作业发布提醒' WHERE code = 'edu.student.homework.created';
UPDATE system_notify_template SET remark = '本校模板' WHERE code = 'edu.student.homework.remind';
-- 授权修复的边界
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type) VALUES
  (301, '辅导员', 'counselor', 10, 1, '', 0, 2), (302, '通知管理员', 'notify_admin', 11, 1, '', 0, 2),
  (303, '日志查看', 'log_viewer', 12, 1, '', 0, 2), (304, '已删除角色', 'removed_role', 13, 1, '', 0, 2),
  (305, 'AI 管理', 'ai_admin', 14, 1, '', 0, 2);
UPDATE system_role SET deleted = b'1' WHERE id = 304;
UPDATE system_menu SET status = 1 WHERE id = 2760;
INSERT INTO system_role_menu (role_id, menu_id) VALUES
  (301, 5107), (301, 5109),
  (302, 2151),
  (303, 1078), (303, 5126),
  (304, 5107), (304, 5117),
  (305, 2761),
  (159, 5114);
INSERT INTO system_role_menu (role_id, menu_id, deleted) VALUES (303, 5118, b'1');
"""
# 辅导员只有留言互动：补 5066 和页面 5107 的上级目录 5047，不补 5066 所在的课程页面与目录；
# 通知管理员只有页面 2151：逐级补 2144、2739、1；访问日志 1078 的上级是页面，不补；
# 直接挂在目录 5047 下的按钮 5126 不作为起点，不补 5047；
# 已删除角色、已删除授权不作为起点；上级目录 2760 已停用时不补。
CUSTOM_GRANTS = BASELINE_GRANTS | {(301, 5066), (301, 5047), (302, 2144), (302, 2739), (302, 1)}


parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--baseline15', help='基线 15 的 SQL 文件；新仓库没有旧历史时显式提供')
parser.add_argument('--baseline16', help='基线 16 的 SQL 文件；新仓库没有旧历史时显式提供')
parser.add_argument('--baseline17', help='基线 17 的 SQL 文件；新仓库没有旧历史时显式提供')
args = parser.parse_args()
try:
    base16 = baseline(16, path=args.baseline16)
    base17 = baseline(17, worktree=True, path=args.baseline17)
except (OSError, ValueError, RuntimeError, AssertionError) as error:
    parser.error(str(error))


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

    # 1. 基线 16 + V17：逐行核对，其余表不变 --------------------------------------------------
    create('up16', base16)
    before = snapshot('up16')
    sql(V17, 'up16')
    after = snapshot('up16')
    assert_upgrade(before, after, 'up16', MENU_RENAMES, DICT_TYPE_RENAMES, DICT_DATA_RELABELS,
                   notify_changes('教学业务 P0 站内信模板'), BASELINE_GRANTS,
                   {code: NOTIFY_REMARK for code in NOTIFY})
    lint('up16')

    # 2. 重复执行：一行不改 ---------------------------------------------------------------------
    sql(V17, 'up16')
    equal(snapshot('up16'), after, 'up16 重复执行 V17')

    # 3. 学校自定义与授权边界 ------------------------------------------------------------------
    create('customized', base16)
    sql(CUSTOMIZE, 'customized')
    before = snapshot('customized')
    sql(V17, 'customized')
    after = snapshot('customized')
    kept_menus = {5080, 2144, 5077}
    kept_dicts = {2011, 2009}
    assert_upgrade(
        before, after, 'customized',
        {i: names for i, names in MENU_RENAMES.items() if i not in kept_menus},
        {i: names for i, names in DICT_TYPE_RENAMES.items() if i not in kept_dicts},
        {i: labels for i, labels in DICT_DATA_RELABELS.items() if i != 18},
        notify_changes('教学业务站内信模板', {
            'edu.student.homework.redo': {'content': None},
            'edu.student.homework.created': {'name': None},
            'edu.student.homework.remind': {'remark': None},
        }),
        CUSTOM_GRANTS,
        {**{code: NOTIFY_REMARK for code in NOTIFY}, 'edu.student.homework.remind': '本校模板'})
    equal(sql('SELECT id, name FROM system_menu WHERE id IN (2144, 5077, 5080, 90001) ORDER BY id',
              'customized').splitlines(),
          ['2144\t本校站内信', '5077\t创建任务', '5080\t查询本校作业', '90001\t新建任务'],
          'customized: 学校改过的名称与同级重名保持原样')
    equal(sql("SELECT code, name, content FROM system_notify_template WHERE code IN "
              "('edu.student.homework.redo', 'edu.student.homework.created') ORDER BY code", 'customized').splitlines(),
          ['edu.student.homework.created\t作业发布提醒\t课程「{courseName}」发布了新任务「{homeworkName}」，截止时间：{deadline}。',
           'edu.student.homework.redo\t任务打回重做\t本校：作业 {homeworkName} 被打回。'],
          'customized: 名称与内容分别保留学校改写')
    custom_state = snapshot('customized')
    sql(V17, 'customized')
    equal(snapshot('customized'), custom_state, 'customized 重复执行 V17')

    # 4. 基线 17：V17 不改动；与基线 16 执行 V17 的结果逐表一致 -------------------------------------
    # 新增授权的 id 须与 V17 在基线 16 上分配的一致（6436-6440），因此按 id 逐行比较。
    create('fresh17', base17)
    fresh = snapshot('fresh17')
    sql(V17, 'fresh17')
    equal(snapshot('fresh17'), fresh, 'fresh17 V17 无改动')
    equal(structure('fresh17'), structure('up16'), '基线 17 与基线 16 + V17：全部表结构')
    equal(contents('fresh17'), contents('up16'), '基线 17 与基线 16 + V17：逐表逐行数据')
    equal(projections('fresh17'), projections('up16'), '基线 17 与基线 16 升级结果一致')

    available = subprocess.run(['docker', 'image', 'inspect', FLYWAY_IMAGE], capture_output=True).returncode == 0
    if available:
        create('fly16', base16)
        flyway('fly16', 'baseline', '-baselineVersion=16', '-baselineDescription=ry261-test.sql')
        flyway('fly16', 'migrate')
        equal(sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank', 'fly16').splitlines(),
              ['16\t1', '17\t1'], 'Flyway 16 -> 17')
        flyway('fly16', 'migrate')
        flyway('fly16', 'validate')
        equal(scalar('SELECT COUNT(*) FROM flyway_schema_history', 'fly16'), '2', 'Flyway 17 latest')
        equal(projections('fly16'), projections('up16'), 'Flyway 16 -> 17 projections')

        create('fly15', baseline(15, path=args.baseline15))
        flyway('fly15', 'baseline', '-baselineVersion=15', '-baselineDescription=ry261-test.sql')
        flyway('fly15', 'migrate')
        flyway('fly15', 'validate')
        equal(sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank', 'fly15').splitlines(),
              ['15\t1', '16\t1', '17\t1'], 'Flyway 15 -> 16 -> 17')
        equal(projections('fly15'), projections('up16'), 'Flyway 15 -> 17 projections')
        create('fly17', base17)
        flyway('fly17', 'baseline', '-baselineVersion=17', '-baselineDescription=ry261-test.sql')
        flyway('fly17', 'migrate')
        flyway('fly17', 'validate')
        equal(scalar('SELECT COUNT(*) FROM flyway_schema_history', 'fly17'), '1', 'Flyway baseline 17 latest')
        flyway_note = 'Flyway 16->17, 15->17 and baseline 17 passed'
    else:
        flyway_note = f'Flyway skipped (local image {FLYWAY_IMAGE} absent)'
    print('PASS: up16 row diff and lint, repeated V17, customized, '
          f'fresh17 no-op/schema/row equivalence; {flyway_note}')
except (OSError, ValueError, RuntimeError, AssertionError) as error:
    print(f'ERROR {error}', file=sys.stderr)
    sys.exit(2)
finally:
    subprocess.run(['docker', 'rm', '-f', NAME], capture_output=True, text=True)
