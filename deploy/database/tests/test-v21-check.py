#!/usr/bin/env python3
"""V21 检查脚本本地回归，不运行容器，不执行 Git 写操作。"""
import copy
import importlib.util
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('v21', Path(__file__).with_name('check-v21.py'))
v21 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v21)


def definitions(text):
    return dict(re.findall(r'CREATE TABLE `(\w+)` \(\n(.*?)\n\) ENGINE=[^;]*;', text, re.S))


def fixture_rows():
    return {'capacity_course': {94000+i: {'is_public_course': str(int(i == 2)),
                                         'deleted': str(int(i == 3))} for i in range(1, 5)},
            'edu_class_course': {94000+i: {'course_id': str(94000+c), 'class_id': str(94000+cl),
                                          'semester_name': sem, 'status': str(status), 'deleted': str(deleted),
                                          'updater': 'system-sync', 'update_time': '2026-08-02 08:00:00.000000'}
                                 for i, c, sem, status, deleted, cl in v21.RELATIONS},
            'edu_homework': {95001+n: {'course_id': str(94000+v21.RELATIONS[i-1][1]),
                                      'class_id': str(94000+v21.RELATIONS[i-1][5]),
                                      'teacher_id': str(teacher), 'status': str(status),
                                      'source_homework_id': '<NULL>' if source is None else str(source),
                                      'semester_name': sem, 'deleted': str(deleted),
                                      'create_time': created+'.000000'}
                             for n, (i, teacher, status, source, sem, deleted, created) in
                             enumerate(v21.HOMEWORKS)}}


def state():
    return {'schema': 'edu_class_course\tid\tbigint', 'rows': fixture_rows(),
            'indexes': 'edu_class_course\tuk_cc_semester\t1\tclass_id\t<NULL>\t0',
            'checksums': {'edu_class_course': 'cc', 'edu_homework': 'hw', 'QRTZ_TRIGGERS': 'qt'}}


def migrated(before):
    after = copy.deepcopy(before)
    after['rows'] = v21.expected_rows(before)
    after['indexes'] += '\n'+'\n'.join(v21.v20.index_lines(v21.INDEXES))
    after['schema'] += '\nedu_class_course\tclaim_teacher_id\tbigint\nedu_class_course\tclaim_time\tdatetime'
    after['checksums']['edu_class_course'] = 'claims'
    return after


