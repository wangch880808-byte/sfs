#!/usr/bin/env python3
"""V1.0.1.8 验收脚本辅助函数与 CLI 回归；不连接后端、不执行 git 写操作。"""
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'deploy/tests/e2e/e2e_v1018.py'
spec = importlib.util.spec_from_file_location('e2e18', SCRIPT)
e2e = importlib.util.module_from_spec(spec)
spec.loader.exec_module(e2e)


def template(absolute=False):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        archive.writestr('xl/workbook.xml', '''<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>
          <sheet name="模板信息" sheetId="1" r:id="rId1"/><sheet name="学生档案" sheetId="2" r:id="rId2"/>
          <sheet name="班级关系" sheetId="3" r:id="rId3"/></sheets></workbook>''')
        targets = ''.join(f'<Relationship Id="rId{i}" Target="{("/xl/" if absolute else "")}worksheets/sheet{i}.xml"/>'
                          for i in (1, 2, 3))
        archive.writestr('xl/_rels/workbook.xml.rels', '<Relationships>' + targets + '</Relationships>')
        archive.writestr('xl/worksheets/sheet1.xml', b'<template>STUDENT_MASTER V1</template>')
        for i in (2, 3):
            archive.writestr(f'xl/worksheets/sheet{i}.xml', '''<worksheet
              xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <dimension ref="A1:H99"/><sheetData><row r="2"><c r="A2" t="inlineStr"><is><t>旧示例</t></is></c></row>
              </sheetData></worksheet>''')
        archive.writestr('[Content_Types].xml', b'<Types/>')
    return buffer.getvalue()


class HelpersTests(unittest.TestCase):
    def setUp(self):
        e2e.SHARED.clear()
        e2e.CREATED['classes'].clear()
        e2e.CREATED['public_courses'].clear()
        e2e.SECRETS.clear()

    def test_scope_workbook_preserves_template_and_encodes_rows(self):
        rows = [['e2e1001', '本院<&>', '0', 'E2EMAJOR1', '2026', '', '', '0'],
                ['e2e1002', '外院学生', '0', 'E2EMAJOR2', '2026', '', '', '0']]
        for absolute in (False, True):
            source = template(absolute)
            result = e2e.student_workbook(source, rows)
            with zipfile.ZipFile(io.BytesIO(source)) as old, zipfile.ZipFile(io.BytesIO(result)) as new:
                self.assertEqual(set(old.namelist()), set(new.namelist()))
                self.assertEqual(new.read('xl/worksheets/sheet1.xml'), old.read('xl/worksheets/sheet1.xml'))
                self.assertEqual(new.read('[Content_Types].xml'), old.read('[Content_Types].xml'))
                xml = ET.fromstring(new.read('xl/worksheets/sheet2.xml'))
                records = [[cell.find('s:is/s:t', e2e.NS).text or '' for cell in row]
                           for row in xml.findall('s:sheetData/s:row', e2e.NS)]
                self.assertEqual(records[1:], rows)
                self.assertEqual(records[0][0], '学号*')
                relation = ET.fromstring(new.read('xl/worksheets/sheet3.xml'))
                self.assertEqual(len(relation.findall('s:sheetData/s:row', e2e.NS)), 1)

    def test_foundation_keeps_college_account_and_major_class_hierarchy(self):
        class Client:
            admin = 'token'
            calls = []

            def send(self, *args, **kwargs):
                return 200, 'application/json', json.dumps({'code': 0}).encode()

            def ok(self, method, path, token=None, params=None, body=None):
                self.calls.append((method, path, body))
                if path == '/system/dept/list-all-simple':
                    return [{'id': 10, 'parentId': 0}]
                if path == '/system/dept/create':
                    return 20 if body['deptType'] == 'COLLEGE' else 30
                if path == '/capacity/indicator/list':
                    return [{'id': 40, 'indicatorLevel': 1, 'status': 0}]
                if path == '/edu/class/create':
                    return 50
                raise AssertionError(path)

        client = Client()
        x = e2e.Ctx18(client)
        self.assertEqual(x.foundation(), (30, 40))
        y = e2e.Ctx18(client)
        self.assertEqual(y.foundation(), (30, 40))
        y.clazz()
        depts = [body for _, path, body in client.calls if path == '/system/dept/create']
        self.assertEqual([(r['deptType'], r['parentId']) for r in depts], [('COLLEGE', 10), ('MAJOR', 20)])
        self.assertEqual(e2e.SHARED['dept'], 20)
        self.assertEqual(client.calls[-1][2]['deptId'], 30)

    def test_unlock_contract_uses_levels_and_level(self):
        class Client:
            def ok(self, *args):
                return {'levels': [{'level': 1, 'unlocked': True}, {'level': 2, 'unlocked': False,
                                    'reasonText': '初级未达标'}], 'archived': False}
        self.assertEqual(e2e.unlock_row(Client(), 'student', 1, 2)['reasonText'], '初级未达标')

    def test_passwords_redacted_from_failure_text(self):
        x = e2e.Ctx18(None)
        password = x.password()
        self.assertNotIn(password, e2e.redact('失败：' + password))

    def test_dry_run_all_and_repeated_only_need_no_credentials_or_network(self):
        env = {k: v for k, v in os.environ.items() if not k.startswith('NUS_E2E_')}
        # 远程 URL 也不被 dry-run 使用，防止只列路径时意外登录。
        env['NUS_E2E_BASE_URL'] = 'http://remote.invalid:48080'
        all_rows = subprocess.run([sys.executable, str(SCRIPT), '--dry-run'], env=env,
                                  text=True, capture_output=True)
        self.assertEqual(all_rows.returncode, 0, all_rows.stderr)
        self.assertEqual(len(all_rows.stdout.splitlines()), 7)
        selected = subprocess.run([sys.executable, str(SCRIPT), '--dry-run', '--only', '能力口径',
                                   '--only', '学院范围'], env=env, text=True, capture_output=True)
        self.assertEqual(selected.returncode, 0, selected.stderr)
        self.assertEqual([line.split(':', 1)[0] for line in selected.stdout.splitlines()], ['能力口径', '学院范围'])
        invalid = subprocess.run([sys.executable, str(SCRIPT), '--dry-run', '--only', '不存在'], env=env,
                                 text=True, capture_output=True)
        self.assertEqual(invalid.returncode, 2)
        live = subprocess.run([sys.executable, str(SCRIPT)], env=env, text=True, capture_output=True)
        self.assertEqual(live.returncode, 2)
        self.assertIn('配置错误', live.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
