#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对基线 19 -> V20。

运行：python3 deploy/database/tests/check-v20.py [--baseline19 /path/to/ry261-test.sql]
       [--baseline20 /path/to/ry261-test.sql]
基线优先取参数、NUS_BASELINE19_SQL / NUS_BASELINE20_SQL、NUS-doc 工作区。
基线 19 可从单仓库历史只读获取；基线 20 必须已同步，缺失时报错，避免漏验 fresh20。
复用 check-v18 的快照辅助函数；仅使用 mysql:8.4.6、--pull=never --network none --tmpfs。
场景：fresh19 导入并迁移、fresh20 逐表结构（含索引）及数据比较、up19 业务数据升级、rerun、
partial 部分索引已建且 Flyway 记录失败后恢复、Flyway validate。
跨基线比较忽略 create_time、update_time；同库升级及重跑用校验和与完整行核对，时间列也不能变。
本地 Flyway 镜像缺失时明确报告跳过，仍执行直接 SQL 的全部场景。
"""
import argparse
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('check-v18.py'))
v18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v18)
v18.NAME = 'nus-v20-check-' + uuid.uuid4().hex[:10]
# 镜像版本固定，避免不同 MySQL 版本的结构规范化行为掩盖差异。
v18.MYSQL_IMAGE = 'mysql:8.4.6'
ROOT = v18.ROOT
DOCS = (ROOT / 'NUS-doc',)
HISTOGRAMS = {(table, column) for table, columns in {
    'system_users': ('status', 'user_type', 'deleted'), 'system_user_role': ('deleted',),
    'system_user_profile': ('user_type', 'deleted'), 'system_dept': ('status', 'deleted'),
    'system_notify_message': ('read_status', 'user_type', 'deleted'), 'capacity_course': ('status', 'deleted'),
    'capacity_course_member': ('deleted',), 'edu_class': ('status', 'deleted'),
    'edu_class_student': ('status', 'deleted'), 'edu_class_course': ('status', 'deleted'),
    'edu_homework': ('status', 'deleted'), 'edu_homework_assignee': ('deleted',),
    'edu_homework_record': ('status', 'deleted'), 'edu_message': ('type', 'read_status', 'deleted'),
}.items() for column in columns}
ADDED = {
    ('system_users', 'idx_system_users_type_status'): ('user_type', 'status', 'deleted', 'id'),
    ('system_users', 'idx_system_users_dept_type'): ('dept_id', 'user_type', 'status', 'deleted'),
    ('system_users', 'idx_system_users_mobile'): ('mobile',),
    ('system_users', 'idx_system_users_email'): ('email',),
    ('system_user_role', 'idx_system_user_role_user'): ('user_id', 'role_id'),
    ('system_user_role', 'idx_system_user_role_role'): ('role_id',),
    ('system_user_profile', 'idx_system_user_profile_type_grade'): ('user_type', 'grade_year', 'user_id'),
    ('infra_config', 'idx_infra_config_key'): ('config_key',),
    ('edu_homework_record', 'idx_edu_homework_record_hw_status_score'): ('homework_id', 'status', 'score'),
    ('edu_homework_record', 'idx_edu_homework_record_status_hw'): ('status', 'homework_id'),
    ('edu_homework_record', 'idx_edu_homework_record_student_status'): ('student_id', 'status', 'correct_time'),
    ('edu_homework', 'idx_edu_homework_teacher_status'): ('teacher_id', 'status', 'id'),
    ('edu_message', 'idx_edu_message_type_read'): ('type', 'read_status', 'class_id', 'course_id'),
}
REMOVED = {
    ('edu_homework_record', 'idx_edu_homework_record_homework_status'): ('homework_id', 'status'),
    ('edu_homework_record', 'idx_student'): ('student_id',),
    ('edu_homework', 'idx_teacher'): ('teacher_id',),
    ('edu_message', 'idx_type'): ('type',),
}

# 空分数并列、打回重交、归档班课与任务、移出班级、逻辑删除、重复参数 key 都必须原样保留。
REPRESENTATIVE = """
INSERT INTO system_users (id, username, nickname, user_type, dept_id, status, mobile, email, deleted)
  VALUES (93001, 'v20-teacher', '索引验收教师', 1, 1, 0, '13900093001', 'v20-teacher@example.invalid', 0),
         (93002, 'v20-student', '索引验收学生', 2, 1, 0, '13900093002', 'v20-student@example.invalid', 0),
         (93003, 'v20-removed', '已移出学生', 2, 1, 1, '', '', 0),
         (93004, 'v20-deleted', '已删除学生', 2, 1, 0, '', '', 1);
