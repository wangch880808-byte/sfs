#!/usr/bin/env python3
"""V1.0.1.8 本机 HTTP 验收；仅用标准库，复用 v1015/v1017 框架。

迁移后先按 README 用本机 redis-cli 清除 permission_menu_ids:*、menu_role_ids:*、
user_role_ids:*、role:*、notify_template:*，再重新登录。脚本不读取或打印 Redis 口令。
学院场景必须先由负责人合并 S6 授权和后端范围校验。
"""
import argparse
import datetime as dt
import io
import json
import os
from pathlib import Path
import sys
import time
import traceback
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from e2e_v1015 import PREFIX, list_items, local_url, ready_student, require, student_tasks  # noqa: E402
from e2e_v1017 import (Client17, Ctx, CREATED, SHARED, SEMESTER_TIMEOUT, FORBIDDEN,  # noqa: E402
                       SETUP_PATHS, at, config_int, confirm_import, notices, submit, wait_for)

PATHS = {
    '教师绑定班课': ['/edu/class-course/bind', '/edu/class-course/unbind', '/edu/class-course/restore',
               '/edu/course-manage/detail', '/capacity/course/audit-public', '/capacity/course/get'],
    '课程任务数': ['/edu/course-manage/detail', '/edu/course-manage/class-detail', '/system/role/simple-list',
              '/system/user/create'],
    '能力口径': ['/edu/homework/get', '/edu/student/homework/submit', '/edu/homework-record/correct',
             '/edu/student/capacity/analysis', '/edu/student/capacity/report/basic'],
    '看板待审批': ['/system/role/simple-list', '/system/user/create', '/capacity/course/get',
              '/capacity/course/delete', '/edu/statistics/admin/dashboard'],
    '学生任务锁定': ['/edu/student/homework/list', '/edu/student/course/unlock-status',
               '/edu/student/homework/submit', '/edu/homework-record/correct'],
    '通知文案': ['/infra/config/get-value-by-key', '/infra/job/page', '/infra/job/trigger',
             '/edu/student/homework/submit', '/edu/homework-record/correct', '/edu/homework-record/redo',
             '/edu/student/homework/update', '/system/notify-message/my-page'],
    '学院范围': ['/system/role/simple-list', '/system/user/create', '/system/dept/list', '/edu/class/page',
             '/edu/class/get', '/edu/class/update', '/system/user/page', '/system/user/update',
             '/system/user/get',
             '/edu/class/student/add', '/edu/class/student/list-assigned',
             '/edu/data-exchange/templates/STUDENT_MASTER', '/edu/data-exchange/imports/STUDENT_MASTER/preview',
             '/edu/data-exchange/imports/{batchNo}/items', '/edu/data-exchange/imports/{batchNo}/confirm',
             '/edu/data-exchange/imports/{batchNo}',
             '/capacity/course/page', '/edu/homework/page', '/edu/statistics/admin/dashboard'],
}
SECRETS = []


class Ctx18(Ctx):
    def password(self):
        value = super().password()
        SECRETS.append(value)
        return value

    def foundation(self):
        college, indicator = super().foundation()
        if 'major18' not in SHARED:
            SHARED['major18'] = self.c.ok('POST', '/system/dept/create', body={
                'name': self.name('验收专业'), 'deptCode': self.name('E2EMAJOR'),
                'parentId': college, 'sort': 999, 'status': 0, 'deptType': 'MAJOR'})
        # Ctx.foundation 仍保存学院节点；调用者拿到专业节点，班级和师生挂在专业下。
        return SHARED['major18'], indicator


def staff(c, x, code, dept):
    roles = {r['code']: r['id'] for r in c.ok('GET', '/system/role/simple-list')}
    require(code in roles, f'基线缺少角色 {code}')
    user = {'username': x.username('a'), 'password': x.password(), 'nickname': x.name('教务')}
    user['id'] = c.ok('POST', '/system/user/create', body={**user, 'deptId': dept, 'roleIds': [roles[code]]})
    return user, x.login(user)


