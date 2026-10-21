#!/usr/bin/env python3
"""Verify V15 and baseline 15 against isolated MySQL; never connects to an existing database.

Only local images are used (--pull=never); run with: python3 deploy/database/tests/check-v15.py

Scenarios (each in its own database of one throwaway container, --network none, tmpfs):
  up14    baseline 14 -> V15: every C1-C6 effect, then V15 again changes nothing
  fresh15 baseline 15 -> V15 changes nothing; baseline 15 equals baseline 14 + V15
  edge    baseline 14 + local customizations -> V15: customized menus and their ancestors,
          referenced dictionary items, a school-defined initial password, existing jobs,
          a school-defined data scope and a renamed role 159 survive
  grants  baseline 14 with a changed/deleted target menu and an existing grant -> V15 grants only
          matching live menus, once
  fly14/fly15  the real Flyway image (when present locally) upgrades 14 -> 15 and finds 15 current
Baselines 14 and 15 are read from shili-platform history (newest matching commits), or
NUS_BASELINE14_SQL and NUS_BASELINE15_SQL respectively. Current baseline may be newer.
--baseline14 /path/to/file.sql 可显式提供基线 14，原 NUS_BASELINE14_SQL 环境变量仍可用。
--baseline15 /path/to/file.sql 可显式提供基线 15，原 NUS_BASELINE15_SQL 环境变量仍可用。
"""
import argparse
import importlib.util
import sys
import os
import pathlib
import re
import secrets
import subprocess
import time
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('baseline_helpers', pathlib.Path(__file__).with_name('check-v18.py'))
baseline_helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline_helpers)

ROOT = pathlib.Path(__file__).resolve().parents[3]
DOC = ROOT / 'NUS-doc'
BASELINE_REL = '3-数据库文件/ry261-test.sql'
MIGRATIONS = ROOT / 'deploy/database/migrations'
V15 = (MIGRATIONS / 'V15__v1_0_1_4_security_workflow.sql').read_text(encoding='utf-8')
MYSQL_IMAGE = os.environ.get('NUS_TEST_MYSQL_IMAGE', 'mysql:8.4.6')
FLYWAY_IMAGE = os.environ.get('NUS_TEST_FLYWAY_IMAGE', 'flyway/flyway:10.20.1-alpine')
NAME = 'nus-v15-check-' + uuid.uuid4().hex[:10]

DELETED_MENUS = [114, 116, 1255, 1256, 1257, 1258, 1259, 1260, 5103, 5105, 5110, 5112]
DISABLED_MENUS = [1093, 1263, 2130, 2920]
UNSUPPORTED_AI = ['AzureOpenAI', 'BaiChuan', 'DouBao', 'Gemini', 'HunYuan', 'Midjourney', 'SiliconFlow',
                  'StableDiffusion', 'Suno', 'TongYi', 'XingHuo', 'YiYan']
SUPPORTED_AI = ['Anthropic', 'DeepSeek', 'MiniMax', 'Moonshot', 'Ollama', 'OpenAI', 'ZhiPu']
SCHOOL_ADMIN_MENUS = ['1001', '5054', '5117']
ROLE_SCOPES_V15 = {'1': 'super_admin|1', '159': 'school_admin|1', '160': 'college_admin|1', '161': 'teacher|1',
                   '200': 'student|1'}
WATCHED = ['system_menu', 'system_role', 'system_role_menu', 'infra_config', 'system_dict_type', 'system_dict_data', 'infra_job',
           'ai_tool', 'ai_api_key', 'ai_model', 'system_social_client', 'edu_homework_record', 'edu_class_student',
           'edu_homework']
