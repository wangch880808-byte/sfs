#!/usr/bin/env python3
"""V20 检查脚本的本地回归；不运行容器，不执行 Git 写操作。"""
import importlib.util
import os
from pathlib import Path
import re
import tempfile
import subprocess
import sys
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('v20', Path(__file__).with_name('check-v20.py'))
v20 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v20)


def definitions(text):
    return {table: block for table, block in re.findall(
        r'CREATE TABLE `(\w+)` \(\n(.*?)\n\) ENGINE=[^;]*;', text, re.S)}


def without_indexes(text):
    # 只排除允许调整的普通索引，列、唯一键和表外数据都仍参与比较。
    text = re.sub(r'^-- Flyway baseline version: \d+$', '-- Flyway baseline version: N', text, flags=re.M)
    # 基线 20 末尾追加了与 V20 一致的直方图语句（统计信息，不是结构或数据），比较时去掉，另行校验。
    text = re.sub(r'\n-- 与 V20 一致：状态类列的直方图[^\n]*\n(?:ANALYZE TABLE [^\n]*\n?)*', '', text)
    for table, block in definitions(text).items():
        lines = [line for line in block.splitlines() if not line.lstrip().startswith('KEY `')]
        lines[-1] = lines[-1].rstrip(',')
        text = text.replace(block, '\n'.join(lines), 1)
    return text