def bindings(c, class_id):
    return c.ok('GET', '/edu/class-course/list-by-class', params={'classId': class_id, 'includeArchived': 'true'})


def bind_body(x, class_id, course_id):
    start = dt.date.today() - dt.timedelta(days=1)
    return {'classIds': [class_id], 'courseId': course_id, 'semesterName': x.semester,
            'semesterStartDate': start.isoformat(), 'semesterEndDate': (start + dt.timedelta(days=120)).isoformat()}


def long_json(c, method, path, body):
    status, _, raw = c.send(method, path, c.admin, data=json.dumps(body).encode(),
                            content_type='application/json', timeout=SEMESTER_TIMEOUT)
    payload = json.loads(raw or b'{}')
    require(status == 200 and payload.get('code') == 0, f'{path} 失败：HTTP {status}，code={payload.get("code")}')
    return payload.get('data')


def scenario_bind(c):
    x = Ctx18(c)
    class_id = x.clazz()
    _, indicator = x.foundation()
    owner, team, other = [x.user('teacher') for _ in range(3)]
    x.teacher_token = x.login(owner)
    team_token, other_token = x.login(team), x.login(other)
    own = x.multipart_course({'indicatorId': indicator, 'isPublicCourse': 1, 'courseName': x.name('团队课程'),
                              'teacherId': owner['id'], 'teachAssistantIds': str(team['id']), 'status': 0}, c.admin)
    c.ok('POST', '/edu/class-course/bind', x.teacher_token, body=bind_body(x, class_id, own))
    binding = next(r['id'] for r in bindings(c, class_id) if r['courseId'] == own)
    for token in (x.teacher_token, team_token):
        c.ok('DELETE', '/edu/class-course/unbind', token, {'id': binding})
        require(next(r for r in bindings(c, class_id) if r['id'] == binding)['status'] == 1, '自己的班课未归档')
        c.ok('PUT', '/edu/class-course/restore', token, {'id': binding})
        require(next(r for r in bindings(c, class_id) if r['id'] == binding)['status'] == 0, '自己的班课未恢复')
    team_class = x.clazz()
    c.ok('POST', '/edu/class-course/bind', team_token, body=bind_body(x, team_class, own))
    require(any(r['courseId'] == own for r in bindings(c, team_class)), '团队成员未能绑定班级')
    foreign = x.course(other['id'])
    x.teacher_token = other_token
    public = x.course(other['id'], public=True)
    long_json(c, 'POST', '/capacity/course/audit-public', {'id': public, 'approved': True, 'remark': '本机验收'})
    require(c.ok('GET', '/capacity/course/get', params={'id': public})['publicAuditStatus'] == 2, '公共课未上线')
    x.teacher_token = x.login(owner)
    # V1.0.1.10 起公共课的“添加班级”就是认领，任何教师都可以（见 e2e_v10110.py），这里只验证专属课程
    for course_id in (foreign,):
        rows_before = bindings(c, class_id)
        c.denied('POST', '/edu/class-course/bind', x.teacher_token,
                 body=bind_body(x, class_id, course_id), code=FORBIDDEN)
        require(bindings(c, class_id) == rows_before, '越权绑定改变了班课')
        foreign_binding = x.bind(class_id, course_id)
        c.denied('DELETE', '/edu/class-course/unbind', x.teacher_token, {'id': foreign_binding}, code=FORBIDDEN)
        require(next(r for r in bindings(c, class_id) if r['id'] == foreign_binding)['status'] == 0,
                '越权归档改变了班课')
        c.ok('DELETE', '/edu/class-course/unbind', params={'id': foreign_binding})
        c.denied('PUT', '/edu/class-course/restore', x.teacher_token, {'id': foreign_binding}, code=FORBIDDEN)
        require(next(r for r in bindings(c, class_id) if r['id'] == foreign_binding)['status'] == 1,
                '越权恢复改变了班课')
    unrelated_class = x.clazz()
    x.bind(unrelated_class, foreign)
    # 已上线的公共课会自动关联新班级，任何教师都可凭此读取该班级；先归档这条自动关联，只留无关的专属课程
    for row in bindings(c, unrelated_class):
        if row['courseId'] == public and row['status'] == 0:
            c.ok('DELETE', '/edu/class-course/unbind', params={'id': row['id']})
    c.denied('GET', '/edu/class-course/list-by-class', x.teacher_token,
             {'classId': unrelated_class}, code=FORBIDDEN)
    require(c.ok('GET', '/edu/course-manage/detail', x.teacher_token, {'courseId': own})['canManage'] is True,
            '负责人 canManage 错误')
    require(c.ok('GET', '/edu/course-manage/detail', x.teacher_token, {'courseId': public})['canManage'] is False,
            '无关教师公共课 canManage 错误')


