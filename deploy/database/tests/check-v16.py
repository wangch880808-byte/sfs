#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对 V16 与基线 16。

运行：python3 deploy/database/tests/check-v16.py
仅用本地镜像（--pull=never）；每次使用独立库、tmpfs 与一次性容器，绝不连接现有数据库。
场景：up15、fresh16、edge、edge_no_sem；本地有 Flyway 镜像时增加 fly15、fly16。
基线 15 从单仓库历史中读取标记为 15 的最新提交，或指定 NUS_BASELINE15_SQL。
基线 16 优先用工作区文件；工作区已同步到更高版本时改读历史中标记为 16 的最新提交，或指定 NUS_BASELINE16_SQL。
--baseline15 /path/to/file.sql 可显式提供基线 15，原 NUS_BASELINE15_SQL 环境变量仍可用。
--baseline16 /path/to/file.sql 可显式提供基线 16，原 NUS_BASELINE16_SQL 环境变量仍可用。
"""
import difflib
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
V16 = (MIGRATIONS / 'V16__v1_0_1_5_business_completion.sql').read_text(encoding='utf-8')
MYSQL_IMAGE = os.environ.get('NUS_TEST_MYSQL_IMAGE', 'mysql:8.4.6')
FLYWAY_IMAGE = os.environ.get('NUS_TEST_FLYWAY_IMAGE', 'flyway/flyway:10.20.1-alpine')
NAME = 'nus-v16-check-' + uuid.uuid4().hex[:10]
WATCHED = ('system_users', 'edu_homework_record_history', 'edu_class_course', 'edu_homework',
           'edu_message', 'ai_edu_review_task', 'system_menu', 'system_role_menu',
           'system_dict_type', 'system_dict_data', 'infra_config', 'infra_job')
C1_MENUS = sorted((set(range(1093, 1110)) - {1099}) | {2083} | set(range(2130, 2144)))
A9_MENUS = (5063, 5101, 5102, 5104)
C1_DICTS = ('system_sms_channel_code', 'system_sms_template_type', 'system_sms_send_status',
            'system_sms_receive_status', 'system_mail_send_status', 'system_oauth2_grant_type',
            'date_interval', 'terminal')


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
        # 只报告有差异的表，避免整库快照淹没真正的差异
        lines = []
        for key in sorted(set(actual) | set(expected)):
            if actual.get(key) != expected.get(key):
                old = str(expected.get(key)).splitlines()
                new = str(actual.get(key)).splitlines()
                diff = list(difflib.unified_diff(old, new, 'expected', 'actual', lineterm='', n=0))
                lines.append(f'[{key}]\n' + '\n'.join(diff[:40] or [f'expected={expected.get(key)!r}', f'actual={actual.get(key)!r}']))
        raise AssertionError(f'{label}:\n' + '\n'.join(lines))
    raise AssertionError(f'{label}: expected {expected!r}, got {actual!r}')


def baseline15(path=None):
    return baseline_helpers.read_baseline(15, path, docs=(DOC,))[0]


def baseline16(path=None):
    return baseline_helpers.read_baseline(16, path, docs=(DOC,))[0]


def create(database, baseline):
    run('docker', 'exec', NAME, 'mysql', '-uroot', '-e', f'CREATE DATABASE `{database}`;')
    sql(baseline, database)


def snapshot(database):
    """完整表校验和及结构，用于确认重复执行没有改动。"""
    result = {}
    for table in WATCHED:
        ddl = re.sub(r' AUTO_INCREMENT=\d+', '', scalar(f'SHOW CREATE TABLE `{table}`;', database))
        checksum = scalar(f'CHECKSUM TABLE `{table}`;', database).split('\t')[-1]
        result[table] = ddl, checksum
    return result


def structure(database):
    """比较所有业务表结构，忽略自增计数。"""
    tables = sql("SELECT table_name FROM information_schema.tables "
                 f"WHERE table_schema = '{database}' AND table_type = 'BASE TABLE' ORDER BY table_name", database).split()
    return {table: re.sub(r' AUTO_INCREMENT=\d+', '', scalar(f'SHOW CREATE TABLE `{table}`;', database))
            for table in tables if table != 'flyway_schema_history'}


def projections(database):
    """基线省略已退休菜单，因此按实际业务可见行核对；不比较生成时间。"""
    queries = {
        'users': "SELECT id, user_type, password_change_required + 0 FROM system_users WHERE deleted = 0 ORDER BY id",
        'history': "SELECT id, record_id, event_type FROM edu_homework_record_history WHERE deleted = 0 ORDER BY id",
        'class_course': "SELECT id, class_id, course_id, semester_name, status FROM edu_class_course WHERE deleted = 0 ORDER BY id",
        'homework': "SELECT id, status, IFNULL(status_before_archive, -1) FROM edu_homework WHERE deleted = 0 ORDER BY id",
        'message': "SELECT id, type, read_status, IFNULL(read_time, '<NULL>') FROM edu_message WHERE deleted = 0 ORDER BY id",
        'ai_task': "SELECT id, IFNULL(teacher_id, -1), IFNULL(generation_token, '<NULL>'), IFNULL(error_message, '<NULL>') FROM ai_edu_review_task WHERE deleted = 0 ORDER BY id",
        'menus': "SELECT id, name, permission, type, parent_id, path, status FROM system_menu WHERE deleted = 0 ORDER BY id",
        'grants': "SELECT role_id, menu_id FROM system_role_menu WHERE deleted = 0 ORDER BY role_id, menu_id",
        'dict_type': "SELECT id, type, status FROM system_dict_type WHERE deleted = 0 ORDER BY id",
        'dict_data': "SELECT id, dict_type, value, status FROM system_dict_data WHERE deleted = 0 ORDER BY id",
        'config': "SELECT id, config_key, value FROM infra_config WHERE deleted = 0 ORDER BY id",
        'jobs': "SELECT id, name, status, handler_name, handler_param, cron_expression, retry_count, retry_interval, monitor_timeout FROM infra_job WHERE deleted = 0 ORDER BY id",
    }
    return {name: sql(query, database) for name, query in queries.items()}


def columns(database, table, names):
    quoted = ','.join("'%s'" % name for name in names)
    return sql(f"SELECT column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), column_comment "
               "FROM information_schema.columns "
               f"WHERE table_schema = '{database}' AND table_name = '{table}' AND column_name IN ({quoted}) "
               "ORDER BY ordinal_position", database).splitlines()


def index(database, table, name):
    return scalar("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                  "FROM information_schema.statistics "
                  f"WHERE table_schema = '{database}' AND table_name = '{table}' AND index_name = '{name}'", database)


def assert_effects(database):
    # AI 对话接口按“AI 对话”菜单上的权限标识授权。
    equal(scalar("SELECT permission FROM system_menu WHERE id = 2759 AND deleted = 0", database), 'ai:chat:use',
          'AI chat menu permission')
    # A1, A2, A3, A5, A6 structure.
    equal(len(columns(database, 'system_users', ['password_change_required'])), 1, 'A1 column')
    equal(index(database, 'edu_homework_record_history', 'idx_record_create_time'),
          'record_id,create_time', 'A2 history index')
    equal(len(columns(database, 'edu_class_course', ['status'])), 1, 'A3 class archive column')
    equal(len(columns(database, 'edu_homework', ['status_before_archive'])), 1, 'A3 homework archive column')
    equal(len(columns(database, 'edu_message', ['read_status', 'read_time'])), 2, 'A6 read columns')
    equal(index(database, 'edu_message', 'idx_message_conversation'),
          'class_id,course_id,student_id,id', 'A6 conversation index')
    equal(len(columns(database, 'ai_edu_review_task', ['teacher_id', 'generation_token', 'error_message'])),
          3, 'A5 task columns')
    # C1 and A9 remove only original, matching menus and grants.
    retired = ','.join(map(str, C1_MENUS + list(A9_MENUS)))
    equal(scalar(f'SELECT COUNT(*) FROM system_menu WHERE id IN ({retired}) AND deleted = 0', database),
          '0', 'C1/A9 original menus retired')
    equal(scalar(f'SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN ({retired}) AND deleted = 0', database),
          '0', 'C1/A9 grants retired')
    equal(scalar("SELECT GROUP_CONCAT(parent_id ORDER BY id) FROM system_menu "
                 "WHERE id IN (5064,5065,5066)", database), '5062,5062,5062', 'A9 button relocation')
    equal(scalar("SELECT COUNT(*) FROM system_menu WHERE parent_id = 5096 AND deleted = 0", database),
          '4', 'AI review buttons retained')
    equal(scalar("SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN (5096,5097,5098,5099,5100) "
                 "AND deleted = 0", database), '10', 'AI review grants retained')
    equal(scalar("SELECT COUNT(*) FROM system_dict_type WHERE type IN (" +
                 ','.join("'%s'" % d for d in C1_DICTS) + ") AND status = 1 AND deleted = 0", database),
          '8', 'C1 scaffold dictionaries disabled')
    # A6 grants are identified by permission, never by the auto-assigned IDs.
    equal(sql("SELECT permission, status FROM system_menu WHERE permission IN "
              "('edu:message:query','edu:message:manage','edu:student-message:query','edu:student-message:send') "
              "AND deleted = 0 ORDER BY permission", database).splitlines(),
          ['edu:message:manage\t0', 'edu:message:query\t0',
           'edu:student-message:query\t0', 'edu:student-message:send\t0'], 'A6 menu permissions')
    equal(sql("SELECT role_id, m.permission FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id "
              "WHERE rm.deleted = 0 AND ((rm.role_id = 161 AND m.permission IN "
              "('edu:message:query','edu:message:manage')) OR (rm.role_id = 200 AND m.permission IN "
              "('edu:student-message:query','edu:student-message:send'))) "
              "ORDER BY role_id, m.permission", database).splitlines(),
          ['161\tedu:message:manage', '161\tedu:message:query',
           '200\tedu:student-message:query', '200\tedu:student-message:send'], 'A6 role grants')
    equal(scalar("SELECT COUNT(*) FROM system_role_menu WHERE role_id = 161 AND menu_id = 5047 AND deleted = 0", database),
          '1', 'A6 teacher parent grant')
    equal(scalar("SELECT CONCAT(status, '|', handler_param, '|', cron_expression) FROM infra_job "
                 "WHERE handler_name = 'eduPublicCourseSyncJob' AND deleted = 0", database),
          '1||0 15 0 * * ?', 'A7 daily job')
    equal(scalar("SELECT COUNT(*) FROM system_dict_data WHERE dict_type = 'system_login_feature' AND deleted = 0", database),
          '0', 'A8 login dictionary data retired')
    equal(scalar("SELECT COUNT(*) FROM system_dict_type WHERE type = 'system_login_feature' AND deleted = 0", database),
          '0', 'A8 login dictionary type retired')
    equal(sql("SELECT config_key, value FROM infra_config WHERE config_key IN "
              "('edu.score.good-ratio','edu.score.excellent-ratio','edu.course.unlock-ratio') "
              "AND deleted = 0 ORDER BY config_key", database).splitlines(),
          ['edu.course.unlock-ratio\t0.8', 'edu.score.excellent-ratio\t0.85',
           'edu.score.good-ratio\t0.75'], 'A4 thresholds')
    equal(scalar("SELECT COUNT(*) FROM infra_config WHERE config_key = 'edu.current.academic.year' "
                 "AND value = '2025' AND deleted = 0", database), '0', 'A4 stale year retired')


def flyway(database, *args):
    return run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{NAME}',
               '-e', 'FLYWAY_PASSWORD', '-v', f'{MIGRATIONS}:/flyway/sql:ro', FLYWAY_IMAGE,
               f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
               '-user=root', '-locations=filesystem:/flyway/sql', '-target=16', *args, env={**os.environ, 'FLYWAY_PASSWORD': ''})


parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--baseline15', help='基线 15 的 SQL 文件；新仓库没有旧历史时显式提供')
parser.add_argument('--baseline16', help='基线 16 的 SQL 文件；新仓库没有旧历史时显式提供')
args = parser.parse_args()
try:
    old = baseline15(args.baseline15)
    new = baseline16(args.baseline16)
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

    create('up15', old)
    sql(V16, 'up15')
    assert_effects('up15')
    equal(scalar("SELECT COUNT(*) FROM system_users WHERE user_type = 2 AND password_change_required = b'1'", 'up15'),
          '0', 'A1 baseline has no student')
    first = snapshot('up15')
    sql(V16, 'up15')
    equal(snapshot('up15'), first, 'up15 repeated V16')

    create('fresh16', new)
    before = snapshot('fresh16')
    sql(V16, 'fresh16')
    equal(snapshot('fresh16'), before, 'fresh16 V16 no-op')
    assert_effects('fresh16')
    equal(projections('fresh16'), projections('up15'), 'baseline 16 active data vs upgraded 15')
    equal(structure('fresh16'), structure('up15'), 'baseline 16 schema vs upgraded 15')

    create('edge', old)
    sql("""
