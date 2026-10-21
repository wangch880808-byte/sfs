#!/usr/bin/env python3
"""Offline integration checks; disposable local containers only, no school access."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
script = (ROOT / 'deploy/offline/configure-domain.sh').read_text()
ns = {'__name__': 'test_import'}
exec(compile(script.split("<<'PY'\n", 1)[1].rsplit('\nPY', 1)[0], '<embedded>', 'exec'), ns)
ns['self_test']()


def run(args, data=None, check=True):
    p = subprocess.run(args, input=data, text=True, capture_output=True)
    if check and p.returncode:
        raise AssertionError(p.stderr)
    return p


with tempfile.TemporaryDirectory(prefix='nus-domain-test-') as tmp:
    tmp = Path(tmp)
    original = (ROOT / 'NUS-front/scripts/deploy/nginx.conf').read_text()
    for monitoring in (None, 'https://monitor.example.edu.cn'):
        edited = ns['nginx_config'](original, ns['DEFAULT_SITE'], monitoring)
        assert edited == ns['nginx_config'](edited, ns['DEFAULT_SITE'], monitoring)
        if monitoring is None:
            assert original.split('    listen 8082;', 1)[1] == edited.split('    listen 8082;', 1)[1]
        nginx_dir = tmp / ('monitor-https' if monitoring else 'monitor-internal')
        nginx_dir.mkdir()
        ns['atomic'](nginx_dir / 'nginx.conf', edited, 0o644)
        run(['docker', 'run', '--rm', '--pull=never', '--network', 'none',
             '--add-host', 'backend:127.0.0.1', '--add-host', 'skywalking-ui:127.0.0.1',
             '-v', str(nginx_dir) + ':/etc/nus-domain:ro', '--entrypoint', 'nginx',
             'nus-front:social-20260905.1', '-t', '-c', '/etc/nus-domain/nginx.conf'])
    print('PASS: actual release nginx image validates both gateway configurations')
    baseline = (ROOT / 'releases/nus-offline-V1.0.1-ubuntu24.04-amd64/runtime/compose.yaml').read_text()
    patched = ns['compose_config'](baseline)
    assert patched == ns['compose_config'](patched)
    (tmp / 'compose.yaml').write_text(patched)
    import re
    keys = set(re.findall(r'\$\{([A-Z_][A-Z_0-9]*)(?:[:?}-])', baseline))
    # Fake values solely to validate structure. Never load real credentials.
    (tmp / 'env').write_text('\n'.join(k + '=test' for k in keys if k.endswith(('PASSWORD', 'KEY', 'IMAGE')) or k in {'NUS_HORIZON_AUTH_LOCAL_USERS', 'NUS_SKYWALKING_URL', 'NUS_MONITOR_USERNAME'}) + '\n')
    run(['docker', 'compose', '--env-file', str(tmp / 'env'), '-f', str(tmp / 'compose.yaml'), 'config', '--quiet'])
    print('PASS: actual release Compose parses; repeat runs do not duplicate mounts')

    name = 'nus-domain-test-' + uuid.uuid4().hex[:10]
    started = False
    try:
        run(['docker', 'run', '-d', '--pull=never', '--network', 'none', '--name', name,
             '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', 'mysql:8.4.6'])
        started = True
        for i in range(90):
            p = run(['docker', 'exec', name, 'mysql', '-uroot', '-Nse', 'SELECT 1'], check=False)
            if p.returncode == 0:
                break
            time.sleep(1)
        else:
            raise AssertionError('test mysql startup timeout')
        def sql(s, check=True):
            return run(['docker', 'exec', '-i', name, 'mysql', '-uroot', '--batch', '--raw', '-N'], s, check)
        sql('CREATE DATABASE test_domain; USE test_domain; CREATE TABLE refs(id INT PRIMARY KEY, body TEXT) ENGINE=InnoDB;')
        old = 'http://10.249.1.63'
        new = ns['DEFAULT_SITE']
        values = [old + '/admin-api/infra/file/1/get/example.pdf',
                  json.dumps([old + '/admin-api/infra/file/1/get/a.png']).replace('/', '\\/'),
                  old + ':8082/', 'http://10.249.1.630/admin-api/infra/file/1/get/x',
                  'https://api.deepseek.com', '<img src="' + old + '/admin-api/infra/file/1/get/a.png">']
        sql('USE test_domain; INSERT INTO refs VALUES ' + ','.join(f'({i},{ns["lit"](v)})' for i, v in enumerate(values)) + ';')
        expr = ns['rewrite_expression']('body', old, new)
        migration = f'USE test_domain; START TRANSACTION; UPDATE refs SET body={expr} WHERE BINARY body<>BINARY {expr}; COMMIT;'
        sql(migration)
        actual = sql('USE test_domain; SELECT body FROM refs ORDER BY id;').stdout.splitlines()
        assert actual[0] == values[0].replace(old, new)
        assert actual[1] == values[1].replace(old.replace('/', '\\/'), new.replace('/', '\\/'))
        assert actual[2:5] == values[2:5]
        assert actual[5] == values[5].replace(old, new)
        sql(migration)
        assert sql('USE test_domain; SELECT body FROM refs ORDER BY id;').stdout.splitlines() == actual
        # Prove SQL failures do not leave earlier changes committed.
        failure = sql("USE test_domain; START TRANSACTION; UPDATE refs SET body='wrong' WHERE id=0; INSERT INTO refs VALUES(0,'duplicate'); COMMIT;", check=False)
        assert failure.returncode != 0
        assert sql('USE test_domain; SELECT body FROM refs WHERE id=0;').stdout.strip() == actual[0]
        print('PASS: MySQL URL rewrite, escaped JSON, HTML, repeat run, unrelated host/monitor preservation, transaction rollback')
    finally:
        if started:
            run(['docker', 'rm', '-f', '-v', name])