def scenario_counts(c):
    x = Ctx18(c)
    class_id = x.clazz()
    dept, indicator = x.foundation()
    owner, team = x.user('teacher'), x.user('teacher')
    x.teacher_token, team_token = x.login(owner), x.login(team)
    _, school = staff(c, x, 'school_admin', dept)
    course_id = x.multipart_course({'indicatorId': indicator, 'isPublicCourse': 1,
                                    'courseName': x.name('任务计数课程'), 'teacherId': owner['id'],
                                    'teachAssistantIds': str(team['id']), 'status': 0}, c.admin)
    binding = x.bind(class_id, course_id)
    expected = {x.task(class_id, course_id, token=t)[0] for t in (x.teacher_token, team_token, c.admin)}
    for role, token in (('管理员', c.admin), ('负责人', x.teacher_token), ('团队成员', team_token), ('学校教务', school)):
        detail = c.ok('GET', '/edu/course-manage/detail', token, {'courseId': course_id})
        require({r['homeworkId'] for r in detail['tasks']} == expected, f'{role} 课程任务数或集合不一致')
        detail = c.ok('GET', '/edu/course-manage/class-detail', token, {'classCourseId': binding})
        require({r['homeworkId'] for r in detail['tasks']} == expected, f'{role} 班级任务数或集合不一致')


def scenario_capacity(c):
    x = Ctx18(c)
    class_id, _, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    _, indicator = x.foundation()
    indicated, _ = x.task(class_id, course_id, indicatorFirstId=indicator)
    plain, _ = x.task(class_id, course_id, indicatorFirstId=None, indicatorSecondId=None)
    require(c.ok('GET', '/edu/homework/get', x.teacher_token, {'id': plain}).get('indicatorFirstId') is None,
            '未挂指标任务造数失败')
    for homework_id, score in ((indicated, 40), (plain, 80)):
        record = submit(c, token, homework_id)
        c.ok('PUT', '/edu/homework-record/correct', x.teacher_token, body={'id': record, 'score': score, 'comment': '口径验收'})
    analysis = c.ok('GET', '/edu/student/capacity/analysis', token)
    report = c.ok('GET', '/edu/student/capacity/report/basic', token)
    for field, value in {'totalTasks': 2, 'submittedTasks': 2, 'pendingReviewTasks': 0,
                         'evaluatedTasks': 2, 'qualifiedTasks': 1}.items():
        require(analysis.get(field) == report.get(field) == value, f'两页 {field} 应一致且为 {value}')
    require(analysis.get('unindicatedEvaluatedCount') == 1, '未关联指标已评定数应为 1')
    require(report.get('qualifiedRate') == 50, '报告任务达标率应统计全部任务')
    require(analysis.get('overallRate') == 0 and analysis.get('qualified') is False
            and report.get('certificateEligible') is False, '能力画像与获证判断应只计挂指标的不达标任务')
    rates = [r for r in analysis['indicators'] if r['indicatorFirstId'] == indicator]
    require(len(rates) == 1 and rates[0]['evaluatedTasks'] == 1 and rates[0]['qualifiedTasks'] == 0,
            '指标画像混入无指标任务')