class CheckTests(unittest.TestCase):
    def test_actual_migration_and_independent_guards(self):
        _, script = v20.migration()
        blocks = re.findall(r"SET @nus_v20_ddl = IF\((.*?)\n\);", script, re.S)
        self.assertEqual(len(blocks), 17)
        for block, (table, name) in zip(blocks, list(v20.ADDED) + list(v20.REMOVED)):
            actions = re.findall(r"^    '([^']*)'", block, re.M)
            if (table, name) in v20.ADDED:
                self.assertEqual(actions[0], 'SELECT 1')
                self.assertTrue(actions[1].startswith(f'ALTER TABLE `{table}` ADD INDEX `{name}`'))
            else:
                self.assertEqual(actions, [f'ALTER TABLE `{table}` DROP INDEX `{name}`', 'SELECT 1'])

    def test_baseline20_matches19_except_requested_indexes(self):
        try:
            old, _ = v20.baseline(19, None, history=True)
            new, _ = v20.baseline(20, None)
        except RuntimeError as error:
            self.skipTest(str(error))
        self.assertEqual(without_indexes(new), without_indexes(old))
        old_tables, new_tables = definitions(old), definitions(new)
        changed = set()
        for table, block in old_tables.items():
            old_keys = dict(re.findall(r'^  KEY `(\w+)` \(([^)]*)\)', block, re.M))
            new_keys = dict(re.findall(r'^  KEY `(\w+)` \(([^)]*)\)', new_tables[table], re.M))
            expected = dict(old_keys)
            for (t, name), columns in v20.REMOVED.items():
                if t == table:
                    self.assertEqual(expected.pop(name), ','.join(f'`{c}`' for c in columns))
            for (t, name), columns in v20.ADDED.items():
                if t == table:
                    expected[name] = ','.join(f'`{c}`' for c in columns)
            self.assertEqual(new_keys, expected, table)
            if old_keys != new_keys:
                changed.add(table)
        self.assertEqual(changed, {table for table, _ in v20.ADDED})
        for table, _ in v20.REMOVED:
            self.assertNotIn('FOREIGN KEY', old_tables[table])
            self.assertNotRegex(old, rf'FOREIGN KEY[^;]*REFERENCES `{table}`')
        # 不同场景在不同秒导入；参与跨库比较的动态默认时间必须在代表性数据里固定。
        for table, columns in re.findall(r'INSERT INTO (\w+) \(([^)]*)\)', v20.REPRESENTATIVE):
            inserted = {column.strip() for column in columns.split(',')}
            dynamic_times = set(re.findall(r'^  `(\w+)`[^\n]*DEFAULT CURRENT_TIMESTAMP',
                                           new_tables[table], re.M)) - set(v20.v18.TIME_COLUMNS)
            self.assertTrue(dynamic_times <= inserted, f'{table}: 动态时间未固定：{dynamic_times - inserted}')

    def test_explicit_baseline_rejects_wrong_version(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'baseline.sql'
            file.write_text('-- Flyway baseline version: 19\n')
            self.assertEqual(v20.baseline(19, file)[1], str(file))
            with self.assertRaisesRegex(AssertionError, '未标记为基线 20'):
                v20.baseline(20, file)

    def test_current_worktree_precedes_history_and_missing20_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            doc = Path(directory)
            file = doc / v20.v18.BASELINE_REL
            file.parent.mkdir()
            file.write_text('-- Flyway baseline version: 20\n')
            with patch.object(v20, 'DOCS', (doc,)), patch.dict(os.environ, {}, clear=True), \
                    patch.object(v20.v18, 'run') as run:
                self.assertEqual(v20.baseline(20, None)[1], str(file))
                run.assert_not_called()
                file.unlink()
                with self.assertRaisesRegex(RuntimeError, '找不到基线 20'):
                    v20.baseline(20, None)

    def test_mono_history_uses_repository_root_and_full_baseline_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            doc = root / 'NUS-doc'
            doc.mkdir()  # 普通目录，没有 .git。
            relative = f'NUS-doc/{v20.v18.BASELINE_REL}'
            old = '-- Flyway baseline version: 20\n'
            def git(*args):
                if args[3:] == ('rev-parse', '--show-toplevel'):
                    return str(root) + '\n'
                if args[3:] == ('log', '--format=%H', '--', relative):
                    return 'new-mono-commit\n'
                if args[3:] == ('show', f'HEAD:{relative}'):
                    return '-- Flyway baseline version: 21\n'
                if args[3:] == ('show', f'new-mono-commit:{relative}'):
                    return old
                self.fail(f'意外的 git 调用：{args}')
            with patch.dict(os.environ, {}, clear=True), patch.object(v20.v18, 'run', side_effect=git) as run:
                text, source = v20.v18.read_baseline(20, docs=(doc,))
                self.assertEqual(text, old)
                self.assertIn('new-mono-commit', source)
                for call in run.call_args_list[1:]:
                    self.assertEqual(call.args[2], root)
                run.reset_mock()
                self.assertEqual(v20.v18.read_baseline(20, docs=(doc,), revision='new-mono-commit')[0], old)
                self.assertFalse(any('log' in call.args for call in run.call_args_list))
                run.reset_mock()
                self.assertEqual(v20.v18.read_baseline(21, docs=(doc,), revision='HEAD')[0],
                                 '-- Flyway baseline version: 21\n')

    def test_missing_history_explains_file_parameter_and_environment_override(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'old.sql'
            file.write_text('-- Flyway baseline version: 20\n')
            with patch.dict(os.environ, {}, clear=True), \
                    patch.object(v20.v18, 'run', side_effect=RuntimeError('历史不可用')):
                with self.assertRaisesRegex(RuntimeError, '--baseline20 .*NUS_BASELINE20_SQL'):
                    v20.v18.read_baseline(20, docs=(Path(directory),))
                with patch.dict(os.environ, {'NUS_BASELINE20_SQL': str(file)}):
                    self.assertEqual(v20.v18.read_baseline(20)[1], str(file))
                with self.assertRaisesRegex(RuntimeError, '文件不存在或不可读'):
                    v20.v18.read_baseline(20, file.with_name('missing.sql'))

    def test_all_migration_clis_report_missing_old_baseline_without_traceback(self):
        # 当前交接仓库只有基线 21；全部旧检查应在调用 Docker 之前说明所需文件参数。
        versions = {15: 14, 16: 15, 17: 16, 18: 17, 19: 18, 20: 19, 21: 20}
        env = {key: value for key, value in os.environ.items() if not key.startswith('NUS_BASELINE')}
        env['PYTHONDONTWRITEBYTECODE'] = '1'
        for script_version, baseline_version in versions.items():
            with self.subTest(version=script_version):
                result = subprocess.run([sys.executable, str(Path(__file__).with_name(f'check-v{script_version}.py'))],
                                        capture_output=True, text=True, env=env)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn(f'--baseline{baseline_version}', result.stderr)
                self.assertNotIn('Traceback', result.stderr)
                self.assertNotIn('docker', result.stderr.lower())

    def test_flyway_only_uses_disposable_container_and_target20(self):
        with patch.object(v20.v18, 'run') as run:
            v20.flyway('fly_fresh19', 'validate')
        args = run.call_args.args
        self.assertIn('--pull=never', args)
        self.assertEqual(args[args.index('--network') + 1], f'container:{v20.v18.NAME}')
        self.assertIn(f'{v20.v18.MIGRATIONS}:/flyway/sql:ro', args)
        self.assertIn('-target=20', args)
        self.assertEqual(args[-1], 'validate')
        self.assertTrue(any(str(arg).startswith('-url=jdbc:mysql://127.0.0.1:3306/') for arg in args))

    def test_upgrade_rejects_data_changes(self):
        before = {'schema': 'same', 'checksums': {'up19.system_users': 'old'}, 'indexes': ''}
        after = {**before, 'checksums': {'up19.system_users': 'changed'}}
        with patch.object(v20.v18, 'snapshot', side_effect=[before, after]), \
                patch.object(v20.v18, 'tables', return_value=[]), \
                patch.object(v20.v18, 'full_rows', return_value={}), patch.object(v20.v18, 'sql'):
            with self.assertRaisesRegex(AssertionError, '所有表数据及时间列不变'):
                v20.assert_upgrade('up19', 'fixture')

    def test_upgrade_rejects_unplanned_index_change(self):
        before = {'schema': 'same', 'checksums': {}, 'indexes': 'table\tkeep\t1\tid\t<NULL>\t0'}
        after = {**before, 'indexes': '\n'.join(v20.index_lines(v20.ADDED))}
        with patch.object(v20.v18, 'snapshot', side_effect=[before, after]), \
                patch.object(v20.v18, 'tables', return_value=[]), \
                patch.object(v20.v18, 'full_rows', return_value={}), patch.object(v20.v18, 'sql'):
            with self.assertRaisesRegex(AssertionError, '唯一键及其余索引不变'):
                v20.assert_upgrade('up19', 'fixture')

    def test_partial_fixture_keeps_all_old_indexes(self):
        indexes = v20.index_lines(v20.REMOVED) + v20.index_lines(dict(list(v20.ADDED.items())[:5]))
        with patch.object(v20.v18, 'snapshot', return_value={'indexes': '\n'.join(indexes)}), \
                patch.object(v20.v18, 'sql') as sql:
            v20.partial_indexes('partial')
        self.assertEqual(sql.call_count, 5)
        self.assertTrue(all('ADD INDEX' in call.args[0] and call.args[1] == 'partial'
                            for call in sql.call_args_list))

    def test_business_checksums_include_tables_without_id(self):
        checksums = {'up19.flyway_schema_history': 'history', 'up19.QRTZ_TRIGGERS': 'quartz',
                     'up19.system_users': 'users'}
        with patch.object(v20.v18, 'snapshot', return_value={'checksums': checksums}):
            self.assertEqual(v20.business_checksums('up19'),
                             {'up19.QRTZ_TRIGGERS': 'quartz', 'up19.system_users': 'users'})


if __name__ == '__main__':
    unittest.main(verbosity=2)
