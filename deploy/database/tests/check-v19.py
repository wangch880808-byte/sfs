#!/usr/bin/env python3
"""在一次性无网络 MySQL 中核对基线 18 -> V19。

运行：python3 deploy/database/tests/check-v19.py [--baseline18 /path/to/ry261-test.sql]
       [--baseline19 /path/to/ry261-test.sql]
基线 18 优先取参数、NUS_BASELINE18_SQL、NUS-doc 工作区，再取单仓库历史基线 18；缺少旧历史时须显式提供文件。
基线 19 取 --baseline19、NUS_BASELINE19_SQL 或上述目录已标记为 19 的文件，都没有时明确报告跳过。
复用 check-v18 的数据库快照辅助函数；不修改基线，不连接现有数据库。
场景：fresh18 全新导入 + Flyway、up18 代表性业务数据升级、rerun 全表无副作用、guards 身份守卫。
fresh19：基线 19 与「基线 18 + V19」逐表比较结构和数据（忽略 create_time、update_time），
并确认 V19 在基线 19 上无改动。
本地 Flyway 镜像缺失时明确报告跳过；MySQL 仅使用 --pull=never --network none --tmpfs。
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
v18.NAME = 'nus-v19-check-' + uuid.uuid4().hex[:10]
ROOT = v18.ROOT
DOCS = (ROOT / 'NUS-doc',)
CONTENT = '「{homeworkName}」有 {count} 份新提交{resubmitNote}待批改。'
PARAMS = '["homeworkName","count","resubmitNote"]'


def baseline18(path):
    return v18.read_baseline(18, path, docs=DOCS)[0]


def baseline19(path):
    """参照 fresh18：参数、环境变量或已同步的基线；不从历史提交取基线 19。"""
    marker = '\n-- Flyway baseline version: 19\n'
    candidate = path or os.environ.get('NUS_BASELINE19_SQL')
    if candidate:
        file = Path(candidate)
        text = file.read_text(encoding='utf-8')
        v18.check(marker in text, f'{file} 未标记为基线 19')
        return text, str(file)
    for doc in DOCS:
        file = doc / v18.BASELINE_REL
        if file.is_file():
            text = file.read_text(encoding='utf-8')
            if marker in text:
                return text, str(file)
    return None, '未提供基线 19（--baseline19 或 NUS_BASELINE19_SQL），NUS-doc 工作区基线也不是 19'


def assert_final(database):
    v18.equal(v18.scalar("SELECT COUNT(*) FROM system_role_menu g JOIN system_role r ON r.id=g.role_id "
                         "JOIN system_menu m ON m.id=g.menu_id WHERE r.id=161 AND r.code='teacher' "
                         "AND r.deleted=0 AND m.id=5064 AND m.permission='edu:class-course:bind' "
                         "AND m.deleted=0 AND g.deleted=0", database), '1', f'{database}: 教师绑定授权恰一行')
    v18.equal(v18.sql("SELECT content, params FROM system_notify_template "
                      "WHERE code='edu.teacher.homework.submitted' AND deleted=0", database).splitlines(),
              [CONTENT + '\t' + PARAMS], f'{database}: F8 通知内容和参数')
    v18.equal(v18.scalar("SELECT label FROM system_dict_data WHERE id=3036 "
                         "AND dict_type='system_user_type' AND value='0' AND deleted=0", database),
              '管理员', f'{database}: 管理员字典标签')
    v18.equal(v18.scalar('SELECT status, CAST(deleted AS UNSIGNED) FROM system_menu WHERE id=107', database),
              '1\t0', f'{database}: 通知公告停用且保留')
    v18.equal(v18.scalar('SELECT CAST(deleted AS UNSIGNED) FROM system_menu WHERE id=5065', database),
              '1', f'{database}: 解绑菜单软删除')
    v18.equal(v18.scalar('SELECT COUNT(*) FROM system_role_menu WHERE role_id=1 AND menu_id=5065 AND deleted=0',
                         database), '0', f'{database}: 超管解绑关联软删除')
    v18.equal(v18.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                         "AND table_name='edu_class_course' AND column_name='assistant_teacher_ids'", database),
              '1', f'{database}: 回滚所需助教 CSV 列保留')
    v18.equal(v18.scalar("SELECT data_scope FROM system_role WHERE id=160 AND code='college_admin'", database),
              '4', f'{database}: 学院教务管理员数据范围为本部门及以下')
    v18.equal(v18.scalar("SELECT COUNT(DISTINCT menu_id) FROM system_role_menu WHERE role_id=160 AND deleted=0 "
                         "AND menu_id IN (5047,103,1017,5049,5051,5052,5053,5054,5048,5050,"
                         "1001,1002,1003,1004,1005,1006,1007,5126)", database),
              '18', f'{database}: 学院教务管理员 18 个菜单')
    v18.equal(v18.scalar("SELECT COUNT(*) FROM system_role_menu WHERE role_id=160 AND deleted=0 "
                         "AND menu_id IN (5142,5064,1018,1019,1020)", database),
              '0', f'{database}: 学院教务管理员没有审批、绑定、组织增删改权限')
    v18.check('college_admin' not in v18.scalar("SELECT value FROM infra_config "
                                                "WHERE config_key='edu.role.admin-codes' AND deleted=0", database),
              f'{database}: admin-codes 已移除 college_admin')


def assert_upgrade(database, script):
    before = v18.snapshot(database)
    rows = v18.full_rows(database, v18.tables(database))
    v18.sql(script, database)
    after = v18.snapshot(database)
    new_rows = v18.full_rows(database, v18.tables(database))
    v18.equal(after['schema'], before['schema'], f'{database}: 不改列定义')
    v18.equal(after['indexes'], before['indexes'], f'{database}: 不改索引')
    # S6 合并后可能追加 system_role / infra_config 更新；按 SQL 声明确定触及的表。
    touched = set(re.findall(r'(?:INSERT INTO|UPDATE)\s+`?(\w+)`?', script, re.I))
    v18.equal(set(after['checksums']), set(before['checksums']), f'{database}: 不增删表')
    for table, old in rows.items():
        new = new_rows[table]
        if old is not None:
            v18.check(set(old) <= set(new), f'{database}: {table} 不能物理删除记录')
        if table not in touched:
            v18.equal(new, old, f'{database}: 未涉及表 {table} 不变')
    # 107 只允许改状态；5065 及关联只允许改 deleted，不能改其余列或丢弃行。
    for table, ids, allowed in (('system_menu', [107], {'status', 'update_time'}),
                                ('system_menu', [5065], {'deleted', 'update_time'}),
                                ('system_role_menu', [i for i, r in rows['system_role_menu'].items()
                                                      if r['role_id']=='1' and r['menu_id']=='5065'],
                                 {'deleted', 'update_time'})):
        for row_id in ids:
            v18.equal({k: v for k, v in new_rows[table][row_id].items() if k not in allowed},
                      {k: v for k, v in rows[table][row_id].items() if k not in allowed},
                      f'{database}: {table}/{row_id} 仅改指定字段')
    assert_final(database)
    v18.sql(script, database)
    v18.equal(v18.snapshot(database), after, f'{database}: 重复执行全表校验和、列与索引不变')


def flyway(database, *args):
    return v18.run('docker', 'run', '--rm', '--pull=never', '--network', f'container:{v18.NAME}',
                   '-v', f'{v18.MIGRATIONS}:/flyway/sql:ro', v18.FLYWAY_IMAGE,
                   f'-url=jdbc:mysql://127.0.0.1:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true',
                   '-user=root', '-password=', '-locations=filesystem:/flyway/sql', '-target=19', *args)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline18', help='必须标记为基线 18 的 ry261-test.sql')
    parser.add_argument('--baseline19', help='必须标记为基线 19 的 ry261-test.sql；默认读取 NUS-doc 工作区')
    args = parser.parse_args()
    base = baseline18(args.baseline18)
    base19, base19_source = baseline19(args.baseline19)
    scripts = list(v18.MIGRATIONS.glob('V19__*.sql'))
    v18.equal(len(scripts), 1, '唯一的 V19 迁移')
    file = scripts[0]
    script = file.read_text(encoding='utf-8')
    digest = hashlib.sha256(script.encode()).hexdigest()
    executable = re.sub(r'--[^\n]*', '', script)
    v18.check(not re.search(r'\b(DELETE\s+FROM|TRUNCATE|DROP|ALTER\s+TABLE)\b', executable, re.I),
              'V19 必须仅做可逆数据修改，不删助教 CSV 列')
    v18.check('学院教务管理员（B3 / S6）' in script, '已合并 S6 学院教务管理员片段')
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
        v18.create('fresh18', base)
        assert_upgrade('fresh18', script)
        notes.append('fresh18 全新基线 18 + V19、rerun 通过')
        if base19 is None:
            notes.append(f'fresh19 跳过：{base19_source}')
        else:
            v18.create('fresh19', base19)
            assert_final('fresh19')
            v18.equal(v18.structure('fresh19'), v18.structure('fresh18'),
                      '基线 19 与基线 18 + V19：全部表结构')
            v18.equal(v18.contents('fresh19'), v18.contents('fresh18'),
                      '基线 19 与基线 18 + V19：逐表逐行数据')
            fresh = v18.snapshot('fresh19')
            v18.sql(script, 'fresh19')
            v18.equal(v18.snapshot('fresh19'), fresh, 'fresh19: V19 在基线 19 上无改动')
            notes.append(f'fresh19 通过（{base19_source}）')
        v18.create('up18', base)
        v18.sql(v18.REPRESENTATIVE, 'up18')
        business = v18.full_rows('up18', ['capacity_course', 'edu_class_course', 'edu_homework',
                                         'edu_homework_record', 'edu_homework_ai_review'])
        assert_upgrade('up18', script)
        v18.equal(v18.full_rows('up18', business), business, '升级保留业务数据及助教 CSV 值')
        notes.append('up18 业务数据升级、rerun 通过')
        # 墓碑授权不阻止补一行生效授权；已软删的解绑菜单仍可修复遗留生效关联。
        v18.create('partial18', base)
        v18.sql("UPDATE system_menu SET deleted=1 WHERE id=5065; "
                "INSERT INTO system_role_menu(role_id,menu_id,deleted) VALUES(161,5064,1);", 'partial18')
        assert_upgrade('partial18', script)
        notes.append('partial18 墓碑与部分执行恢复通过')
        v18.create('guards18', base)
        v18.sql("UPDATE system_role SET code='custom_teacher' WHERE id=161; "
                "UPDATE system_role SET code='custom_admin' WHERE id=1; "
                "UPDATE system_menu SET permission='custom:course:unbind' WHERE id=5065; "
                "UPDATE system_menu SET name='自定义页面' WHERE id=107; "
                "UPDATE system_dict_data SET value='custom' WHERE id=3036; "
                "UPDATE system_notify_template SET deleted=1 WHERE code='edu.teacher.homework.submitted';",
                'guards18')
        guard_queries = {
            'menus': 'SELECT * FROM system_menu WHERE id IN (107,5065) ORDER BY id',
            'grants': 'SELECT * FROM system_role_menu WHERE (role_id=161 AND menu_id=5064) '
                      'OR (role_id=1 AND menu_id=5065) ORDER BY id',
            'dict': 'SELECT * FROM system_dict_data WHERE id=3036',
            'notify': "SELECT * FROM system_notify_template WHERE code='edu.teacher.homework.submitted' ORDER BY id",
        }
        guarded = {key: v18.sql(query, 'guards18') for key, query in guard_queries.items()}
        v18.sql(script, 'guards18')
        v18.equal({key: v18.sql(query, 'guards18') for key, query in guard_queries.items()}, guarded,
                  '标识不符或已删除对象不改（S6 的其他授权独立核对）')
        # 菜单 permission 和停用 / 软删除的教师角色分别阻止授予绑定权限。
        for database, mutation in (
                ('wrong_menu', "UPDATE system_menu SET permission='custom:course:bind' WHERE id=5064"),
                ('disabled_role', 'UPDATE system_role SET status=1 WHERE id=161'),
                ('deleted_role', 'UPDATE system_role SET deleted=1 WHERE id=161')):
            v18.create(database, base)
            v18.sql(mutation, database)
            old = v18.scalar('SELECT COUNT(*) FROM system_role_menu WHERE role_id=161 AND menu_id=5064 AND deleted=0',
                             database)
            v18.sql(script, database)
            v18.equal(v18.scalar('SELECT COUNT(*) FROM system_role_menu WHERE role_id=161 AND menu_id=5064 AND deleted=0',
                                 database), old, f'{database}: 不授予绑定权限')
        notes.append('guards 身份与删除/停用守卫通过')
        if subprocess.run(['docker', 'image', 'inspect', v18.FLYWAY_IMAGE], capture_output=True).returncode == 0:
            for database, fixture in (('fly_fresh18', ''), ('fly_up18', v18.REPRESENTATIVE)):
                v18.create(database, base)
                v18.sql(fixture, database)
                v18.check(hashlib.sha256(file.read_bytes()).hexdigest() == digest, '检查期间 V19 被改写')
                flyway(database, 'baseline', '-baselineVersion=18', '-baselineDescription=ry261-test.sql')
                flyway(database, 'migrate')
                v18.equal(v18.sql('SELECT version, success FROM flyway_schema_history ORDER BY installed_rank',
                                  database).splitlines(), ['18\t1', '19\t1'], f'{database}: Flyway 18 -> 19')
                assert_final(database)
                state = v18.snapshot(database)
                flyway(database, 'migrate')
                flyway(database, 'validate')
                v18.equal(v18.snapshot(database), state, f'{database}: 再次 migrate 无改动')
                direct = 'fresh18' if not fixture else 'up18'
                v18.equal(v18.contents(database), v18.contents(direct), f'{database}: 与直接执行逐表一致')
            notes.append('Flyway 全新安装 / 升级、再次 migrate、validate 通过')
        else:
            notes.append(f'Flyway 跳过：本地缺少 {v18.FLYWAY_IMAGE}，需负责人补验')
        v18.check(hashlib.sha256(file.read_bytes()).hexdigest() == digest, '检查期间 V19 被改写')
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