INSERT INTO system_user_role (id, user_id, role_id, deleted)
  VALUES (93001, 93001, 161, 0), (93002, 93002, 162, 0), (93003, 93004, 162, 1);
INSERT INTO system_user_profile (id, user_id, user_type, grade_year, deleted)
  VALUES (93001, 93001, 1, NULL, 0), (93002, 93002, 2, 2026, 0), (93003, 93004, 2, 2025, 1);
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, deleted)
  VALUES (93001, 'edu', 2, '索引验收参数一', 'v20.duplicate-key', 'first', 1, 0),
         (93002, 'edu', 2, '索引验收参数二', 'v20.duplicate-key', 'second', 1, 0);
INSERT INTO edu_class (id, class_name, grade_year, dept_id, education_level, schooling_year)
  VALUES (93001, '索引验收班级', 2026, 1, 1, 4);
INSERT INTO capacity_course (id, indicator_id, course_name, teacher_id)
  VALUES (93001, 1, '索引验收课程', 93001);
INSERT INTO edu_class_course (id, class_id, course_id, semester_name, status)
  VALUES (93001, 93001, 93001, '2026-2027-1', 0), (93002, 93001, 93001, '2025-2026-2', 1);
INSERT INTO edu_class_student (id, class_id, student_id, join_time, deleted)
  VALUES (93001, 93001, 93002, '2026-10-01 09:00:00', 0),
         (93002, 93001, 93003, '2026-10-01 09:00:00', 1);
INSERT INTO edu_homework (id, homework_name, course_id, class_id, semester_name, teacher_id, status)
  VALUES (93001, '索引验收任务', 93001, 93001, '2026-2027-1', 93001, 1),
         (93002, '已归档任务', 93001, 93001, '2025-2026-2', 93001, 3);
INSERT INTO edu_homework_assignee (id, homework_id, student_id, class_id, assigned_time, deleted)
  VALUES (93001, 93001, 93002, 93001, '2026-10-01 09:00:00', 0),
         (93002, 93001, 93003, 93001, '2026-10-01 09:00:00', 1);
INSERT INTO edu_homework_record (id, homework_id, student_id, content, status, score,
                                submit_time, correct_time, redo_count, deleted)
  VALUES (93001, 93001, 93002, '已提交', 1, NULL, '2026-10-01 10:00:00', NULL, 0, 0),
         (93002, 93001, 93003, '移出班级后保留记录', 3, 70, '2026-10-01 10:00:00', NULL, 1, 0),
         (93003, 93001, 93004, '重交', 4, 70, '2026-10-02 10:00:00', NULL, 1, 0),
         (93004, 93002, 93002, '空分数并列一', 2, NULL, '2026-10-01 10:00:00', '2026-10-02 10:00:00', 0, 0),
         (93005, 93002, 93003, '空分数并列二', 2, NULL, '2026-10-01 10:00:00', '2026-10-02 10:00:00', 0, 0),
         (93006, 93002, 93004, '未提交', 0, NULL, NULL, NULL, 0, 0),
         (93007, 93001, 93002, '已删除历史记录', 2, 88, '2026-09-01 10:00:00', '2026-09-02 10:00:00', 0, 1);
INSERT INTO edu_message (id, class_id, course_id, student_id, content, type, read_status, deleted)
  VALUES (93001, 93001, 93001, 93002, '未读学生留言', 0, 0, 0),
         (93002, 93001, 93001, 93002, '已读教师回复', 1, 1, 0),
         (93003, 93001, 93001, 93003, '已删除留言', 0, 0, 1);