def scenario_dashboard(c):
    x = Ctx18(c)
    dept, _ = x.foundation()
    _, school = staff(c, x, 'school_admin', dept)
    dashboard = lambda: c.ok('GET', '/edu/statistics/admin/dashboard', school)
    require(dashboard().get('pendingPublicAuditCount') == 0, '一次性测试库须没有已有待审批公共课')
    teacher = x.user('teacher')
    x.teacher_token = x.login(teacher)
    pending = x.course(teacher['id'], public=True)
    require(c.ok('GET', '/capacity/course/get', params={'id': pending})['publicAuditStatus'] == 1,
            '课程未处于待审批状态')
    require(dashboard().get('pendingPublicAuditCount') == 1, '学校看板待审批公共课应为 1')
    c.ok('DELETE', '/capacity/course/delete', params={'id': pending})
    require(dashboard().get('pendingPublicAuditCount') == 1, '归档待审批课程不应从看板计数中消失')


def unlock_row(c, token, course_id, level):
    status = c.ok('GET', '/edu/student/course/unlock-status', token, {'courseId': course_id})
    require(isinstance(status, dict) and isinstance(status.get('levels'), list), 'unlock-status 应返回 levels')
    return next(r for r in status['levels'] if r['level'] == level)


def scenario_locked(c):
    x = Ctx18(c)
    class_id, _, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    first, _ = x.task(class_id, course_id, homeworkLevel=1)
    second, _ = x.task(class_id, course_id, homeworkLevel=2)
    record = submit(c, token, first)
    rows = {r['homeworkId']: r for r in student_tasks(c, token, course_id)}
    require(first in rows and second in rows, '任务列表漏掉未解锁任务')
    reason = unlock_row(c, token, course_id, 2)
    require(rows[second].get('locked') is True and rows[second].get('lockReason')
            and rows[second]['lockReason'] == reason['reasonText'] and reason['unlocked'] is False,
            'locked 或 lockReason 与解锁接口不一致')
    require(rows[first].get('locked') is False, '初级任务不应锁定')
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token, body={'id': record, 'score': 80, 'comment': '解锁'})
    rows = {r['homeworkId']: r for r in student_tasks(c, token, course_id)}
    require(rows[second].get('locked') is False and not rows[second].get('lockReason'), '达标后任务仍锁定或保留原因')


def scenario_notification(c):
    require(config_int(c, 'edu.notify.teacher-digest-minutes', 0) > 0, '教师新提交汇总须启用（配置大于 0）')
    jobs = list_items(c.ok('GET', '/infra/job/page', params={'pageNo': 1, 'pageSize': 100, 'name': '教师新提交汇总'}))
    job = next((r for r in jobs if r.get('handlerName') == 'eduHomeworkTeacherDigestJob'), None)
    require(job is not None, '缺少教师新提交汇总任务')
    trigger = lambda: c.ok('PUT', '/infra/job/trigger', params={'id': job['id']})
    # 预先初始化扫描游标，之后的新提交即使跨过汇总窗口也不会遗漏。
    trigger()
    x = Ctx18(c)
    class_id, _, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    plain, plain_body = x.task(class_id, course_id)
    redo, redo_body = x.task(class_id, course_id)
    submit(c, token, plain)
    record = submit(c, token, redo)
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token, body={'id': record, 'score': 40, 'comment': '重交测试'})
    c.ok('PUT', '/edu/homework-record/redo', x.teacher_token, body={'id': record, 'redoDeadline': at(days=7)})
    c.ok('PUT', '/edu/student/homework/update', token, body={'id': record, 'content': '重交'})
    code = 'edu.teacher.homework.submitted'

    def probe():
        trigger()
        normal = notices(c, x.teacher_token, code, homeworkName=plain_body['homeworkName'])
        resubmitted = notices(c, x.teacher_token, code, homeworkName=redo_body['homeworkName'])
        return (normal, resubmitted) if normal and resubmitted else None

    rows = wait_for(probe, 300, interval=10)  # 汇总有 2 分钟宽限与分钟取整，不改线上配置或系统时间
    require(rows, '5 分钟内未收到真实汇总通知；检查 Quartz 与调度日志')
    for group, expected_note in zip(rows, ('', '（含 1 份重交）')):
        require(len(group) == 1, '同一任务重复收到汇总通知')
        row = group[0]
        params = row.get('templateParams') or {}
        require(params.get('resubmitNote') == expected_note and int(params.get('count', 0)) == 1,
                '汇总通知参数不符合 F8')
        content = row.get('templateContent')
        require(isinstance(content, str) and content, '通知未返回渲染后的 templateContent')
        expected = f'「{params["homeworkName"]}」有 1 份新提交{expected_note}待批改。'
        require(content == expected and '含 0 份重交' not in content and '{resubmitNote}' not in content,
                '真实汇总通知文案错误')
    return '首次提交与重交真实通知均通过'


NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
      'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}


def student_workbook(template, rows):
    """在下载的模板里填两行，保留模板信息；只用标准库，不落盘或输出账号口令。"""
    with zipfile.ZipFile(io.BytesIO(template)) as source:
        workbook = ET.fromstring(source.read('xl/workbook.xml'))
        rels = ET.fromstring(source.read('xl/_rels/workbook.xml.rels'))
        targets = {r.attrib['Id']: r.attrib['Target'] for r in rels}
        replacements = {}
        headers = {'学生档案': ['学号*', '姓名*', '性别', '班级*', '手机号', '邮箱', '状态'],
                   '班级关系': ['学号*', '班级编号*', '备注']}
        for sheet in workbook.findall('s:sheets/s:sheet', NS):
            name = sheet.attrib['name']
            if name not in headers:
                continue
            target = targets[sheet.attrib['{' + NS['r'] + '}id']]
            path = target.lstrip('/') if target.startswith('/') else 'xl/' + target
            xml = ET.fromstring(source.read(path))
            data = xml.find('s:sheetData', NS)
            require(data is not None, '模板缺少 sheetData')
            data.clear()
            records = [headers[name]] + (rows if name == '学生档案' else [])
            for number, values in enumerate(records, 1):
                row = ET.SubElement(data, '{' + NS['s'] + '}row', r=str(number))
                for index, value in enumerate(values):
                    cell = ET.SubElement(row, '{' + NS['s'] + '}c', r=chr(65 + index) + str(number), t='inlineStr')
                    inline = ET.SubElement(cell, '{' + NS['s'] + '}is')
                    ET.SubElement(inline, '{' + NS['s'] + '}t').text = str(value)
            dimension = xml.find('s:dimension', NS)
            if dimension is not None:
                dimension.set('ref', f'A1:{chr(64 + len(headers[name]))}{len(records)}')
            replacements[path] = ET.tostring(xml, encoding='utf-8', xml_declaration=True)
        require(len(replacements) == 2, '模板缺少学生档案或班级关系')
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as target:
            for item in source.infolist():
                target.writestr(item, replacements.get(item.filename, source.read(item.filename)))
        return output.getvalue()