PROJECTIONS = {
    'system_menu': "SELECT id, name, permission, type, sort, parent_id, path, icon, IFNULL(component, '<NULL>'), "
                   "IFNULL(component_name, '<NULL>'), status, visible + 0, keep_alive + 0, always_show + 0 "
                   "FROM system_menu WHERE deleted = 0 ORDER BY id",
    'system_role': 'SELECT id, name, code, sort, data_scope, data_scope_dept_ids, status, type FROM system_role '
                   'WHERE deleted = 0 ORDER BY id',
    'system_role_menu': 'SELECT id, role_id, menu_id FROM system_role_menu WHERE deleted = 0 ORDER BY id',
    'infra_config': "SELECT id, category, type, name, config_key, SHA2(value, 256), visible + 0, IFNULL(remark, '<NULL>') "
                    'FROM infra_config WHERE deleted = 0 ORDER BY id',
    'system_dict_type': "SELECT id, name, type, status, IFNULL(remark, '<NULL>'), IFNULL(deleted_time, '<NULL>') "
                        'FROM system_dict_type WHERE deleted = 0 ORDER BY id',
    'system_dict_data': "SELECT id, sort, label, value, dict_type, status, IFNULL(color_type, '<NULL>'), "
                        "IFNULL(css_class, '<NULL>'), IFNULL(remark, '<NULL>') FROM system_dict_data "
                        'WHERE deleted = 0 ORDER BY id',
    'infra_job': "SELECT id, name, status, handler_name, IFNULL(handler_param, '<NULL>'), cron_expression, retry_count, "
                 'retry_interval, monitor_timeout FROM infra_job WHERE deleted = 0 ORDER BY id',
}


def run(*args, input=None, env=None):
    return subprocess.run([str(a) for a in args], input=input, encoding='utf-8', check=True,
                          capture_output=True, env=env).stdout


def sql(text, database):
    return run('docker', 'exec', '-i', NAME, 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '-B',
               database, input=text)


def value(query, database):
    return sql(query, database).strip()


def column(query, database):
    return sql(query, database).split()


def check(actual, expected, label):
    assert actual == expected, f'{label}: expected {expected!r}, got {actual!r}'


def baseline14(path=None):
    return baseline_helpers.read_baseline(14, path, docs=(DOC,))[0]


def baseline15(path=None):
    return baseline_helpers.read_baseline(15, path, docs=(DOC,))[0]


def create(database, baseline):
    run('docker', 'exec', NAME, 'mysql', '-uroot', '-e', f'CREATE DATABASE {database};')
    sql(baseline, database)


def snapshot(database):
    """Structure and full-row checksums of every table V15 may touch."""
    state = {}
    for table in WATCHED:
        ddl = re.sub(r' AUTO_INCREMENT=\d+', '', value(f'SHOW CREATE TABLE {table}', database))
        state[table] = (ddl, value(f'CHECKSUM TABLE {table}', database).split('\t')[-1])
    return state


def schema(database):
    """Every table's DDL and live row count (rows with deleted = 0 when the table is soft-deleting)."""
    tables = [line.split('\t') for line in sql(
        "SELECT table_name, SUM(column_name = 'deleted') FROM information_schema.columns "
        f"WHERE table_schema = '{database}' GROUP BY table_name ORDER BY table_name", database).splitlines()]
    ddl = dict(line.split('\t', 1) for line in sql(
        ''.join(f'SHOW CREATE TABLE `{table}`;\n' for table, _ in tables), database).splitlines())
    counts = dict(line.split('\t') for line in sql(' UNION ALL '.join(
        f"SELECT '{table}', COUNT(*) FROM `{table}`" + (' WHERE deleted = 0' if soft != '0' else '')
        for table, soft in tables) + ';', database).splitlines())
    return {table: (re.sub(r' AUTO_INCREMENT=\d+', '', ddl[table]), counts[table]) for table, _ in tables}


def projections(database):
    return {name: sql(query, database) for name, query in PROJECTIONS.items()}


def menu_state(database, ids):
    rows = sql('SELECT id, status, deleted + 0 FROM system_menu WHERE id IN (%s) ORDER BY id'
               % ','.join(map(str, ids)), database)
    return {int(i): (int(s), int(d)) for i, s, d in (line.split('\t') for line in rows.splitlines())}


def role_scopes(database):
    return dict(line.split('\t') for line in sql(
        "SELECT id, CONCAT(code, '|', data_scope) FROM system_role WHERE deleted = 0 ORDER BY id", database).splitlines())


def school_admin_grants(database):
    """Active role 159 grants on the three V15 menus, duplicates included."""
    return column('SELECT menu_id FROM system_role_menu WHERE role_id = 159 AND deleted = 0 '
                  'AND menu_id IN (1001, 5054, 5117) ORDER BY menu_id, id', database)