"""


def baseline(version, path, history=False, revision=None):
    return v18.read_baseline(version, path, docs=DOCS, history=history, revision=revision)


def migration():
    files = list(v18.MIGRATIONS.glob('V20__*.sql'))
    v18.equal(len(files), 1, '唯一的 V20 迁移')
    file = files[0]
    script = file.read_text(encoding='utf-8')
    executable = re.sub(r'--[^\n]*', '', script)
    # 直方图语句只改统计信息；先确认格式，再从关键字检查中去掉其中的 UPDATE。
    analyze = re.findall(r'^\s*ANALYZE\b[^;]*;', executable, re.I | re.M)
    v18.check(all(re.fullmatch(r'\s*ANALYZE TABLE \w+ UPDATE HISTOGRAM ON \w+(, \w+)*;', s) for s in analyze),
              'V20 的 ANALYZE 只用于建立直方图')
    executable = re.sub(r'\bUPDATE\s+HISTOGRAM\b', '', executable, flags=re.I)
    v18.check(not re.search(r'\b(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|CREATE|DROP\s+TABLE|'
                            r'ADD\s+COLUMN|DROP\s+COLUMN|MODIFY|CHANGE|RENAME)\b', executable, re.I),
              'V20 只能调整索引，不得修改数据、表或列')
    operations = re.findall(r'ALTER TABLE `(\w+)` (ADD|DROP) INDEX `(\w+)`(?: \(([^)]*)\))?', script)
    expected = [(table, 'ADD', name, ', '.join(f'`{c}`' for c in columns))
                for (table, name), columns in ADDED.items()]
    expected += [(table, 'DROP', name, '') for table, name in REMOVED]
    v18.equal(operations, expected, 'V20 先建立全部 13 个新索引，再删除 4 个旧索引')
    # 每条 DDL 都单独守卫，避免部分执行时由一个已存在索引跳过后续操作。
    for table, operation, name, _ in operations:
        pattern = (rf"SET @nus_v20_ddl = IF\(\s*EXISTS \(SELECT 1 FROM information_schema.statistics\s*"
                   rf"WHERE table_schema = DATABASE\(\) AND table_name = '{table}'\s*"
                   rf"AND index_name = '{name}'\),\s*")
        v18.check(bool(re.search(pattern, script)), f'{table}/{name}: 独立检查当前库索引')
    for command in ('PREPARE nus_v20_stmt FROM @nus_v20_ddl;', 'EXECUTE nus_v20_stmt;',
                    'DEALLOCATE PREPARE nus_v20_stmt;'):
        v18.equal(script.count(command), 17, f'每条索引 DDL 都执行 {command}')
    return file, script


def index_lines(indexes):
    return ['\t'.join((table, name, str(seq), column, '<NULL>', '1'))
            for (table, name), columns in indexes.items() for seq, column in enumerate(columns, 1)]


def assert_final(database):
    indexes = v18.snapshot(database)['indexes'].splitlines()
    for line in index_lines(ADDED):
        v18.check(line in indexes, f'{database}: 普通索引列顺序正确：{line}')
    for table, name in REMOVED:
        v18.check(not any(line.startswith(f'{table}\t{name}\t') for line in indexes),
                  f'{database}: 旧索引 {table}/{name} 已删除')
    # 还要排除前缀索引、隐藏索引等“列名相同但行为不同”的定义。
    definitions = v18.sql("SELECT table_name, index_name, seq_in_index, IFNULL(sub_part, '<NULL>'), "
                          "index_type, is_visible FROM information_schema.statistics "
                          "WHERE table_schema = DATABASE() ORDER BY table_name, index_name, seq_in_index", database)
    selected = [line for line in definitions.splitlines()
                if tuple(line.split('\t')[:2]) in ADDED]
    expected = ['\t'.join((table, name, str(seq), '<NULL>', 'BTREE', 'YES'))
                for (table, name), columns in ADDED.items() for seq in range(1, len(columns) + 1)]
    v18.equal(sorted(selected), sorted(expected), f'{database}: 全长、可见的普通 BTREE 索引')
    # 状态类列的直方图：迁移与基线 20 都要建立，否则优化器按 5% 估算命中率、连接顺序退化。
    histograms = v18.sql("SELECT table_name, column_name FROM information_schema.column_statistics "
                         "WHERE schema_name = DATABASE() ORDER BY table_name, column_name", database)
    actual = {tuple(line.split('\t')) for line in histograms.splitlines() if line}
    v18.equal(sorted(HISTOGRAMS - actual), [], f'{database}: 状态类列直方图齐全')


def expected_indexes(before):
    retained = [line for line in before.splitlines() if tuple(line.split('\t')[:2]) not in REMOVED
                and tuple(line.split('\t')[:2]) not in ADDED]
    return sorted(retained + index_lines(ADDED))


def assert_upgrade(database, script):
    before = v18.snapshot(database)
    rows = v18.full_rows(database, v18.tables(database))
    v18.sql(script, database)
    after = v18.snapshot(database)
    v18.equal(after['schema'], before['schema'], f'{database}: 所有表、列定义不变')
    v18.equal(after['checksums'], before['checksums'], f'{database}: 所有表数据及时间列不变')
    v18.equal(v18.full_rows(database, v18.tables(database)), rows, f'{database}: 逐行数据不变')
    v18.equal(sorted(after['indexes'].splitlines()), expected_indexes(before['indexes']),
              f'{database}: 只新增指定索引、删除指定旧索引，唯一键及其余索引不变')
    assert_final(database)
    v18.sql(script, database)
    v18.equal(v18.snapshot(database), after, f'{database}: rerun 全部校验和、列与索引不变')


def flyway(database, *args):
    return v18.run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{v18.NAME}',
                   '-v', f'{v18.MIGRATIONS}:/flyway/sql:ro', v18.FLYWAY_IMAGE,
                   f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
                   '-user=root', '-password=', '-locations=filesystem:/flyway/sql', '-target=20', *args)


def assert_flyway(database, direct):
    v18.equal(v18.sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank',
                      database).splitlines(), ['19\t1', '20\t1'], f'{database}: Flyway 19 -> 20')
    assert_final(database)
    v18.equal(v18.structure(database), v18.structure(direct), f'{database}: 与直接执行逐表结构一致')
    v18.equal(v18.contents(database), v18.contents(direct), f'{database}: 与直接执行逐表数据一致')
    state = v18.snapshot(database)
    flyway(database, 'migrate')
    flyway(database, 'validate')
    v18.equal(v18.snapshot(database), state, f'{database}: 再次 migrate、validate 无改动')


def business_checksums(database):
    # Quartz 等无 id 的表不能用 full_rows 比较，也需要覆盖全部数据和时间列。
    return {table: checksum for table, checksum in v18.snapshot(database)['checksums'].items()
            if table != f'{database}.flyway_schema_history'}


def partial_indexes(database):
    for (table, name), columns in list(ADDED.items())[:5]:
        v18.sql(f'ALTER TABLE `{table}` ADD INDEX `{name}` (' +
                ', '.join(f'`{column}`' for column in columns) + ');', database)
    indexes = v18.snapshot(database)['indexes'].splitlines()
    for line in index_lines(REMOVED):
        v18.check(line in indexes, f'{database}: 部分执行时旧索引仍保留')
    v18.equal(sum(line in indexes for line in index_lines(ADDED)),
              sum(len(columns) for columns in list(ADDED.values())[:5]),
              f'{database}: 仅建成前 5 个新索引')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline19', help='基线 19；默认可读取单仓库历史')
    parser.add_argument('--baseline20', help='基线 20；默认读取 NUS-doc 工作区')
    args = parser.parse_args()
    base19, source19 = baseline(19, args.baseline19, history=True)
    base20, source20 = baseline(20, args.baseline20)
    file, script = migration()
    digest = hashlib.sha256(file.read_bytes()).hexdigest()

    def unchanged():
        v18.check(hashlib.sha256(file.read_bytes()).hexdigest() == digest, '检查期间 V20 被改写，请重跑')

    notes = []
    try:
        v18.run('docker', 'run', '-d', '--rm', '--pull=never', '--name', v18.NAME, '--network', 'none',
                '--tmpfs', '/var/lib/mysql', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', v18.MYSQL_IMAGE)
        for _ in range(120):
            logs = subprocess.run(['docker', 'logs', v18.NAME], text=True, capture_output=True)
            if 'MySQL init process done. Ready for start up.' in logs.stdout + logs.stderr:
                try:
                    v18.run('docker', 'exec', v18.NAME, 'mysqladmin', 'ping', '-uroot')
                    break
                except RuntimeError:
                    pass
            time.sleep(1)
        else:
            raise RuntimeError('一次性 MySQL 容器未就绪')

        v18.create('fresh19', base19)
        assert_upgrade('fresh19', script)
        notes.append(f'fresh19 全新基线 19 + V20、rerun 通过（{source19}）')

        v18.create('fresh20', base20)
        assert_final('fresh20')
        v18.equal(v18.structure('fresh20'), v18.structure('fresh19'), 'fresh20: 全部表结构（含索引）一致')
        v18.equal(v18.contents('fresh20'), v18.contents('fresh19'), 'fresh20: 逐表逐行数据一致')
        state = v18.snapshot('fresh20')
        v18.sql(script, 'fresh20')
        v18.equal(v18.snapshot('fresh20'), state, 'fresh20: 执行 V20 无改动')
        notes.append(f'fresh20 通过（{source20}）')

        v18.create('up19', base19)
        v18.sql(REPRESENTATIVE, 'up19')
        assert_upgrade('up19', script)
        notes.append('up19 代表性业务数据升级、rerun 通过（含重复配置 key）')

        v18.create('partial', base19)
        v18.sql(REPRESENTATIVE, 'partial')
        partial_indexes('partial')
        assert_upgrade('partial', script)
        v18.equal(v18.structure('partial'), v18.structure('up19'), 'partial: 恢复后结构一致')
        v18.equal(v18.contents('partial'), v18.contents('up19'), 'partial: 恢复后数据一致')
        notes.append('partial 直接 SQL 恢复通过')

        if subprocess.run(['docker', 'image', 'inspect', v18.FLYWAY_IMAGE], capture_output=True).returncode == 0:
            for database, fixture, direct in (('fly_fresh19', '', 'fresh19'),
                                               ('fly_up19', REPRESENTATIVE, 'up19')):
                unchanged()
                v18.create(database, base19)
                v18.sql(fixture, database)
                flyway(database, 'baseline', '-baselineVersion=19', '-baselineDescription=ry261-test.sql')
                before = v18.contents(database)
                checksums = business_checksums(database)
                business_rows = v18.full_rows(database, [t for t in v18.tables(database)
                                                        if t != 'flyway_schema_history'])
                flyway(database, 'migrate')
                v18.equal(v18.contents(database), before, f'{database}: Flyway 不改业务数据')
                v18.equal(business_checksums(database), checksums, f'{database}: 全表校验和及时间列不变')
                v18.equal(v18.full_rows(database, business_rows), business_rows,
                          f'{database}: Flyway 保留所有业务行及时间列')
                assert_flyway(database, direct)
            notes.append('fresh19 / up19 Flyway migrate、rerun、validate 通过')

            unchanged()
            v18.create('fly_partial', base19)
            v18.sql(REPRESENTATIVE, 'fly_partial')
            flyway('fly_partial', 'baseline', '-baselineVersion=19', '-baselineDescription=ry261-test.sql')
            checksums = business_checksums('fly_partial')
            before = v18.full_rows('fly_partial', [t for t in v18.tables('fly_partial')
                                                 if t != 'flyway_schema_history'])
            partial_indexes('fly_partial')
            # 人工构造 MySQL 非事务 DDL 中断的历史状态，不额外放入必然失败的迁移污染目录。
            v18.sql("INSERT INTO flyway_schema_history "
                    "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) "
                    f"VALUES (2, '20', 'v1 0 1 9 query indexes', 'SQL', '{file.name}', NULL, 'root', 1, 0);",
                    'fly_partial')
            v18.equal(v18.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0",
                                  'fly_partial'), '1', 'partial: V20 失败记录存在')
            try:
                flyway('fly_partial', 'validate')
            except RuntimeError as error:
                v18.check('detected failed migration to version 20' in str(error).lower(),
                          'partial: validate 必须因 V20 失败记录而拒绝')
            else:
                raise AssertionError('partial: 未 repair 的失败记录必须阻止 validate')
            flyway('fly_partial', 'repair')
            v18.equal(v18.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0",
                                  'fly_partial'), '0', 'partial: repair 已移除失败记录')
            unchanged()
            flyway('fly_partial', 'migrate')
            v18.equal(business_checksums('fly_partial'), checksums, 'partial: 全表校验和及时间列不变')
            v18.equal(v18.full_rows('fly_partial', before), before, 'partial: 修复及重跑保留业务行和时间列')
            assert_flyway('fly_partial', 'up19')
            notes.append('partial 失败记录、validate 拒绝、repair、重跑及 validate 通过')
        else:
            notes.append(f'Flyway 跳过：本地缺少 {v18.FLYWAY_IMAGE}；fresh19 / up19 Flyway 迁移、'
                         'partial 失败记录 repair 与 validate 需负责人补验')
        unchanged()
        print(f'PASS {file.name}: ' + '；'.join(notes))
    finally:
        subprocess.run(['docker', 'rm', '-f', v18.NAME], capture_output=True, text=True)


if __name__ == '__main__':
    try:
        main()
    except AssertionError as error:
        print(f'FAIL {error}', file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, RuntimeError) as error:
        print(f'ERROR {error}', file=sys.stderr)
        sys.exit(2)