INSERT INTO system_users (username, user_type, nickname) VALUES
  ('edge-student', 2, '学生'), ('edge-teacher', 1, '教师'), ('edge-admin', 0, '管理员');
INSERT INTO edu_class (id, class_name, grade_year, dept_id, education_level, schooling_year)
  VALUES (101, '甲班', 2026, 1, 1, 4), (102, '乙班', 2026, 1, 1, 4), (103, '丙班', 2026, 1, 1, 4);
INSERT INTO capacity_course (id, indicator_id, course_name, is_public_course, public_audit_status, status)
  VALUES (201, 1, '公共课', 0, 2, 0), (202, 1, '停用课', 0, 2, 1);
ALTER TABLE edu_class_course ADD COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1已归档';
INSERT INTO edu_class_course (class_id, course_id, semester_name, status)
  VALUES (101, 201, '2025-2026 下学期', 1), (102, 201, '2025-2026 下学期', 0);
INSERT INTO edu_message (id, class_id, course_id, student_id, content, type)
  VALUES (10, 101, 201, 901, '第一条', 0), (11, 101, 201, 901, '回复', 1),
         (12, 101, 201, 901, '待回复', 0);
UPDATE system_menu SET name = '学校短信模板', path = 'custom-sms' WHERE id = 1100;
UPDATE system_menu SET path = 'custom-ai' WHERE id = 5101;
UPDATE system_menu SET path = 'custom-bind' WHERE id = 5064;
""", 'edge')
    sql(V16, 'edge')
    equal(sql("SELECT username, password_change_required + 0 FROM system_users "
              "WHERE username LIKE 'edge-%' ORDER BY username", 'edge').splitlines(),
          ['edge-admin\t0', 'edge-student\t1', 'edge-teacher\t0'], 'A1 only student flagged')
    equal(sql("SELECT class_id, course_id, status FROM edu_class_course ORDER BY class_id", 'edge').splitlines(),
          ['101\t201\t1', '102\t201\t0', '103\t201\t0'], 'A7 archived kept, existing not duplicated, missing filled')
    equal(sql('SELECT id, read_status FROM edu_message ORDER BY id', 'edge').splitlines(),
          ['10\t1', '11\t1', '12\t0'], 'A6 historical read backfill')
    equal(sql('SELECT id, name, path, deleted + 0 FROM system_menu WHERE id IN (1093,1100,5101) ORDER BY id',
              'edge').splitlines(),
          ['1093\t短信管理\tsms\t0', '1100\t学校短信模板\tcustom-sms\t0',
           '5101\tAI对话\tcustom-ai\t0'], 'C1/A9 customized menus and ancestor protected')
    equal(sql('SELECT id, parent_id, path, deleted + 0 FROM system_menu WHERE id IN (5063,5064) ORDER BY id',
              'edge').splitlines(),
          ['5063\t5047\tclass-course\t0', '5064\t5063\tcustom-bind\t0'],
          'A9 customized button not moved; parent kept')
    sql("UPDATE system_users SET password_change_required = b'0' WHERE username = 'edge-student'; "
        "UPDATE edu_message SET read_status = 1 WHERE id = 12;", 'edge')
    edge_state = snapshot('edge')
    sql(V16, 'edge')
    equal(snapshot('edge'), edge_state, 'edge repeated V16 keeps later changes')

    create('edge_no_sem', old)
    sql("""