def other_role_grants(database):
    return value('SELECT COUNT(*) FROM system_role_menu WHERE deleted = 0 AND role_id IN (160, 161, 200) '
                 'AND menu_id NOT IN (%s)' % ','.join(map(str, DELETED_MENUS)), database)


def dict_status(database, dict_type, values):
    rows = sql("SELECT value, status FROM system_dict_data WHERE deleted = 0 AND dict_type = '%s' AND value IN (%s)"
               % (dict_type, ','.join("'%s'" % v for v in values)), database)
    return dict(line.split('\t') for line in rows.splitlines())


def index_columns(database, table, index):
    return value(f"SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics "
                 f"WHERE table_schema = '{database}' AND table_name = '{table}' AND index_name = '{index}'", database)


def assert_structure(database):
    check(value("SELECT CONCAT(data_type, '|', is_nullable, '|', column_comment) FROM information_schema.columns "
                f"WHERE table_schema = '{database}' AND table_name = 'edu_homework_record' "
                "AND column_name = 'redo_deadline'", database), 'datetime|YES|重做截止时间', 'redo_deadline column')
    check(value("SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns "
                f"WHERE table_schema = '{database}' AND table_name = 'edu_homework_record' "
                "AND column_name IN ('redo_count', 'redo_deadline')", database), 'redo_count,redo_deadline',
          'redo_deadline position')
    check(index_columns(database, 'edu_class_student', 'idx_edu_class_student_student'), 'student_id', 'class student index')
    check(index_columns(database, 'edu_homework', 'idx_edu_homework_class_status'), 'class_id,status', 'homework index')
    check(index_columns(database, 'edu_homework_record', 'idx_edu_homework_record_homework_status'),
          'homework_id,status', 'homework record index')


def flyway(database, *args):
    return run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{NAME}',
               '-e', 'FLYWAY_PASSWORD', '-v', f'{MIGRATIONS}:/flyway/sql:ro',
               FLYWAY_IMAGE, f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
               '-user=root', '-locations=filesystem:/flyway/sql', '-target=15', *args, env={**os.environ, 'FLYWAY_PASSWORD': ''})


parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--baseline14', help='基线 14 的 SQL 文件；新仓库没有旧历史时显式提供')
parser.add_argument('--baseline15', help='基线 15 的 SQL 文件；新仓库没有旧历史时显式提供')
args = parser.parse_args()
try:
    old = baseline14(args.baseline14)
    new = baseline15(args.baseline15)
except (OSError, ValueError, RuntimeError, AssertionError) as error:
    parser.error(str(error))


