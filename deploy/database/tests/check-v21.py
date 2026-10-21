#!/usr/bin/env python3
"""一次性 MySQL 8.4.6 验收基线 20 -> V21，不连接现有数据库。

运行：python3 deploy/database/tests/check-v21.py [--baseline20 /path/to/ry261-test.sql]
       [--baseline21 /path/to/ry261-test.sql]
基线优先取参数、NUS_BASELINE20_SQL / NUS_BASELINE21_SQL、NUS-doc 工作区。
基线 20 可只读查历史；基线 21 必须存在。覆盖 fresh20、fresh21、up20、rerun、
部分列完成、已有认领保留、Flyway 失败记录 repair、migrate/validate。
仅用本地镜像、--pull=never、--network none、tmpfs；Flyway 共享该隔离容器的网络。
Flyway 镜像缺失时直接报错，避免把不完整验收报告为通过。
"""
import argparse
import copy
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
spec = importlib.util.spec_from_file_location('v20_helpers', Path(__file__).with_name('check-v20.py'))
v20 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v20)
v18 = v20.v18
v18.NAME = 'nus-v21-check-' + uuid.uuid4().hex[:10]
v18.MYSQL_IMAGE = 'mysql:8.4.6'
ROOT = v18.ROOT
DOCS = (ROOT / 'NUS-doc',)
CLAIM_COLUMNS = ('claim_teacher_id', 'claim_time')
INDEXES = {
    ('edu_class_course', 'idx_edu_class_course_claim'): ('course_id', 'claim_teacher_id', 'status'),
    ('edu_class_course', 'idx_edu_class_course_claim_teacher'): ('claim_teacher_id', 'status'),
}

