#!/usr/bin/env python3
"""Verify V14 against isolated MySQL; never connects to an existing database."""
import pathlib
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[3]
NAME = 'nus-demo-check-' + uuid.uuid4().hex[:10]

def run(*args, input=None):
    return subprocess.run(args, input=input, text=True, check=True, capture_output=True).stdout

def sql(text, database):
    return run('docker', 'exec', '-i', NAME, 'mysql', '-uroot', '-N', database, input=text)

try:
    run('docker', 'run', '-d', '--name', NAME, '--network', 'none', '--tmpfs',
        '/var/lib/mysql', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', 'mysql:8.4')
    for attempt in range(60):
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
    run('docker', 'exec', NAME, 'mysql', '-uroot', '-e',
        'CREATE DATABASE fresh; CREATE DATABASE upgrade_check;')
    baseline = (ROOT / 'NUS-doc/3-数据库文件/ry261-test.sql').read_text()
    migration = (ROOT / 'deploy/database/migrations/V14__remove_scaffold_demo_menus.sql').read_text()
    sql(baseline, 'fresh')
    assert sql("SELECT COUNT(*) FROM system_menu WHERE component LIKE 'infra/demo/%' OR component='infra/testDemo/index' OR permission LIKE 'infra:demo%' OR component='infra/webSocket/index';", 'fresh').strip() == '0'
    sql(migration, 'fresh')
    sql(migration, 'fresh')
    # Reconstruct original demo identities from the migration's exact-match predicates.
    import re
    predicates = re.findall(r'\(id = (\d+) AND name = (.+?) AND permission = (.+?) AND parent_id = (\d+) AND path = (.+?) AND component = (.+?)\)', migration)
    assert len(predicates) == 22
    setup = '''CREATE TABLE system_menu (id BIGINT PRIMARY KEY, name VARCHAR(128), permission VARCHAR(128), parent_id BIGINT, path VARCHAR(128), component VARCHAR(128), deleted BIT DEFAULT 0, status INT DEFAULT 0);
CREATE TABLE system_role_menu (menu_id BIGINT);
'''
    for entry in predicates:
        setup += 'INSERT INTO system_menu (id,name,permission,parent_id,path,component) VALUES (' + ','.join(entry) + ');\n'
        setup += 'INSERT INTO system_role_menu VALUES (' + entry[0] + ');\n'
    setup += "INSERT INTO system_menu VALUES (90001,'教学业务','edu:course:query',0,'course','edu/course/index',0,0); INSERT INTO system_role_menu VALUES (90001);"
    sql(setup, 'upgrade_check')
    sql(migration, 'upgrade_check')
    assert sql("SELECT COUNT(*) FROM system_menu WHERE deleted=1;", 'upgrade_check').strip() == '22'
    assert sql('SELECT menu_id FROM system_role_menu;', 'upgrade_check').strip() == '90001'
    sql(migration, 'upgrade_check')
    assert sql("SELECT COUNT(*) FROM system_menu WHERE deleted=1;", 'upgrade_check').strip() == '22'
    # Customized child protects its demo page and ancestor; unrelated demos still retire.
    sql("UPDATE system_menu SET deleted=0,status=0; UPDATE system_menu SET name='自定义功能' WHERE id=2479;", 'upgrade_check')
    sql(migration, 'upgrade_check')
    assert sql('SELECT id FROM system_menu WHERE deleted=0 ORDER BY id;', 'upgrade_check').strip().splitlines() == ['1070', '2478', '2479', '90001']
    print('PASS: fresh baseline, 22 original demos, role bindings, repeated migration, customized child/ancestor protection')
finally:
    subprocess.run(['docker', 'rm', '-f', NAME], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