try:
    run('docker', 'run', '-d', '--pull=never', '--name', NAME, '--network', 'none', '--tmpfs', '/var/lib/mysql',
        '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', MYSQL_IMAGE)
    for attempt in range(120):
        try:
            logs = subprocess.run(['docker', 'logs', NAME], text=True, capture_output=True)
            if 'MySQL init process done. Ready for start up.' not in logs.stdout + logs.stderr:
                time.sleep(1)
                continue
            run('docker', 'exec', NAME, 'mysqladmin', 'ping', '-uroot')
            break
        except subprocess.CalledProcessError:
            time.sleep(1)
    else:
        raise RuntimeError('MySQL did not start')


    # 1. Baseline 14 -> V15 --------------------------------------------------------------------------
    create('up14', old)
    check(value("SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN (%s)" % ','.join(map(str, DELETED_MENUS)),
                'up14'), '4', 'baseline 14 grants on retired menus')
    check(role_scopes('up14'), {**ROLE_SCOPES_V15, '159': 'school_admin|4', '161': 'teacher|5'}, 'baseline 14 roles')
    check(school_admin_grants('up14'), [], 'baseline 14 school_admin grants')
    other_grants = other_role_grants('up14')
    sql(V15, 'up14')
    assert_structure('up14')
    check(value("SELECT CONCAT(category, '|', value) FROM infra_config "
                "WHERE config_key = 'system.user.register-enabled'", 'up14'), 'biz|false', 'register switch')
    check(value("SELECT CONCAT(value = '', '|', remark) FROM infra_config "
                "WHERE config_key = 'system.user.init-password'", 'up14'),
          '1|为空时批量导入非学生账号须填写初始密码', 'upstream initial password cleared')
    menus = menu_state('up14', DELETED_MENUS + DISABLED_MENUS + [1094, 1264, 2131, 2921])
    for menu in DELETED_MENUS:
        check(menus[menu], (1, 1), f'menu {menu} retired')
    for menu in DISABLED_MENUS:  # 1093 and 2130 have a NULL component
        check(menus[menu], (1, 0), f'menu {menu} disabled')
    for menu in (1094, 1264, 2131, 2921):
        check(menus[menu], (0, 0), f'original child {menu} untouched')
    check(value("SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN (%s)" % ','.join(map(str, DELETED_MENUS)),
                'up14'), '0', 'grants on retired menus')
    check(sql("SELECT d.dict_type, d.sort, d.label, d.value, d.status FROM system_dict_data d "
              "JOIN system_dict_type t ON t.type = d.dict_type AND t.deleted = 0 AND t.status = 0 "
              "WHERE d.deleted = 0 AND d.dict_type IN ('system_menu_type', 'system_data_scope', 'user_type') "
              "ORDER BY d.dict_type, d.sort", 'up14').splitlines(), [
        'system_data_scope\t1\t全部数据权限\t1\t0', 'system_data_scope\t2\t指定部门数据权限\t2\t0',
        'system_data_scope\t3\t本部门数据权限\t3\t0', 'system_data_scope\t4\t本部门及以下数据权限\t4\t0',
        'system_data_scope\t5\t仅本人数据权限\t5\t0', 'system_menu_type\t1\t目录\t1\t0', 'system_menu_type\t2\t菜单\t2\t0',
        'system_menu_type\t3\t按钮\t3\t0', 'user_type\t2\t管理员\t2\t0'], 'restored dictionaries')
    check(dict_status('up14', 'ai_platform', UNSUPPORTED_AI + SUPPORTED_AI),
          {**{v: '1' for v in UNSUPPORTED_AI}, **{v: '0' for v in SUPPORTED_AI}}, 'ai_platform')
    check(dict_status('up14', 'system_social_type', ['10', '20', '30', '31', '32', '40', '50']),
          {'10': '0', '20': '0', '30': '1', '31': '1', '32': '1', '40': '0', '50': '0'}, 'system_social_type')
    check(value("SELECT value FROM system_dict_data WHERE dict_type = 'system_login_feature' "
                "AND label = 'dingtalk_login'", 'up14'), 'false', 'dingtalk_login')
    check(sql("SELECT handler_name, handler_param, cron_expression, status, CHAR_LENGTH(name) <= 32 FROM infra_job "
              "WHERE handler_name LIKE '%LogCleanJob' ORDER BY cron_expression", 'up14').splitlines(), [
        'accessLogCleanJob\t180\t0 20 3 * * ?\t1\t1', 'errorLogCleanJob\t180\t0 25 3 * * ?\t1\t1',
        'jobLogCleanJob\t30\t0 30 3 * * ?\t1\t1'], 'log retention jobs')
    check(role_scopes('up14'), ROLE_SCOPES_V15, 'teacher and school_admin data scope set to all')
    check(school_admin_grants('up14'), SCHOOL_ADMIN_MENUS, 'school_admin grants 1001/5054/5117')
    check(other_role_grants('up14'), other_grants, 'no grants added to roles 160/161/200')
    after_first = snapshot('up14')
    sql(V15, 'up14')
    check(snapshot('up14'), after_first, 'repeated V15')
    check(school_admin_grants('up14'), SCHOOL_ADMIN_MENUS, 'repeated V15 inserts no duplicate grant')

    # 2. Baseline 15 is current and equals baseline 14 + V15 ----------------------------------------
    create('fresh15', new)
    before = snapshot('fresh15')
    sql(V15, 'fresh15')
    check(snapshot('fresh15'), before, 'V15 on baseline 15')
    assert_structure('fresh15')
    check(projections('fresh15'), projections('up14'), 'baseline 15 data vs baseline 14 + V15')
    check(schema('fresh15'), schema('up14'), 'baseline 15 schema/row counts vs baseline 14 + V15')

    # 3. Customized installations -------------------------------------------------------------------
    school_password = 'School-' + secrets.token_hex(6)
    create('edge', old)
    sql(f"""
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status)
VALUES (99001, '自定义资讯审核', 'edu:news:audit', 3, 9, 5112, '', '', '', NULL, 0),
       (99002, '自定义短信报表', '', 2, 9, 1094, 'sms-report', '', 'custom/sms/report', NULL, 0);
UPDATE system_menu SET component = 'custom/mail/index' WHERE id = 2130;
INSERT INTO system_role_menu (role_id, menu_id) VALUES (1, 1263), (1, 2920), (1, 99001);
INSERT INTO ai_api_key (name, api_key, platform, status, deleted) VALUES
  ('通义', 'placeholder', 'TongYi', 0, b'0'), ('旧 Suno', 'placeholder', 'Suno', 0, b'1');
INSERT INTO ai_model (key_id, name, model, platform, type, sort, status, deleted) VALUES
  (1, '豆包', 'doubao-pro', 'DouBao', 1, 1, 0, b'0');
INSERT INTO system_social_client (name, social_type, user_type, client_id, client_secret, status, deleted) VALUES
  ('公众号', 31, 2, 'client', 'placeholder', 0, b'0');
UPDATE infra_config SET value = '{school_password}' WHERE config_key = 'system.user.init-password';
UPDATE infra_config SET value = 'false' WHERE config_key = 'system.user.register-enabled';
UPDATE system_role SET data_scope = 2 WHERE id = 161;
UPDATE system_role SET code = 'custom_admin' WHERE id = 159;
INSERT INTO ai_tool (name, description, status, deleted) VALUES
  ('directory_list', '', 0, b'0'), ('weather_query', '', 0, b'0'), ('user_profile_query', '', 0, b'1'),
  ('school_tool', '', 0, b'0');
INSERT INTO infra_job (name, status, handler_name, handler_param, cron_expression, retry_count, retry_interval,
                       monitor_timeout, deleted)
VALUES ('学校访问日志清理', 2, 'accessLogCleanJob', '90', '0 0 4 * * ?', 0, 0, 0, b'0');
ALTER TABLE edu_homework ADD INDEX idx_edu_homework_class_status (class_id, status);
""", 'edge')
    sql(V15, 'edge')
    assert_structure('edge')
    menus = menu_state('edge', DELETED_MENUS + DISABLED_MENUS + [1094, 99001, 99002])
    for menu in (5110, 5112, 99001):  # customized child protects the candidate and its ancestor
        check(menus[menu], (1 if menu != 99001 else 0, 0), f'protected menu {menu}')
    for menu in (114, 116, 1255, 1256, 1257, 1258, 1259, 1260, 5103, 5105):
        check(menus[menu], (1, 1), f'menu {menu} retired')
    for menu in (1093, 1094, 99002):  # custom grandchild keeps the SMS directory visible
        check(menus[menu], (0, 0), f'protected disable candidate {menu}')
    check(menus[2130], (0, 0), 'customized mail menu')
    check(menus[1263], (1, 0), 'OAuth2 application menu disabled')
    check(menus[2920], (1, 0), 'AI tool menu disabled')
    check(column('SELECT menu_id FROM system_role_menu WHERE menu_id IN (%s) ORDER BY menu_id'
                 % ','.join(map(str, DELETED_MENUS + [1263, 2920, 99001])), 'edge'),
          ['1263', '2920', '5110', '5112', '99001'], 'grants of disabled and protected menus kept')
    check(dict_status('edge', 'ai_platform', ['TongYi', 'DouBao', 'Suno', 'Gemini']),
          {'TongYi': '0', 'DouBao': '0', 'Suno': '1', 'Gemini': '1'}, 'referenced ai_platform kept')
    check(dict_status('edge', 'system_social_type', ['30', '31', '32']), {'30': '1', '31': '0', '32': '1'},
          'referenced social type kept')
    check(value("SELECT CONCAT(value = '%s', '|', remark) FROM infra_config "
                "WHERE config_key = 'system.user.init-password'" % school_password, 'edge'),
          '1|初始化密码 123456', 'school initial password kept')
    check(value("SELECT CONCAT(category, '|', value) FROM infra_config "
                "WHERE config_key = 'system.user.register-enabled'", 'edge'), 'biz|false', 'register category fixed')
    check(sql('SELECT name, status FROM ai_tool ORDER BY id', 'edge').splitlines(),
          ['directory_list\t1', 'weather_query\t1', 'user_profile_query\t0', 'school_tool\t0'], 'demo AI tools')
    check(sql("SELECT handler_name, handler_param, status FROM infra_job WHERE handler_name LIKE '%LogCleanJob' "
              'ORDER BY handler_name', 'edge').splitlines(),
          ['accessLogCleanJob\t90\t2', 'errorLogCleanJob\t180\t1', 'jobLogCleanJob\t30\t1'], 'existing job kept')
    check(role_scopes('edge'), {**ROLE_SCOPES_V15, '159': 'custom_admin|4', '161': 'teacher|2'},
          'school-defined data scope and renamed role 159 kept')
    check(school_admin_grants('edge'), [], 'no grants when role 159 is not school_admin')
    edge_state = snapshot('edge')
    sql(V15, 'edge')
    check(snapshot('edge'), edge_state, 'repeated V15 on customized installation')
    sql('ALTER TABLE edu_class_student DROP INDEX idx_edu_class_student_student;', 'edge')
    sql(V15, 'edge')
    check(snapshot('edge'), edge_state, 'V15 restores only the missing index')

    # 4. Grant preconditions: menu permission must match, menu must be live, existing grants are kept once
    create('grants', old)
    sql("""
UPDATE system_menu SET permission = 'edu:class:custom' WHERE id = 5054;
UPDATE system_menu SET deleted = b'1' WHERE id = 5117;
INSERT INTO system_role_menu (role_id, menu_id) VALUES (159, 1001);
""", 'grants')
    sql(V15, 'grants')
    sql(V15, 'grants')
    check(school_admin_grants('grants'), ['1001'], 'grants only on matching live menus, existing grant not duplicated')
    check(role_scopes('grants'), ROLE_SCOPES_V15, 'data scopes updated independently of grants')

    # 5. Real Flyway path -----------------------------------------------------------------------------
    have_flyway = subprocess.run(['docker', 'image', 'inspect', FLYWAY_IMAGE], capture_output=True).returncode == 0
    if have_flyway:
        create('fly14', old)
        flyway('fly14', 'baseline', '-baselineVersion=14', '-baselineDescription=ry261-test.sql')
        flyway('fly14', 'migrate')
        check(sql("SELECT version, success FROM flyway_schema_history WHERE version IN ('14', '15') ORDER BY installed_rank",
                  'fly14').splitlines(), ['14\t1', '15\t1'], 'Flyway 14 -> 15')
        flyway('fly14', 'migrate')
        flyway('fly14', 'validate')
        check(value('SELECT COUNT(*) FROM flyway_schema_history', 'fly14'), '2', 'Flyway re-run applies nothing')
        check(projections('fly14'), projections('up14'), 'Flyway result vs mysql client result')
        create('fly15', new)
        flyway('fly15', 'baseline', '-baselineVersion=15', '-baselineDescription=ry261-test.sql')
        flyway('fly15', 'migrate')
        check(value('SELECT COUNT(*) FROM flyway_schema_history', 'fly15'), '1', 'baseline 15 has no pending migration')
        flyway_note = 'Flyway 14->15 and baseline 15'
    else:
        flyway_note = f'SKIPPED Flyway path ({FLYWAY_IMAGE} not present locally)'

    print('PASS: 14->15 effects (C1-C6), repeated V15, baseline 15 no-op and equivalence, customized menu/'
          'ancestor protection, NULL-component match, referenced dictionaries, school password, existing jobs, '
          'partial DDL, role data scopes and school_admin grants (exact match, no duplicates); ' + flyway_note)
finally:
    subprocess.run(['docker', 'rm', '-f', NAME], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
