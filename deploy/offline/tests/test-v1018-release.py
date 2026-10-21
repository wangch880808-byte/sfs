#!/usr/bin/env python3
"""基线 21 离线发布与 MySQL 健康检查回归；只用临时文件和 fake docker，不执行 git 写操作。"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


def mysql_healthcheck(path):
    mysql = path.read_text().split('\n  mysql:', 1)[1].split('\n  redis:', 1)[0]
    return json.loads(re.search(r'^\s+test: (\[.*\])$', mysql, re.M).group(1))


class ReleaseTests(unittest.TestCase):
    def test_healthcheck_password_environment_only(self):
        kind, command = mysql_healthcheck(ROOT / 'deploy/compose.yaml')
        self.assertEqual(kind, 'CMD-SHELL')
        self.assertIn('$${MYSQL_ROOT_PASSWORD}', command)
        with tempfile.TemporaryDirectory(prefix='nus-health-v19-') as directory:
            root = Path(directory)
            mysqladmin = root / 'mysqladmin'
            mysqladmin.write_text('''#!/usr/bin/env python3
import os, sys
assert os.environ['MYSQL_PWD'] == os.environ['EXPECTED_PASSWORD']
assert sys.argv[1:] == ['ping', '-h', '127.0.0.1', '-u', 'root', '--silent']
print('healthy')
''')
            mysqladmin.chmod(0o755)
            # 特殊字符仍经双引号完整传递；绝不变成参数或执行命令。
            secret = 'Health test " $ ` ; 空格'
            env = {**os.environ, 'PATH': str(root) + os.pathsep + os.environ['PATH'],
                   'MYSQL_ROOT_PASSWORD': secret, 'EXPECTED_PASSWORD': secret}
            result = subprocess.run(['sh', '-c', command.replace('$$', '$')], env=env,
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout, 'healthy\n')
            self.assertNotIn(secret, result.stdout + result.stderr)

    def test_release_baseline21_and_copied_healthcheck(self):
        with tempfile.TemporaryDirectory(prefix='nus-release-v21-') as directory:
            root = Path(directory)
            shutil.copytree(ROOT / 'deploy', root / 'deploy')
            doc = root / 'NUS-doc/3-数据库文件'
            doc.mkdir(parents=True)
            baseline = doc / 'ry261-test.sql'
            baseline.write_text('-- Flyway baseline version: 21\n')
            apt = root / 'apt-cache'
            apt.mkdir()
            for package in ('docker.io', 'docker-compose-v2', 'containerd', 'runc'):
                (apt / f'{package}_test.deb').write_text('fixture')
            binary = root / 'bin'
            binary.mkdir()
            docker = binary / 'docker'
            docker.write_text('''#!/bin/sh
set -eu
case "$1 $2" in
  'image inspect')
    case "$*" in
      *Architecture*) echo linux/amd64 ;;
      *) echo fixture@sha256:test ;;
    esac ;;
  'save --platform')
    while [ "$#" -gt 0 ]; do
      if [ "$1" = -o ]; then shift; printf fixture > "$1"; exit 0; fi
      shift
    done
    exit 1 ;;
  *) exit 1 ;;
esac
''')
            docker.chmod(0o755)
            env = {**os.environ, 'PATH': str(binary) + os.pathsep + os.environ['PATH'],
                   'APT_CACHE_DIR': str(apt), 'BACKEND_IMAGE': 'backend:test', 'FRONTEND_IMAGE': 'frontend:test'}
            builder = root / 'deploy/offline/build-release.sh'
            # 不继承发布镜像覆写，保持可重现的 stub。
            for name in ('MYSQL_IMAGE', 'REDIS_IMAGE', 'FLYWAY_IMAGE', 'ELASTICSEARCH_IMAGE',
                         'SKYWALKING_OAP_IMAGE', 'SKYWALKING_UI_IMAGE'):
                env.pop(name, None)
            result = subprocess.run(['sh', str(builder), 'V1.0.1.10'], env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            release = root / 'releases/nus-offline-V1.0.1.10-ubuntu24.04-amd64'
            self.assertIn('BASELINE_VERSION=21\n', (release / 'release.env').read_text())
            self.assertEqual(mysql_healthcheck(release / 'runtime/compose.yaml'),
                             mysql_healthcheck(ROOT / 'deploy/compose.yaml'))
            self.assertTrue((release / 'database/migrations/V21__v1_0_1_10_public_course_claim.sql').is_file())
            verify = subprocess.run(['sh', '-c', 'sha256sum --check --quiet SHA256SUMS'], cwd=release,
                                    text=True, capture_output=True)
            self.assertEqual(verify.returncode, 0, verify.stderr)
            # 全量打包必须拒绝未更新的基线，不能把基线 20 冒充为 21。
            baseline.write_text('-- Flyway baseline version: 20\n')
            result = subprocess.run(['sh', str(builder), 'V1.0.1.10-bad'], env=env, text=True, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('not marked as Flyway baseline version 21', result.stderr)
            self.assertFalse((root / 'releases/nus-offline-V1.0.1.10-bad-ubuntu24.04-amd64').exists())

    def test_baseline_gate_defaults21_and_allows_explicit19(self):
        with tempfile.TemporaryDirectory(prefix='nus-baseline-gate-') as directory:
            binary = Path(directory)
            docker = binary / 'docker'
            docker.write_text('''#!/usr/bin/env python3
import os, re, sys
query = sys.argv[-1]
if 'information_schema.tables' in query:
    print(1)
else:
    version = re.search("version='([0-9]+)'", query).group(1)
    print(int(version == os.environ['HISTORY_VERSION']))
''')
            docker.chmod(0o755)
            env = {**os.environ, 'PATH': str(binary) + os.pathsep + os.environ['PATH'],
                   'MYSQL_CONTAINER': 'fixture', 'NUS_DB_ROOT_PASSWORD': 'fixture-password',
                   'NUS_DB_NAME': 'fixture', 'HISTORY_VERSION': '21'}
            env.pop('BASELINE_VERSION', None)
            gate = ROOT / 'deploy/database/baseline-gate.sh'
            result = subprocess.run(['sh', str(gate), 'check-baselined'], env=env, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            env['HISTORY_VERSION'] = '19'
            result = subprocess.run(['sh', str(gate), 'check-baselined'], env=env, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            env['BASELINE_VERSION'] = '19'
            result = subprocess.run(['sh', str(gate), 'check-baselined'], env=env, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