def scenario_college(c):
    x = Ctx18(c)
    x.foundation()
    roots = c.ok('GET', '/system/dept/list-all-simple')
    root = next(d for d in roots if not d.get('parentId'))['id']

    def organization(kind, parent):
        code = x.name('E2E' + kind)
        return c.ok('POST', '/system/dept/create', body={'name': x.name('范围组织'), 'deptCode': code,
                    'deptType': kind, 'parentId': parent, 'sort': 999, 'status': 0}), code

    college, _ = organization('COLLEGE', root)
    major, major_code = organization('MAJOR', college)
    outside, _ = organization('COLLEGE', root)
    outside_major, outside_code = organization('MAJOR', outside)
    _, token = staff(c, x, 'college_admin', college)  # 管理员挂学院，班级与师生挂专业
    _, school = staff(c, x, 'school_admin', root)

    def clazz(dept, who):
        body = {'className': x.name('范围班'), 'gradeYear': dt.date.today().year, 'deptId': dept,
                'educationLevel': 2, 'schoolingYear': 4, 'status': 0}
        item = c.ok('POST', '/edu/class/create', who, body=body)
        CREATED['classes'].append(item)
        return item, body

    def user(kind, dept, who, class_id=None):
        body = {'username': x.username('u'), 'nickname': x.name('范围师生'), 'password': x.password(), 'deptId': dept}
        if kind == 'student':
            body['profile'] = {'gradeYear': dt.date.today().year, 'classIds': [class_id] if class_id else []}
        return c.ok('POST', '/system/user/create-' + kind, who, body=body), body

    local_class, local_body = clazz(major, token)
    other_class, other_body = clazz(outside_major, c.admin)
    own_student, own_user = user('student', major, token, local_class)
    own_teacher, teacher_user = user('teacher', major, token)
    other_student, other_user = user('student', outside_major, c.admin, other_class)
    other_teacher, other_teacher_user = user('teacher', outside_major, c.admin)
    depts = c.ok('GET', '/system/dept/list', token)
    require({r['id'] for r in depts} == {college, major},
            '学院管理员组织列表未限定本学院及以下')
    classes = list_items(c.ok('GET', '/edu/class/page', token, {'pageNo': 1, 'pageSize': 100, 'className': '范围班' + x.suffix}))
    require(local_class in {r['id'] for r in classes} and other_class not in {r['id'] for r in classes},
            '班级列表跨学院泄露或缺少本院数据')
    for user_type, own, foreign in ((1, own_teacher, other_teacher), (2, own_student, other_student)):
        users = list_items(c.ok('GET', '/system/user/page', token,
                               {'pageNo': 1, 'pageSize': 100, 'userType': user_type, 'nickname': '范围师生' + x.suffix}))
        require(own in {r['id'] for r in users} and foreign not in {r['id'] for r in users}, '师生查询范围错误')
    c.ok('PUT', '/edu/class/update', token, body={**local_body, 'id': local_class, 'className': x.name('本院修改')})
    c.ok('PUT', '/system/user/update', token, body={k: v for k, v in {**own_user, 'id': own_student,
                                                                  'nickname': x.name('本院学生修改')}.items() if k != 'password'})
    c.ok('PUT', '/system/user/update', token,
         body={k: v for k, v in {**teacher_user, 'id': own_teacher}.items() if k != 'password'})
    c.denied('POST', '/edu/class/create', token, body={**other_body, 'className': x.name('越权班')}, code=FORBIDDEN)
    c.denied('POST', '/system/user/create-student', token,
             body={**other_user, 'username': x.username('bad')}, code=FORBIDDEN)
    c.denied('POST', '/system/user/create-teacher', token,
             body={'username': x.username('bad'), 'nickname': x.name('越权教师'), 'password': x.password(),
                   'deptId': outside_major}, code=FORBIDDEN)
    c.denied('PUT', '/edu/class/update', token, body={**other_body, 'id': other_class, 'className': x.name('越权修改')},
             code=FORBIDDEN)
    c.denied('PUT', '/edu/class/update', token, body={**local_body, 'id': local_class, 'deptId': outside_major},
             code=FORBIDDEN)
    c.denied('PUT', '/system/user/update', token,
             body={k: v for k, v in {**other_user, 'id': other_student, 'nickname': x.name('越权学生')}.items()
                   if k != 'password'}, code=FORBIDDEN)
    c.denied('PUT', '/system/user/update', token,
             body={k: v for k, v in {**other_teacher_user, 'id': other_teacher,
                                    'nickname': x.name('越权教师')}.items() if k != 'password'}, code=FORBIDDEN)
    c.denied('PUT', '/system/user/update', token,
             body={k: v for k, v in {**own_user, 'id': own_student, 'deptId': outside_major}.items()
                   if k != 'password'}, code=FORBIDDEN)
    c.denied('POST', '/edu/class/student/add', token,
             body={'classId': local_class, 'studentIds': [other_student]}, code=FORBIDDEN)
    c.denied('POST', '/edu/class/student/add', token,
             body={'classId': other_class, 'studentIds': [own_student]}, code=FORBIDDEN)
    # 越权 UPDATE 不能静默影响 0 行并报成功，拒绝后目标行也必须保留。
    require(c.ok('GET', '/edu/class/get', params={'id': other_class})['className'] == other_body['className'],
            '越权更新改变了外院班级')
    require(c.ok('GET', '/system/user/get', params={'id': other_student})['nickname'] == other_user['nickname'],
            '越权更新改变了外院学生')
    require(c.ok('GET', '/system/user/get', params={'id': other_teacher})['nickname'] == other_teacher_user['nickname'],
            '越权更新改变了外院教师')
    # 停用/恢复（教务账号归档）：本院学生可以，外院学生被拒且不受影响
    c.ok('DELETE', '/edu/user/delete', token, {'id': own_student})
    c.ok('PUT', '/edu/user/restore', token, {'id': own_student})
    c.denied('DELETE', '/edu/user/delete', token, {'id': other_student}, code=FORBIDDEN)
    require(c.ok('GET', '/system/user/get', params={'id': other_student})['status'] == 0, '越权停用改变了外院学生')
    assigned = c.ok('GET', '/edu/class/student/list-assigned', params={'classId': local_class})
    require(other_student not in {r['id'] for r in assigned}, '越权添加把外院学生加入了本院班级')
    template = c.download('/edu/data-exchange/templates/STUDENT_MASTER', token, {})
    inside_import, outside_import = x.username('im'), x.username('im')
    # 学生档案按「班级」导入：专业、年级由班级补全，并自动加入该班级（有效学生多一条「学生档案（入班）」预检行）
    rows = [[inside_import, '本院导入', '0', f"{local_body['className']} [{local_class}]", '', '', '0'],
            [outside_import, '外院导入', '0', f"{other_body['className']} [{other_class}]", '', '', '0']]
    batch = c.form('/edu/data-exchange/imports/STUDENT_MASTER/preview', token,
                   {'optionsJson': '{}'}, 'scope.xlsx', student_workbook(template, rows))
    # 本院：学生 + 自动入班两行；外院：只有学生行被跳过（跳过的学生不再生成入班行）
    require(batch['totalCount'] == 3 and batch['insertedCount'] == 2 and batch['skippedCount'] == 1
            and batch['invalidCount'] == 0, '本院学生与入班应可导入，外院学生应跳过而非报错')
    items = list_items(c.ok('GET', f'/edu/data-exchange/imports/{batch["batchNo"]}/items', token,
                           {'pageNo': 1, 'pageSize': 100}))
    skipped = [r for r in items if r['action'] == 'SKIP']
    require(len(skipped) == 1 and all(outside_import in json.dumps(r, ensure_ascii=False)
                                      and '不在本学院' in json.dumps(r, ensure_ascii=False) for r in skipped),
            '外院预检未标明跳过原因')
    done = confirm_import(c, batch['batchNo'], token, {'initialPassword': x.password()})
    require(done['status'] == 'SUCCESS' and done['insertedCount'] == 2 and done['updatedCount'] == 0,
            '确认导入不应写入外院行')
    imported = c.ok('GET', '/system/user/page', params={'pageNo': 1, 'pageSize': 10, 'username': inside_import})
    imported_id = next(r['id'] for r in list_items(imported) if r['username'] == inside_import)
    require(imported_id in {r['id'] for r in c.ok('GET', '/edu/class/student/list-assigned',
                                                  params={'classId': local_class})},
            '按班级导入的学生没有自动加入该班级')
    for username, count in ((inside_import, 1), (outside_import, 0)):
        page = c.ok('GET', '/system/user/page', params={'pageNo': 1, 'pageSize': 100, 'username': username})
        require(len([r for r in list_items(page) if r['username'] == username]) == count, '实际导入范围错误')
    for path in ('/capacity/course/page', '/edu/homework/page', '/edu/statistics/admin/dashboard'):
        c.denied('GET', path, token, {'pageNo': 1, 'pageSize': 10}, code=FORBIDDEN)
    for who in (c.admin, school):
        rows = c.ok('GET', '/system/dept/list', who)
        require({college, major, outside, outside_major} <= {r['id'] for r in rows}, '全局管理员组织被学院范围过滤')
        rows = list_items(c.ok('GET', '/edu/class/page', who,
                              {'pageNo': 1, 'pageSize': 100, 'className': '范围班' + x.suffix}))
        # 本院班已改名；按 ID 再直接读取确认全局角色两边都能访问。
        for class_id in (local_class, other_class):
            require(c.ok('GET', '/edu/class/get', who, {'id': class_id})['id'] == class_id,
                    '全局管理员班级被范围过滤')
        for user_id in (own_student, other_student, own_teacher, other_teacher):
            require(c.ok('GET', '/system/user/get', who, {'id': user_id})['id'] == user_id,
                    '全局管理员师生被范围过滤')