class CheckTests(unittest.TestCase):
    def test_baseline21_only_adds_claim_columns_indexes_marker(self):
        try:
            old, _ = v21.baseline(20)
        except RuntimeError as error:
            if os.environ.get('NUS_BASELINE20_SQL') or not str(error).startswith('找不到基线 20'):
                raise
            self.skipTest(str(error))  # 新仓库没有旧 SQL 时明确跳过跨基线比较，不伪造基线。
        new, _ = v21.baseline(21)
        restored = new.replace('-- Flyway baseline version: 21', '-- Flyway baseline version: 20')
        restored = re.sub(r'^  `claim_(teacher_id|time)`[^\n]*\n', '', restored, flags=re.M)
        restored = re.sub(r'^  KEY `idx_edu_class_course_claim(?:_teacher)?`[^\n]*\n', '', restored, flags=re.M)
        restored = restored.replace('KEY `idx_edu_class_course_backfill` (`backfill_pending`,`deleted`,`course_id`),',
                                     'KEY `idx_edu_class_course_backfill` (`backfill_pending`,`deleted`,`course_id`)')
        self.assertEqual(restored, old)

    def test_current_baseline21_claim_columns_and_indexes(self):
        new, _ = v21.baseline(21)
        block = definitions(new)['edu_class_course']
        self.assertIn("`claim_teacher_id` bigint DEFAULT NULL COMMENT '公共课认领教师（用户 ID）'", block)
        self.assertIn("`claim_time` datetime DEFAULT NULL COMMENT '认领时间'", block)
        for (_, name), cols in v21.INDEXES.items():
            self.assertIn(f"KEY `{name}` ("+','.join(f'`{c}`' for c in cols)+')', block)

    def test_migration_independent_guards_and_write_scope(self):
        _, script = v21.migration()
        blocks = re.findall(r'SET @nus_v21_ddl = IF\((.*?)\n\);', script, re.S)
        self.assertEqual(len(blocks), 4)
        for block, name in zip(blocks, [*v21.CLAIM_COLUMNS, *(k[1] for k in v21.INDEXES)]):
            self.assertIn('table_schema = DATABASE()', block)
            self.assertIn("table_name = 'edu_class_course'", block)
            self.assertIn(f"= '{name}'", block)
            self.assertIn("'SELECT 1',", block)
        executable = re.sub(r'--[^\n]*', '', script)
        self.assertEqual(re.findall(r'ALTER TABLE `(\w+)`', executable), ['edu_class_course']*4)
        self.assertNotRegex(executable, r'\b(DELETE|TRUNCATE|REPLACE|FOREIGN KEY)\b')
        self.assertEqual(re.findall(r'\bUPDATE (\w+) cc', executable), ['edu_class_course'])
        assignments = executable.split('SET cc.claim_teacher_id =', 1)[1].split('WHERE', 1)[0]
        self.assertEqual(re.findall(r'cc\.(\w+)\s*=', assignments), ['claim_time', 'update_time'])
        self.assertIn('cc.update_time = cc.update_time', assignments)
        self.assertNotIn('updater', assignments)
        for condition in ('c.deleted = 0', 'c.is_public_course = 0', 'h.deleted = 0',
                          'h.source_homework_id IS NULL', 'h.status IN (1, 2)',
                          'cc.deleted = 0', 'cc.status = 0', 'cc.claim_teacher_id IS NULL',
                          'HAVING COUNT(DISTINCT h.teacher_id) = 1'):
            self.assertIn(condition, executable)
        self.assertIn('MIN(h.create_time)', executable)
        self.assertIn('MIN(h.first_create_time)', executable)
        self.assertIn("h.semester_name = '' OR h.semester_name = TRIM(cc.semester_name)", executable)
        self.assertEqual(executable.count('PREPARE nus_v21_stmt FROM @nus_v21_ddl;'), 4)
        self.assertEqual(executable.count('EXECUTE nus_v21_stmt;'), 4)
        self.assertEqual(executable.count('DEALLOCATE PREPARE nus_v21_stmt;'), 4)

    def test_fixture_oracle_matches_independent_expected_claims(self):
        before = state()
        original = copy.deepcopy(before)
        actual = v21.expected_rows(before)['edu_class_course']
        self.assertEqual(before, original)
        for key, cc in actual.items():
            self.assertEqual(tuple(cc[c] for c in v21.CLAIM_COLUMNS),
                             v21.EXPECTED.get(key, ('<NULL>', '<NULL>')), key)
        # 已有认领不可覆盖，即使历史任务会推断出另一位教师。
        before['rows']['edu_class_course'][94001].update(claim_teacher_id='99', claim_time='original')
        after = v21.expected_rows(before)
        self.assertEqual(after['edu_class_course'][94001]['claim_teacher_id'], '99')
        self.assertEqual(after['edu_class_course'][94001]['claim_time'], 'original')

    def test_preservation_rejects_wrong_claim_audit_and_unrelated_changes(self):
        before = state()
        good = migrated(before)
        with patch.object(v21, 'capture', return_value=good), patch.object(v21, 'assert_final'):
            self.assertEqual(v21.assert_preserved('up20', before), good)
        changes = [('edu_class_course', 94001, 'updater', 'teacher'),
                   ('edu_class_course', 94001, 'update_time', 'changed'),
                   ('edu_class_course', 94002, 'claim_teacher_id', '11'),
                   ('edu_homework', 95001, 'status', '3')]
        for table, key, column, value in changes:
            with self.subTest(table=table, column=column):
                wrong = copy.deepcopy(good)
                wrong['rows'][table][key][column] = value
                with patch.object(v21, 'capture', return_value=wrong), patch.object(v21, 'assert_final'):
                    with self.assertRaisesRegex(AssertionError, '所有原字段'):
                        v21.assert_preserved('up20', before)
        wrong = copy.deepcopy(good)
        wrong['checksums']['QRTZ_TRIGGERS'] = 'changed'
        with patch.object(v21, 'capture', return_value=wrong), patch.object(v21, 'assert_final'):
            with self.assertRaisesRegex(AssertionError, '其余表全表校验和'):
                v21.assert_preserved('up20', before)

    def test_preservation_rejects_unique_key_or_column_loss(self):
        before = state()
        for field in ('schema', 'indexes'):
            wrong = migrated(before)
            wrong[field] = ''
            with self.subTest(field=field), patch.object(v21, 'capture', return_value=wrong):
                with self.assertRaises(AssertionError):
                    v21.assert_preserved('up20', before)

    def test_baseline21_required_and_explicit_version_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            doc = Path(directory)
            file = doc / v21.v18.BASELINE_REL
            file.parent.mkdir()
            file.write_text('-- Flyway baseline version: 20\n')
            with patch.object(v21, 'DOCS', (doc,)), patch.dict(os.environ, {}, clear=True), \
                    patch.object(v21.v18, 'run') as run:
                self.assertEqual(v21.baseline(20)[1], str(file))
                with self.assertRaisesRegex(RuntimeError, '找不到基线 21'):
                    v21.baseline(21)
                run.assert_not_called()
                with self.assertRaisesRegex(AssertionError, '未标记为基线 21'):
                    v21.baseline(21, file)
                file.write_text('-- Flyway baseline version: 21\n')
                self.assertEqual(v21.baseline(21)[1], str(file))

    def test_flyway_isolated_and_target21(self):
        with patch.object(v21.v18, 'run') as run:
            v21.flyway('fly_up20', 'validate')
        args = run.call_args.args
        self.assertIn('--pull=never', args)
        self.assertEqual(args[args.index('--network')+1], f'container:{v21.v18.NAME}')
        self.assertIn(f'{v21.v18.MIGRATIONS}:/flyway/sql:ro', args)
        self.assertIn('-target=21', args)
        self.assertEqual(args[-1], 'validate')
        self.assertIn('-url=jdbc:mysql://127.0.0.1:3306/fly_up20?useSSL=false&allowPublicKeyRetrieval=true', args)

    def test_partial_executes_columns_only_and_optionally_preserves_claim(self):
        _, script = v21.migration()
        with patch.object(v21.v18, 'sql') as sql, patch.object(v21.v18, 'scalar', return_value='0'):
            v21.partial_columns('partial', script, preserved=True)
        ddl = sql.call_args_list[0].args[0]
        self.assertEqual(ddl.count('ALTER TABLE'), 2)
        self.assertNotIn('ADD INDEX', ddl)
        self.assertNotIn('INSERT INTO', ddl)
        self.assertIn('claim_teacher_id=99', sql.call_args_list[1].args[0])
        self.assertIn('update_time=update_time', sql.call_args_list[1].args[0])


if __name__ == '__main__':
    unittest.main(verbosity=2)