# 场景与预期独立列出，避免只使用与迁移相同的 SQL 再算一遍结果。
# (班课序号, 课程序号, 学期, 状态, 删除标记, 班级序号)
RELATIONS = [(i, 1, '2026-2027-1', 0, 0, i) for i in range(1, 21)]
RELATIONS[4] = (5, 1, ' 2026-2027-1 ', 0, 0, 5)
RELATIONS[5] = (6, 2, '2026-2027-1', 0, 0, 6)
RELATIONS[8] = (9, 1, '2026-2027-1', 1, 0, 9)
RELATIONS[9] = (10, 1, '2026-2027-1', 0, 1, 10)
RELATIONS[10] = (11, 3, '2026-2027-1', 0, 0, 11)
RELATIONS[14] = (15, 1, '2025-2026-2', 0, 0, 1)
RELATIONS[15] = (16, 1, '', 0, 0, 16)
RELATIONS[16] = (17, 1, '   ', 0, 0, 17)
RELATIONS[19] = (20, 4, '2026-2027-1', 0, 0, 20)
# (班课序号, 教师, 任务状态, 来源任务, 学期, 删除标记, 创建时间)
HOMEWORKS = [
    (1, 11, 1, None, '2026-2027-1', 0, '2026-10-02 09:00:00'),
    (1, 11, 2, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (1, 12, 1, 95001, '2026-2027-1', 0, '2026-09-01 09:00:00'),
    (1, 12, 0, None, '2026-2027-1', 0, '2026-09-01 09:00:00'),
    (2, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (2, 12, 2, None, '2026-2027-1', 0, '2026-10-02 09:00:00'),
    (3, 11, 1, 95001, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (4, 11, 0, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (5, 11, 1, None, ' 2026-2027-1 ', 0, '2026-10-01 09:00:00'),
    (6, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (7, 11, 1, None, '2025-2026-2', 0, '2026-10-01 09:00:00'),
    (8, 11, 1, None, '   ', 0, '2026-10-01 09:00:00'),
    (9, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (10, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (11, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (12, 11, 3, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (13, 11, 1, None, '2026-2027-1', 1, '2026-10-01 09:00:00'),
    (15, 13, 1, None, '2025-2026-2', 0, '2026-10-03 09:00:00'),
    (16, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (17, 11, 1, None, '', 0, '2026-10-01 09:00:00'),
    (18, 11, 1, None, '', 0, '2026-09-30 09:00:00'),
    (18, 11, 2, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (19, 11, 1, None, '', 0, '2026-10-01 09:00:00'),
    (19, 12, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
    (20, 11, 1, None, '2026-2027-1', 0, '2026-10-01 09:00:00'),
]
EXPECTED = {94000 + i: ('11', '2026-10-01 09:00:00.000000') for i in (1, 5, 8, 17, 20)}
EXPECTED[94015] = ('13', '2026-10-03 09:00:00.000000')
EXPECTED[94018] = ('11', '2026-09-30 09:00:00.000000')


# V21 改动行的表；其余表全表校验和必须不变。
UPDATED_TABLES = {'edu_class_course', 'system_dict_data'}
DICT_LABELS = {('3', '已归档'): '已封存', ('0', '草稿'): '待发布'}


def fixture():
    script = """
INSERT INTO capacity_course (id, indicator_id, course_name, is_public_course, status, deleted)
VALUES (94001, 1, '认领验收公共课', 0, 0, 0), (94002, 1, '认领验收专属课', 1, 0, 0),
       (94003, 1, '已删除公共课', 0, 0, 1), (94004, 1, '停用公共课类型仍回填', 0, 1, 0);
"""
    script += ('INSERT INTO edu_class_course (id, class_id, course_id, semester_name, status, deleted, '
               'creator, updater, create_time, update_time, backfill_pending) VALUES\n')
    script += ',\n'.join(f"({94000+i}, {94000+class_id}, {94000+course}, '{semester}', {status}, {deleted}, "
                         "'system', 'system-sync', '2026-08-01 08:00:00', '2026-08-02 08:00:00', 1)"
                         for i, course, semester, status, deleted, class_id in RELATIONS) + ';\n'
    script += ('INSERT INTO edu_homework (id, homework_name, course_id, class_id, teacher_id, status, '
               'source_homework_id, semester_name, deleted, create_time, update_time) VALUES\n')
    relations = {r[0]: r for r in RELATIONS}
    script += ',\n'.join(
        f"({95001+n}, '认领验收任务 {n}', {94000+relations[i][1]}, {94000+relations[i][5]}, "
        f"{teacher}, {status}, {'NULL' if source is None else source}, '{semester}', {deleted}, "
        f"'{created}', '2026-10-04 08:00:00')"
        for n, (i, teacher, status, source, semester, deleted, created) in enumerate(HOMEWORKS)) + ';\n'
    return script


REPRESENTATIVE = fixture()


def baseline(version, path=None):
    # 复用已验证的参数、环境变量和只读历史查找；NUS-doc 普通目录优先。
    v20.DOCS = DOCS
    return v20.baseline(version, path, history=version == 20)


def migration():
    files = list(v18.MIGRATIONS.glob('V21__*.sql'))
    v18.equal(len(files), 1, '唯一的 V21 迁移')
    return files[0], files[0].read_text(encoding='utf-8')


def capture(database):
    names = [t for t in v18.tables(database) if t != 'flyway_schema_history']
    state = v18.snapshot(database)
    state['rows'] = v18.full_rows(database, names)
    state['schema'] = '\n'.join(line for line in state['schema'].splitlines()
                               if not line.startswith('flyway_schema_history\t'))
    state['indexes'] = '\n'.join(line for line in state['indexes'].splitlines()
                                if not line.startswith('flyway_schema_history\t'))
    state['checksums'] = {k.split('.', 1)[1]: value for k, value in state['checksums'].items()
                          if not k.endswith('.flyway_schema_history')}
    return state


def expected_rows(before):
    result = copy.deepcopy(before['rows'])
    courses, homeworks = result['capacity_course'], result['edu_homework']
    for cc in result['edu_class_course'].values():
        cc.setdefault('claim_teacher_id', '<NULL>')
        cc.setdefault('claim_time', '<NULL>')
        course = courses.get(int(cc['course_id']))
        if (cc['claim_teacher_id'] != '<NULL>' or cc['deleted'] != '0' or cc['status'] != '0'
                or not course or course['deleted'] != '0' or course['is_public_course'] != '0'):
            continue
        matches = [h for h in homeworks.values() if h['course_id'] == cc['course_id']
                   and h['class_id'] == cc['class_id'] and h['deleted'] == '0'
                   and h['source_homework_id'] == '<NULL>' and h['status'] in ('1', '2')
                   and (h['semester_name'].strip(' ') == ''
                        or h['semester_name'].strip(' ') == cc['semester_name'].strip(' '))]
        if len({h['teacher_id'] for h in matches}) == 1:
            cc['claim_teacher_id'] = matches[0]['teacher_id']
            cc['claim_time'] = min(h['create_time'] for h in matches)
    # 任务状态字典：只把出厂文字改为“已封存”“待发布”，其余字段不变。
    for d in (result.get('system_dict_data') or {}).values():
        if d['dict_type'] == 'edu_homework_status' and d['deleted'] == '0':
            d['label'] = DICT_LABELS.get((d['value'], d['label']), d['label'])
    return result


def assert_final(database):
    columns = v18.sql("SELECT column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), "
                      "extra, column_comment FROM information_schema.columns WHERE table_schema = DATABASE() "
                      "AND table_name = 'edu_class_course' AND column_name IN ('claim_teacher_id', 'claim_time') "
                      "ORDER BY ordinal_position", database).splitlines()
    v18.equal(columns, ['claim_teacher_id\tbigint\tYES\t<NULL>\t\t公共课认领教师（用户 ID）',
                        'claim_time\tdatetime\tYES\t<NULL>\t\t认领时间'], f'{database}: 认领列定义')
    order = v18.sql("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                    "AND table_name = 'edu_class_course' ORDER BY ordinal_position", database).split()
    at = order.index('backfill_pending')
    v18.equal(order[at+1:at+3], list(CLAIM_COLUMNS), f'{database}: 列位置一致')
    indexes = v18.sql("SELECT table_name, index_name, seq_in_index, column_name, non_unique, "
                      "IFNULL(sub_part, '<NULL>'), index_type, is_visible FROM information_schema.statistics "
                      "WHERE table_schema = DATABASE() AND table_name = 'edu_class_course' "
                      "AND index_name IN ('idx_edu_class_course_claim', 'idx_edu_class_course_claim_teacher') "
                      "ORDER BY index_name, seq_in_index", database).splitlines()
    expected = ['\t'.join((table, name, str(seq), col, '1', '<NULL>', 'BTREE', 'YES'))
                for (table, name), cols in INDEXES.items() for seq, col in enumerate(cols, 1)]
    v18.equal(indexes, sorted(expected), f'{database}: 普通可见全长索引')
    labels = v18.sql("SELECT value, label FROM system_dict_data WHERE dict_type = 'edu_homework_status' "
                     "AND deleted = 0 AND value IN ('0', '3') ORDER BY value", database).splitlines()
    v18.equal(labels, ['0\t待发布', '3\t已封存'], f'{database}: 任务状态字典改为待发布、已封存')
    # 沿用基线 20 的索引和直方图，不丢失已有优化器统计信息。
    v20.assert_final(database)


def assert_preserved(database, before):
    after = capture(database)
    strip_columns = lambda s: sorted(line for line in s.splitlines()
                                    if tuple(line.split('\t')[:2]) not in
                                    {('edu_class_course', c) for c in CLAIM_COLUMNS})
    v18.equal(strip_columns(after['schema']), strip_columns(before['schema']),
              f'{database}: 原有列定义与其余表不变')
    retained = [line for line in before['indexes'].splitlines()
                if tuple(line.split('\t')[:2]) not in INDEXES]
    v18.equal(sorted(after['indexes'].splitlines()), sorted(retained + v20.index_lines(INDEXES)),
              f'{database}: 仅加两个索引，唯一键及原索引不变')
    v18.equal(after['rows'], expected_rows(before),
              f'{database}: 仅按规则回填认领，所有原字段（含 updater、update_time）不变')
    untouched = lambda state: {k: v for k, v in state['checksums'].items() if k not in UPDATED_TABLES}
    v18.equal(untouched(after), untouched(before), f'{database}: 其余表全表校验和不变（含无 id 表）')
    assert_final(database)
    return after


def assert_upgrade(database, script):
    before = capture(database)
    v18.sql(script, database)
    after = assert_preserved(database, before)
    v18.sql(script, database)
    v18.equal(capture(database), after, f'{database}: rerun 含时间列无改动')


def assert_fixture(database, preserved=False):
    actual = v18.rows(database, 'edu_class_course')
    for i, *_ in RELATIONS:
        expected = EXPECTED.get(94000+i, ('<NULL>', '<NULL>'))
        if preserved and i == 1:
            expected = ('99', '2026-08-03 08:00:00.000000')
        v18.equal(tuple(actual[94000+i][c] for c in CLAIM_COLUMNS), expected,
                  f'{database}: 班课 {i} 的独立场景断言')


def partial_columns(database, script, preserved=False):
    # 模拟列 DDL 已完成、索引尚未创建；已有认领也必须原样保留。
    column_end = list(re.finditer(r'DEALLOCATE PREPARE nus_v21_stmt;', script))[1].end()
    v18.sql(script[:column_end], database)
    v18.equal(v18.scalar("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
                         "AND table_name='edu_class_course' AND index_name LIKE 'idx_edu_class_course_claim%'",
                         database), '0', f'{database}: 列已加、索引未加')
    if preserved:
        v18.sql("UPDATE edu_class_course SET claim_teacher_id=99, claim_time='2026-08-03 08:00:00', "
                "update_time=update_time WHERE id=94001;", database)


def flyway(database, *args):
    return v18.run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{v18.NAME}',
                   '-v', f'{v18.MIGRATIONS}:/flyway/sql:ro', v18.FLYWAY_IMAGE,
                   f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
                   '-user=root', '-password=', '-locations=filesystem:/flyway/sql', '-target=21', *args)


def assert_flyway(database, direct):
    v18.equal(v18.sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank',
                      database).splitlines(), ['20\t1', '21\t1'], f'{database}: Flyway 20 -> 21')
    assert_final(database)
    v18.equal(v18.structure(database), v18.structure(direct), f'{database}: 直接执行与 Flyway 结构一致')
    v18.equal(v18.contents(database), v18.contents(direct), f'{database}: 直接执行与 Flyway 数据一致')
    state = capture(database)
    history = v18.sql('SELECT * FROM flyway_schema_history ORDER BY installed_rank', database)
    flyway(database, 'migrate')
    flyway(database, 'validate')
    v18.equal(capture(database), state, f'{database}: 再次 migrate、validate 不改业务数据')
    v18.equal(v18.sql('SELECT * FROM flyway_schema_history ORDER BY installed_rank', database),
              history, f'{database}: 无重复迁移记录')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline20', help='基线 20，默认只读查工作区及历史')
    parser.add_argument('--baseline21', help='基线 21，必须已同步')
    args = parser.parse_args()
    base20, source20 = baseline(20, args.baseline20)
    base21, source21 = baseline(21, args.baseline21)
    file, script = migration()
    digest = hashlib.sha256(file.read_bytes()).hexdigest()

    def unchanged():
        v18.equal(hashlib.sha256(file.read_bytes()).hexdigest(), digest, '验收期间 V21 不得变化')

    # 先检查所有镜像，缺镜像不能静默漏验 Flyway。
    for mirror in (v18.MYSQL_IMAGE, v18.FLYWAY_IMAGE):
        v18.run('docker', 'image', 'inspect', mirror)
    notes = []
    started = False
    try:
        v18.run('docker', 'run', '-d', '--rm', '--pull=never', '--name', v18.NAME, '--network', 'none',
                '--tmpfs', '/var/lib/mysql', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', v18.MYSQL_IMAGE)
        started = True
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

        v18.create('fresh20', base20)
        assert_upgrade('fresh20', script)
        notes.append(f'fresh20 + V21、rerun（{source20}）')
        v18.create('fresh21', base21)
        assert_final('fresh21')
        v18.equal(v18.structure('fresh21'), v18.structure('fresh20'), 'fresh21: 全部表结构一致')
        v18.equal(v18.contents('fresh21'), v18.contents('fresh20'), 'fresh21: 全部数据一致')
        before = capture('fresh21')
        v18.sql(script, 'fresh21')
        v18.equal(capture('fresh21'), before, 'fresh21: V21 无改动')
        flyway('fresh21', 'baseline', '-baselineVersion=21', '-baselineDescription=ry261-test.sql')
        flyway('fresh21', 'migrate')
        flyway('fresh21', 'validate')
        v18.equal(capture('fresh21'), before, 'fresh21: baseline 21 后 migrate/validate 无改动')
        notes.append(f'fresh21 结构、数据、Flyway（{source21}）')

        v18.create('up20', base20)
        v18.sql(REPRESENTATIVE, 'up20')
        assert_upgrade('up20', script)
        assert_fixture('up20')
        notes.append('up20 20 个代表性班课、全部原字段保留、rerun')

        for database, preserved in (('partial', False), ('claimed', True)):
            v18.create(database, base20)
            v18.sql(REPRESENTATIVE, database)
            partial_columns(database, script, preserved)
            assert_upgrade(database, script)
            assert_fixture(database, preserved)
            v18.equal(v18.structure(database), v18.structure('up20'), f'{database}: 恢复后结构一致')
            if not preserved:
                v18.equal(v18.contents(database), v18.contents('up20'), f'{database}: 恢复后数据一致')
        notes.append('partial 列完成索引未建、claimed 已有认领保留')

        for database, data, direct in (('fly_fresh20', '', 'fresh20'), ('fly_up20', REPRESENTATIVE, 'up20')):
            unchanged()
            v18.create(database, base20)
            v18.sql(data, database)
            flyway(database, 'baseline', '-baselineVersion=20', '-baselineDescription=ry261-test.sql')
            before = capture(database)
            flyway(database, 'migrate')
            assert_preserved(database, before)
            assert_flyway(database, direct)
        notes.append('fresh20 / up20 Flyway migrate、rerun、validate')

        v18.create('fly_partial', base20)
        v18.sql(REPRESENTATIVE, 'fly_partial')
        flyway('fly_partial', 'baseline', '-baselineVersion=20', '-baselineDescription=ry261-test.sql')
        partial_columns('fly_partial', script)
        before = capture('fly_partial')
        v18.sql("INSERT INTO flyway_schema_history "
                "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) "
                f"VALUES (2, '21', 'v1 0 1 10 public course claim', 'SQL', '{file.name}', NULL, 'root', 1, 0);",
                'fly_partial')
        v18.equal(v18.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='21' AND success=0",
                              'fly_partial'), '1', 'partial: V21 失败记录存在')
        try:
            flyway('fly_partial', 'validate')
        except RuntimeError as error:
            v18.check('detected failed migration to version 21' in str(error).lower(),
                      'partial: validate 因 V21 失败记录拒绝')
        else:
            raise AssertionError('partial: 未 repair 必须拒绝 validate')
        flyway('fly_partial', 'repair')
        v18.equal(v18.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='21' AND success=0",
                              'fly_partial'), '0', 'partial: repair 清除失败记录')
        unchanged()
        flyway('fly_partial', 'migrate')
        assert_preserved('fly_partial', before)
        assert_fixture('fly_partial')
        assert_flyway('fly_partial', 'up20')
        notes.append('partial 失败记录、validate 拒绝、repair、重跑')
        unchanged()
        print(f'PASS {file.name}: ' + '；'.join(notes))
    finally:
        if started:
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