UPDATE infra_config SET value = '未设置' WHERE config_key = 'edu.current.semester.name';
INSERT INTO edu_class (id, class_name, grade_year, dept_id, education_level, schooling_year)
  VALUES (104, '丁班', 2026, 1, 1, 4);
INSERT INTO capacity_course (id, indicator_id, course_name, is_public_course, public_audit_status, status)
  VALUES (204, 1, '公共课', 0, 2, 0);
""", 'edge_no_sem')
    sql(V16, 'edge_no_sem')
    equal(scalar('SELECT COUNT(*) FROM edu_class_course', 'edge_no_sem'), '0', 'A7 unset semester does not backfill')

    available = subprocess.run(['docker', 'image', 'inspect', FLYWAY_IMAGE], capture_output=True).returncode == 0
    if available:
        create('fly15', old)
        flyway('fly15', 'baseline', '-baselineVersion=15', '-baselineDescription=ry261-test.sql')
        flyway('fly15', 'migrate')
        equal(sql("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank", 'fly15').splitlines(),
              ['15\t1', '16\t1'], 'Flyway 15 -> 16')
        flyway('fly15', 'migrate')
        flyway('fly15', 'validate')
        equal(scalar('SELECT COUNT(*) FROM flyway_schema_history', 'fly15'), '2', 'Flyway 16 latest')
        equal(projections('fly15'), projections('up15'), 'Flyway upgrade projections')
        create('fly16', new)
        flyway('fly16', 'baseline', '-baselineVersion=16', '-baselineDescription=ry261-test.sql')
        flyway('fly16', 'migrate')
        flyway('fly16', 'validate')
        equal(scalar('SELECT COUNT(*) FROM flyway_schema_history', 'fly16'), '1', 'Flyway baseline 16 latest')
        flyway_note = 'Flyway 15->16 and baseline 16 passed'
    else:
        flyway_note = f'Flyway skipped (local image {FLYWAY_IMAGE} absent)'
    print('PASS: up15, repeated V16, fresh16 no-op/equivalence, edge, edge_no_sem; ' + flyway_note)
finally:
    subprocess.run(['docker', 'rm', '-f', NAME], capture_output=True, text=True)