SCENARIOS = {'教师绑定班课': scenario_bind, '课程任务数': scenario_counts, '能力口径': scenario_capacity,
             '看板待审批': scenario_dashboard, '学生任务锁定': scenario_locked,
             '通知文案': scenario_notification, '学院范围': scenario_college}


def redact(value):
    text = str(value)
    for secret in SECRETS:
        text = text.replace(secret, '[口令已隐藏]')
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=SCENARIOS, action='append', help='只执行指定场景，可重复')
    parser.add_argument('--dry-run', action='store_true', help='只列场景和接口路径，不发请求')
    parser.add_argument('--debug', action='store_true', help='失败时打印调用栈（口令隐藏）')
    parser.add_argument('--keep-data', action='store_true', help='结束后不归档本次新建的班级与公共课')
    args = parser.parse_args()
    selected = args.only or list(SCENARIOS)
    if args.dry_run:
        for name in selected:
            print(f'{name}: ' + ', '.join(PREFIX + p for p in dict.fromkeys(SETUP_PATHS + PATHS[name])))
        return 0
    try:
        base = local_url(os.environ.get('NUS_E2E_BASE_URL'))
        username = os.environ['NUS_E2E_ADMIN_USERNAME']
        password = Path(os.environ['NUS_E2E_ADMIN_PASSWORD_FILE']).read_text(encoding='utf-8').rstrip('\r\n')
        require(bool(username and password), '管理员用户名或口令为空')
        SECRETS.append(password)
    except (KeyError, OSError, ValueError, AssertionError):
        print('配置错误：缺少或不合法的本机 URL、管理员用户名或口令文件', file=sys.stderr)
        return 2
    client = Client17(base)
    try:
        client.admin = client.login(username, password)['accessToken']
    except Exception:
        print('管理员登录失败（响应与凭据已隐藏）', file=sys.stderr)
        return 2
    print('INFO 迁移后须已清 Redis 权限与通知模板缓存并重新登录，命令见 README.md', flush=True)
    passed = failed = 0
    for name in selected:
        started = time.monotonic()
        try:
            note = SCENARIOS[name](client)
            extra = f'（{note}）' if note else ''
            print(f'PASS {name}：关键接口和业务状态断言通过{extra}，{time.monotonic() - started:.0f} 秒', flush=True)
            passed += 1
        except Exception as exc:
            reason = redact(exc) if isinstance(exc, AssertionError) else type(exc).__name__
            print(f'FAIL {name}：{reason}', flush=True)
            if args.debug:
                print(redact(traceback.format_exc()), file=sys.stderr)
            failed += 1
    if 'switch_seconds' in SHARED:
        level = 'WARN' if SHARED['switch_seconds'] > 12 else 'INFO'
        print(f'{level} 切换当前学期耗时 {SHARED["switch_seconds"]:.0f} 秒')
    if not args.keep_data:
        archived = [path for path, ids in (('/edu/class/delete', CREATED['classes']),
                                           ('/capacity/course/delete', CREATED['public_courses']))
                    for item in ids if client.call('DELETE', path, client.admin, {'id': item})[1].get('code') == 0]
        print(f'INFO 已归档本次新建的班级与公共课 {len(archived)} 个（--keep-data 可保留）')
    print(f'汇总：PASS {passed}，FAIL {failed}')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
