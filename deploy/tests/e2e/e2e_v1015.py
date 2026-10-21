#!/usr/bin/env python3
"""V1.0.1.5 本机 HTTP 端到端验收；仅用标准库。"""
import argparse
import datetime as dt
import json
import os
import re
import secrets
import string
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

PREFIX = '/admin-api'
PASSWORD_REQUIRED = 1002000009
LEVEL_LOCKED = 1050007001
DELETED = ('/system/auth/login-features', '/edu/statistics/student-list',
           '/edu/statistics/academic-year-list', '/edu/report/course/export',
           '/edu/ai-review/chat/teacher', '/edu/student/homework/ranking',
           '/edu/homework-record/save-draft', '/edu/homework-record/get-draft')
PATHS = {
    '匿名与下线接口': ['/system/user/profile/get', '/system/auth/social-login-config', *DELETED],
    '学生强制改密': ['/system/user/create-student', '/system/user/create-teacher', '/system/auth/login', '/system/auth/get-permission-info', '/edu/student/course/list', '/edu/student/profile/password', '/system/user/update-password', '/ai/chat/conversation/my-list'],
    '评分轨迹': ['/edu/homework/create', '/edu/student/homework/submit', '/edu/homework-record/get', '/edu/homework-record/correct', '/edu/homework-record/redo', '/edu/student/homework/update', '/edu/homework-record/history', '/edu/student/homework/history'],
    '归档': ['/edu/class/delete', '/edu/class/restore', '/capacity/course/delete', '/capacity/course/restore', '/edu/homework/delete', '/edu/homework/restore', '/edu/homework/remove-impact', '/edu/homework/remove', '/edu/class-course/unbind', '/edu/class-course/restore', '/edu/class/student/list-assigned', '/edu/student/course/list', '/edu/student/homework/list', '/edu/student/homework/submit', '/edu/student/homework/attachment/upload', '/edu/homework-record/correct', '/edu/statistics/total-pass-rate', '/edu/user/delete', '/edu/user/restore', '/system/user/get', '/edu/class-course/bind'],
    '公共课': ['/capacity/course/create', '/capacity/course/audit-public', '/capacity/course/get', '/edu/class-course/list-by-class', '/edu/class-course/claim', '/edu/homework/create', '/edu/student/homework/submit', '/edu/homework-record/correct', '/edu/class-course/unbind'],
    '留言': ['/edu/student/message/send', '/edu/message/student-list', '/edu/message/detail', '/edu/message/reply', '/edu/message/close', '/edu/message/reopen', '/edu/student/message/list', '/edu/student/message/detail'],
    '钉钉按配置显示': ['/system/auth/social-login-config', '/system/auth/social-auth-redirect', '/system/social-client/create', '/system/social-client/update', '/system/social-client/delete'],
    '统计口径': ['/edu/homework/statistics', '/edu/homework-record/page', '/edu/statistics/total-pass-rate', '/edu/student/capacity/report/basic', '/edu/class/student/add', '/edu/student/homework/list', '/edu/student/homework/submit', '/edu/homework-record/correct'],
    '解锁规则': ['/edu/student/course/unlock-status', '/edu/student/homework/get', '/edu/student/homework/submit', '/edu/student/homework/update', '/edu/homework-record/redo'],
    'AI 校准接口': ['/edu/ai-review/calibration', '/edu/ai-review/feedback', '/edu/student/homework/submit'],
}
SETUP_PATHS = ['/edu/semester/current', '/system/dept/list-all-simple', '/system/dept/create',
               '/capacity/indicator/list', '/capacity/indicator/create', '/edu/class/create',
               '/system/user/create-teacher', '/system/user/create-student', '/system/auth/login',
               '/capacity/course/create', '/edu/class-course/bind',
               '/edu/class-course/list-by-class', '/edu/student/profile/password', '/edu/homework/create']


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def future(days=30):
    return (dt.datetime.now() + dt.timedelta(days=days)).strftime('%Y-%m-%d %H:%M:%S')


def local_url(value):
    # 精确限制主机、端口和 URL 形状；urllib 不得经代理或跳转离开本机。
    match = re.fullmatch(r'http://(127\.0\.0\.1|localhost):([0-9]{1,5})', value or '')
    if not match or not 1 <= int(match.group(2)) <= 65535:
        raise ValueError('NUS_E2E_BASE_URL 必须是 http://127.0.0.1:<端口> 或 http://localhost:<端口>')
    # localhost 输入同样连接数字回环地址，避免依赖本机 hosts/DNS 映射。
    return f'http://127.0.0.1:{int(match.group(2))}'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise AssertionError('服务端返回跳转，已拒绝跟随')


class Client:
    def __init__(self, base):
        self.base = local_url(base)
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.admin = None

    def call(self, method, path, token=None, params=None, body=None, upload=False):
        require(path.startswith('/') and not path.startswith('//') and '?' not in path and '#' not in path,
                '非法接口路径')
        url = self.base + PREFIX + path
        if params:
            url += '?' + urllib.parse.urlencode(params, doseq=True)
        headers = {'Accept': 'application/json', 'Accept-Language': 'zh-CN'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        data = None
        if upload:
            boundary = 'e2e' + secrets.token_hex(12)
            file_part = b'--' + boundary.encode() + b'\r\nContent-Disposition: form-data; name="file"; filename="e2e.txt"\r\nContent-Type: text/plain\r\n\r\ne2e\r\n--' + boundary.encode() + b'--\r\n'
            data = file_part
            headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode()
            headers['Content-Type'] = 'application/json'
        if method not in ('GET', 'HEAD'):
            headers['X-Idempotency-Key'] = str(uuid.uuid4())
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(req, timeout=12)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            status = response.status
            raw = response.read(1024 * 1024)
        try:
            payload = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            payload = {}
        return status, payload

    def ok(self, method, path, token=None, params=None, body=None, upload=False):
        status, payload = self.call(method, path, self.admin if token is None else token,
                                    params, body, upload)
        require(status == 200 and payload.get('code') == 0,
                f'{path} 失败：HTTP {status}，code={payload.get("code")}')
        return payload.get('data')

    def denied(self, method, path, token=None, params=None, body=None, code=None, phrase=None, upload=False):
        status, payload = self.call(method, path, token, params, body, upload)
        require(status >= 400 or payload.get('code') not in (None, 0), f'{path} 意外成功')
        if code is not None:
            require(payload.get('code') == code, f'{path} 错误码不是 {code}')
        if phrase is not None:
            require(phrase in str(payload.get('msg', '')), f'{path} 错误原因不含指定文案')
        return payload

    def login(self, username, password):
        data = self.ok('POST', '/system/auth/login', token='',
                       body={'username': username, 'password': password})
        require(isinstance(data, dict) and data.get('accessToken'), '登录未返回令牌')
        return data


class Context:
    def __init__(self, client):
        self.c = client
        self.suffix = secrets.token_hex(4)
        self.count = 0
        self.dept = None
        self.indicator = None
        self.semester = f'E2E-{self.suffix}'
        self.semester_ready = False
        self.teacher_token = None

    def name(self, stem):
        self.count += 1
        return f'{stem}{self.suffix}{self.count}'

    def username(self, stem):
        return self.name('e2e' + stem)

    def password(self):
        return 'A1' + ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(10))

    def foundation(self):
        if not self.semester_ready:
            start = dt.date.today() - dt.timedelta(days=1)
            self.c.ok('PUT', '/edu/semester/current', body={
                'semesterName': self.semester, 'semesterStartDate': start.isoformat(),
                'semesterEndDate': (start + dt.timedelta(days=120)).isoformat()})
            self.semester_ready = True
        if self.dept is None:
            depts = self.c.ok('GET', '/system/dept/list-all-simple')
            require(depts, '无可用根组织')
            root = next((d for d in depts if not d.get('parentId')), depts[0])
            self.dept = self.c.ok('POST', '/system/dept/create', body={
                'name': self.name('验收学院'), 'parentId': root['id'], 'sort': 999, 'status': 0,
                'deptType': 'COLLEGE'})
        if self.indicator is None:
            indicators = self.c.ok('GET', '/capacity/indicator/list')
            self.indicator = next((i['id'] for i in indicators if i.get('indicatorLevel') == 1 and i.get('status') == 0), None)
            if self.indicator is None:
                self.indicator = self.c.ok('POST', '/capacity/indicator/create', body={
                    'indicatorName': self.name('验收指标'), 'indicatorLevel': 1,
                    'parentIndicatorId': 0, 'weight': 100, 'status': 0})
        return self.dept, self.indicator

    def clazz(self):
        dept, _ = self.foundation()
        return self.c.ok('POST', '/edu/class/create', body={
            'className': self.name('验收班'), 'gradeYear': dt.date.today().year,
            'deptId': dept, 'educationLevel': 2, 'schoolingYear': 4, 'status': 0})

    def user(self, kind, class_id=None):
        dept, _ = self.foundation()
        username, password = self.username('s' if kind == 'student' else 't'), self.password()
        nickname = self.name('学生' if kind == 'student' else '教师')
        body = {'username': username, 'nickname': nickname, 'password': password, 'deptId': dept}
        if kind == 'student':
            body['profile'] = {'gradeYear': dt.date.today().year, 'classIds': [class_id] if class_id else []}
        user_id = self.c.ok('POST', '/system/user/create-' + kind, body=body)
        return {'id': user_id, 'username': username, 'password': password, 'nickname': nickname}

    def course(self, teacher_id, public=False):
        _, indicator = self.foundation()
        course = {'indicatorId': indicator, 'isPublicCourse': 0 if public else 1,
                  'courseName': self.name('验收课程'), 'teacherId': teacher_id, 'status': 0}
        return self.multipart_course(course, self.teacher_token if public else self.c.admin)

    def multipart_course(self, course, token):
        boundary = 'e2e' + secrets.token_hex(12)
        blob = json.dumps(course, ensure_ascii=False).encode()
        data = b'--' + boundary.encode() + b'\r\nContent-Disposition: form-data; name="course"\r\nContent-Type: application/json\r\n\r\n' + blob + b'\r\n--' + boundary.encode() + b'--\r\n'
        headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
                   'X-Idempotency-Key': str(uuid.uuid4())}
        req = urllib.request.Request(self.c.base + PREFIX + '/capacity/course/create', data=data, headers=headers, method='POST')
        try:
            response = self.c.opener.open(req, timeout=12)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            status, raw = response.status, response.read(1024 * 1024)
        payload = json.loads(raw)
        require(status == 200 and payload.get('code') == 0,
                f'/capacity/course/create 失败：HTTP {status}，code={payload.get("code")}')
        return payload['data']

    def bind(self, class_id, course_id):
        start = dt.date.today() - dt.timedelta(days=1)
        end = start + dt.timedelta(days=120)
        self.c.ok('POST', '/edu/class-course/bind', body={
            'courseId': course_id, 'classIds': [class_id], 'semesterName': self.semester,
            'semesterStartDate': start.isoformat(), 'semesterEndDate': end.isoformat()})
        rows = self.c.ok('GET', '/edu/class-course/list-by-class', params={'classId': class_id, 'includeArchived': 'true'})
        matches = [r for r in rows if r['courseId'] == course_id and r['semesterName'] == self.semester]
        require(matches, '未找到班课绑定')
        return matches[-1]['id']

    def homework(self, class_id, course_id, level=1, pass_score=60, token=None):
        body = {'homeworkName': self.name('验收任务'), 'homeworkType': 1, 'homeworkLevel': level,
                'courseId': course_id, 'classId': class_id, 'semesterName': self.semester,
                'requirement': '本机验收', 'deadline': future(), 'autoGrade': 0,
                'submissionMode': 'text', 'aiReviewMode': 0, 'passScore': pass_score,
                'score': 100, 'status': 1}
        return self.c.ok('POST', '/edu/homework/create', token or self.teacher_token,
                         body=body), body

    def bundle(self, students=1):
        class_id = self.clazz()
        teacher = self.user('teacher')
        self.teacher_token = self.c.login(teacher['username'], teacher['password'])['accessToken']
        pupils = [self.user('student', class_id) for _ in range(students)]
        course_id = self.course(teacher['id'])
        binding = self.bind(class_id, course_id)
        return class_id, teacher, pupils, course_id, binding


def list_items(page):
    require(isinstance(page, dict) and isinstance(page.get('list'), list), '分页返回结构错误')
    return page['list']


def student_tasks(c, token, course_id):
    return list_items(c.ok('GET', '/edu/student/homework/list', token, {'courseId': course_id, 'pageNo': 1, 'pageSize': 100}))


def scenario_anonymous(c):
    c.denied('GET', '/system/user/profile/get')
    data = c.ok('GET', '/system/auth/social-login-config', token='')
    require(set(data) == {'dingtalkEnabled'}, '匿名配置暴露多余字段')
    for path in DELETED:
        status, payload = c.call('GET', path, c.admin)
        require(status == 404 or payload.get('code') == 404 or
                '不存在' in str(payload.get('msg', '')) or
                'Not Found' in str(payload.get('msg', '')),
                f'下线接口仍存在：{path}')


def scenario_password(c):
    x = Context(c)
    pupil = x.user('student')
    first = c.login(pupil['username'], pupil['password'])
    second = c.login(pupil['username'], pupil['password'])
    require(first.get('passwordChangeRequired') is True, '首次登录未要求改密')
    c.ok('GET', '/system/auth/get-permission-info', first['accessToken'])
    c.denied('GET', '/edu/student/course/list', first['accessToken'], code=PASSWORD_REQUIRED)
    new_password = x.password()
    c.ok('PUT', '/edu/student/profile/password', first['accessToken'], body={
        'oldPassword': pupil['password'], 'newPassword': new_password})
    c.denied('GET', '/system/user/profile/get', second['accessToken'])
    fresh = c.login(pupil['username'], new_password)
    require(fresh.get('passwordChangeRequired') is False, '改密后标志未清除')
    c.ok('GET', '/edu/student/course/list', fresh['accessToken'])
    # AI 对话接口按“AI 对话”菜单授权：学生没有该菜单，不能直接调用；超级管理员可以
    c.denied('GET', '/ai/chat/conversation/my-list', fresh['accessToken'], code=403)
    c.ok('GET', '/ai/chat/conversation/my-list')
    reset_password = x.password()
    c.ok('PUT', '/system/user/update-password', body={'id': pupil['id'], 'password': reset_password})
    reset = c.login(pupil['username'], reset_password)
    require(reset.get('passwordChangeRequired') is True, '管理员重置未恢复改密要求')
    c.denied('GET', '/edu/student/course/list', reset['accessToken'], code=PASSWORD_REQUIRED)
    teacher = x.user('teacher')
    require(c.login(teacher['username'], teacher['password']).get('passwordChangeRequired') is False,
            '新教师被要求改密')


def scenario_history(c):
    x = Context(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    homework_id, _ = x.homework(class_id, course_id)
    token = c.login(pupils[0]['username'], pupils[0]['password'])['accessToken']
    # 本场景先履行首次改密。
    changed = x.password()
    c.ok('PUT', '/edu/student/profile/password', token, body={'oldPassword': pupils[0]['password'], 'newPassword': changed})
    token = c.login(pupils[0]['username'], changed)['accessToken']
    record_id = c.ok('POST', '/edu/student/homework/submit', token, body={'homeworkId': homework_id, 'content': 'first'})
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
         body={'id': record_id, 'score': 85, 'comment': 'first score'})
    c.ok('PUT', '/edu/homework-record/redo', x.teacher_token,
         body={'id': record_id, 'redoDeadline': future(10)})
    record = c.ok('GET', '/edu/homework-record/get', x.teacher_token, {'id': record_id})
    require(record['status'] == 3 and record['score'] == 85, '打回未保留原成绩')
    c.denied('PUT', '/edu/homework-record/correct', x.teacher_token,
             body={'id': record_id, 'score': 90, 'comment': 'invalid'})
    c.ok('PUT', '/edu/student/homework/update', token, body={'id': record_id, 'content': 'second'})
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
         body={'id': record_id, 'score': 91, 'comment': 'second score'})
    teacher_history = c.ok('GET', '/edu/homework-record/history', x.teacher_token,
                           {'recordId': record_id})
    student_history = c.ok('GET', '/edu/student/homework/history', token, {'homeworkId': homework_id})
    events = ['SUBMIT', 'CORRECT', 'REDO', 'RESUBMIT', 'CORRECT']
    require([h['eventType'] for h in teacher_history] == events, '教师轨迹事件不完整')
    require([h['eventType'] for h in student_history] == events, '学生轨迹事件不完整')
    require([h.get('score') for h in teacher_history if h['eventType'] == 'CORRECT'] == [85, 91], '复评历史成绩错误')


def scenario_archive(c):
    x = Context(c)
    class_id, teacher, pupils, course_id, binding = x.bundle()
    homework_id, _ = x.homework(class_id, course_id)
    graded_id, _ = x.homework(class_id, course_id)
    pupil = pupils[0]
    token = c.login(pupil['username'], pupil['password'])['accessToken']
    changed = x.password()
    c.ok('PUT', '/edu/student/profile/password', token, body={'oldPassword': pupil['password'], 'newPassword': changed})
    token = c.login(pupil['username'], changed)['accessToken']
    require(any(r['courseId'] == course_id for r in c.ok('GET', '/edu/student/course/list', token)), '课程原本不可见')
    require(any(r['homeworkId'] == homework_id for r in student_tasks(c, token, course_id)), '任务原本不可见')
    graded_record = c.ok('POST', '/edu/student/homework/submit', token,
                         body={'homeworkId': graded_id, 'content': 'archive score'})
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
         body={'id': graded_record, 'score': 85, 'comment': 'archive score'})
    scope = {'classId': class_id, 'courseId': course_id}
    require(c.ok('GET', '/edu/statistics/total-pass-rate', params=scope)['totalCount'] == 1,
            '归档前统计基线错误')
    for delete_path, restore_path, item_id in (
        ('/edu/homework/delete', '/edu/homework/restore', homework_id),
        ('/edu/class-course/unbind', '/edu/class-course/restore', binding),
        ('/capacity/course/delete', '/capacity/course/restore', course_id),
        ('/edu/class/delete', '/edu/class/restore', class_id)):
        c.ok('DELETE', delete_path,
             x.teacher_token if delete_path == '/edu/homework/delete' else c.admin,
             {'id': item_id})
        require(not any(r['homeworkId'] == homework_id for r in student_tasks(c, token, course_id)),
                f'{delete_path} 后任务仍可见')
        c.denied('POST', '/edu/student/homework/submit', token, body={'homeworkId': homework_id, 'content': 'blocked'})
        c.denied('POST', '/edu/student/homework/attachment/upload', token,
                 {'homeworkId': homework_id}, upload=True)
        # V1.0.1.10：封存只读，历史数据照常计入分析类统计
        require(c.ok('GET', '/edu/statistics/total-pass-rate', params=scope)['totalCount'] == 1,
                f'{delete_path} 封存后统计不应减少')
        c.ok('PUT', restore_path,
             x.teacher_token if restore_path == '/edu/homework/restore' else c.admin,
             {'id': item_id})
        require(any(r['homeworkId'] == homework_id for r in student_tasks(c, token, course_id)),
                f'{restore_path} 后任务仍不可见')
        require(c.ok('GET', '/edu/statistics/total-pass-rate', params=scope)['totalCount'] == 1,
                f'{restore_path} 后统计未恢复')
    c.ok('DELETE', '/edu/homework/delete', x.teacher_token, {'id': graded_id})
    require(c.ok('GET', '/edu/statistics/total-pass-rate', params=scope)['totalCount'] == 1,
            '封存已批改任务后统计不应减少')
    c.ok('PUT', '/edu/homework/restore', x.teacher_token, {'id': graded_id})
    # 删除才不计入统计：先看影响范围，再删除
    impact = c.ok('GET', '/edu/homework/remove-impact', x.teacher_token, {'ids': str(graded_id)})
    require(impact['homeworkCount'] == 1 and impact['submittedCount'] == 1, f'删除影响范围错误：{impact}')
    c.ok('DELETE', '/edu/homework/remove', x.teacher_token, {'id': graded_id})
    require(c.ok('GET', '/edu/statistics/total-pass-rate', params=scope)['totalCount'] == 0,
            '删除已批改任务后统计仍计入')
    require(not any(r['homeworkId'] == graded_id for r in student_tasks(c, token, course_id)), '删除后学生仍能看到任务')
    assigned = c.ok('GET', '/edu/class/student/list-assigned', params={'classId': class_id})
    require(any(r['id'] == pupil['id'] for r in assigned), '归档班级丢失学生关系')
    c.ok('DELETE', '/edu/class-course/unbind', params={'id': binding})
    require(x.bind(class_id, course_id) == binding, '重新绑定未恢复原记录')
    c.ok('DELETE', '/edu/user/delete', params={'id': pupil['id']})
    require(c.ok('GET', '/system/user/get', params={'id': pupil['id']})['status'] == 1, '教务删除未停用学生')
    c.ok('PUT', '/edu/user/restore', params={'id': pupil['id']})
    require(c.ok('GET', '/system/user/get', params={'id': pupil['id']})['status'] == 0, '学生未恢复')


def scenario_public(c):
    x = Context(c)
    first = x.clazz()
    owner = x.user('teacher')
    other = x.user('teacher')
    x.teacher_token = c.login(owner['username'], owner['password'])['accessToken']
    course_id = x.course(owner['id'], public=True)
    c.ok('POST', '/capacity/course/audit-public', body={'id': course_id, 'approved': True, 'remark': 'E2E'})
    def link(cl, timeout=30):
        # V1.0.1.7 起公共课同步在提交后由后台执行，这里轮询等待关联出现
        end = time.monotonic() + timeout
        while True:
            found = next((r for r in c.ok('GET', '/edu/class-course/list-by-class', params={'classId': cl})
                          if r['courseId'] == course_id and r['status'] == 0), None)
            if found or time.monotonic() >= end:
                return found
            time.sleep(1)
    first_link = link(first)
    require(first_link, '现有班级未自动关联公共课')
    second = x.clazz()
    require(link(second), '新班级未自动关联公共课')
    pupil = x.user('student', second)
    other_token = c.login(other['username'], other['password'])['accessToken']
    c.ok('GET', '/capacity/course/get', other_token, {'id': course_id})
    # V1.0.1.10 起公共课按认领划分班级：负责人也要先认领才能给该班布置任务
    c.ok('POST', '/edu/class-course/claim', x.teacher_token, body={'courseId': course_id, 'classIds': [second]})
    homework_id, _ = x.homework(second, course_id)
    # 另一位教师不能给别人认领的班级布置任务，也不得批改课程所有者布置的任务。
    own_task = {'homeworkName': x.name('跨教师任务'), 'homeworkType': 1, 'homeworkLevel': 1,
                'courseId': course_id, 'classId': second, 'semesterName': x.semester,
                'deadline': future(), 'submissionMode': 'text', 'passScore': 60, 'score': 100, 'status': 1}
    c.denied('POST', '/edu/homework/create', other_token, body=own_task)
    p_token = c.login(pupil['username'], pupil['password'])['accessToken']
    changed = x.password()
    c.ok('PUT', '/edu/student/profile/password', p_token, body={'oldPassword': pupil['password'], 'newPassword': changed})
    p_token = c.login(pupil['username'], changed)['accessToken']
    record = c.ok('POST', '/edu/student/homework/submit', p_token, body={'homeworkId': homework_id, 'content': 'public'})
    c.denied('PUT', '/edu/homework-record/correct', other_token, body={'id': record, 'score': 90})
    c.ok('DELETE', '/edu/class-course/unbind', params={'id': first_link['id']})
    x.clazz()
    start = dt.date.today() - dt.timedelta(days=1)
    c.ok('PUT', '/edu/semester/current', body={
        'semesterName': x.semester, 'semesterStartDate': start.isoformat(),
        'semesterEndDate': (start + dt.timedelta(days=120)).isoformat()})  # 触发全量同步
    require(link(first) is None, '手动归档的公共课关联被同步恢复')


def scenario_message(c):
    x = Context(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(2)
    other_teacher = x.user('teacher')
    pupil = pupils[0]
    token = c.login(pupil['username'], pupil['password'])['accessToken']
    changed = x.password()
    c.ok('PUT', '/edu/student/profile/password', token, body={'oldPassword': pupil['password'], 'newPassword': changed})
    token = c.login(pupil['username'], changed)['accessToken']
    teacher_token = c.login(teacher['username'], teacher['password'])['accessToken']
    message = {'classId': class_id, 'courseId': course_id, 'content': 'E2E student message'}
    c.ok('POST', '/edu/student/message/send', token, body=message)
    rows = c.ok('GET', '/edu/message/student-list', teacher_token, {'classId': class_id, 'courseId': course_id})
    row = next((r for r in rows if r['studentId'] == pupil['id']), None)
    require(row and row['studentName'] == pupil['nickname'] and row['unreadCount'] >= 1,
            '教师未看到真实姓名或未读数')
    params = {'classId': class_id, 'courseId': course_id, 'studentId': pupil['id']}
    c.ok('GET', '/edu/message/detail', teacher_token, params)
    rows = c.ok('GET', '/edu/message/student-list', teacher_token, {'classId': class_id, 'courseId': course_id})
    require(next(r for r in rows if r['studentId'] == pupil['id'])['unreadCount'] == 0, '教师打开后未读未清零')
    c.ok('POST', '/edu/message/reply', teacher_token, body={**params, 'content': 'E2E teacher reply'})
    rows = c.ok('GET', '/edu/student/message/list', token)
    require(any(r['classId'] == class_id and r['courseId'] == course_id and r['unreadCount'] >= 1 for r in rows),
            '教师回复未增加学生未读数')
    c.ok('PUT', '/edu/message/close', teacher_token, params)
    c.denied('POST', '/edu/student/message/send', token, body=message)
    c.denied('POST', '/edu/message/reply', teacher_token, body={**params, 'content': 'blocked'})
    c.ok('PUT', '/edu/message/reopen', teacher_token, params)
    c.ok('POST', '/edu/student/message/send', token, body=message)
    outsider = c.login(other_teacher['username'], other_teacher['password'])['accessToken']
    c.denied('GET', '/edu/message/detail', outsider, params)
    c.denied('POST', '/edu/message/reply', outsider, body={**params, 'content': 'blocked'})
    another = ready_student(c, x, pupils[1])
    own_conversation = c.ok('GET', '/edu/student/message/detail', another,
                            {'classId': class_id, 'courseId': course_id})
    require(not own_conversation['messages'], '学生读取到了他人会话')
    unrelated = x.user('student')
    unrelated_token = ready_student(c, x, unrelated)
    c.denied('GET', '/edu/student/message/detail', unrelated_token,
             {'classId': class_id, 'courseId': course_id})


def scenario_dingtalk(c):
    config_path = '/system/auth/social-login-config'
    def enabled():
        data = c.ok('GET', config_path, token='')
        require(set(data) == {'dingtalkEnabled'}, '社交配置结构错误')
        return data['dingtalkEnabled']
    require(enabled() is False, '默认钉钉配置不是停用')
    c.denied('GET', '/system/auth/social-auth-redirect', c.admin,
             {'type': 20, 'redirectUri': 'http://127.0.0.1:1/'}, phrase='钉钉登录未启用')
    body = {'name': 'e2e-placeholder', 'socialType': 20, 'userType': 2,
            'clientId': 'e2e-placeholder', 'clientSecret': 'e2e-placeholder', 'status': 0}
    client_id = None
    try:
        client_id = c.ok('POST', '/system/social-client/create', body=body)
        require(enabled() is True, '启用配置后仍为 false')
        c.ok('PUT', '/system/social-client/update', body={**body, 'id': client_id, 'status': 1})
        require(enabled() is False, '停用配置后仍为 true')
    finally:
        if client_id is not None:
            c.ok('DELETE', '/system/social-client/delete', params={'id': client_id})


def ready_student(c, x, pupil):
    token = c.login(pupil['username'], pupil['password'])['accessToken']
    changed = x.password()
    c.ok('PUT', '/edu/student/profile/password', token,
         body={'oldPassword': pupil['password'], 'newPassword': changed})
    return c.login(pupil['username'], changed)['accessToken']


def scenario_metrics(c):
    # 与 deploy/database/tests/metrics/expected.json 的分档/名次样本同值，数据经 HTTP 动态创建。
    x = Context(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(5)
    tokens = [ready_student(c, x, pupil) for pupil in pupils]
    homework_id, _ = x.homework(class_id, course_id)
    stats = c.ok('GET', '/edu/homework/statistics', x.teacher_token, {'homeworkId': homework_id})
    require(stats['totalCount'] == 5, '应交人数不是创建时指派快照')
    records = []
    for token, score in zip(tokens, (59, 60, 75, 85, 85)):
        record = c.ok('POST', '/edu/student/homework/submit', token,
                      body={'homeworkId': homework_id, 'content': 'metrics'})
        c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
             body={'id': record, 'score': score, 'comment': 'E2E'})
        records.append(record)
    rates = c.ok('GET', '/edu/statistics/total-pass-rate',
                 params={'classId': class_id, 'courseId': course_id})
    for field, expected in {'totalCount': 5, 'unqualifiedRate': 20.0, 'qualifiedRate': 20.0,
                            'goodRate': 20.0, 'excellentRate': 40.0}.items():
        require(rates.get(field) == expected, f'分档边界 {field} 错误')
    page = list_items(c.ok('GET', '/edu/homework-record/page', x.teacher_token,
                           {'homeworkId': homework_id, 'pageNo': 1, 'pageSize': 20}))
    ranks = {row['id']: row['classRank'] for row in page}
    require(ranks[records[3]] == ranks[records[4]] == 1 and ranks[records[2]] == 3,
            '并列排名不是 1、1、3')
    newcomer = x.user('student')
    c.ok('POST', '/edu/class/student/add', body={'classId': class_id,
                                                  'studentIds': [newcomer['id']]})
    new_token = ready_student(c, x, newcomer)
    require(any(row['homeworkId'] == homework_id for row in student_tasks(c, new_token, course_id)),
            '新入班学生未收到未截止任务')
    stats_after = c.ok('GET', '/edu/homework/statistics', x.teacher_token, {'homeworkId': homework_id})
    require(stats_after['totalCount'] == 6, '未截止任务补发后应交快照未更新')
    # 再创建已截止任务需要时间流逝；本机验收用独立任务确认新入班者计入后续快照。
    next_id, _ = x.homework(class_id, course_id)
    require(c.ok('GET', '/edu/homework/statistics', x.teacher_token, {'homeworkId': next_id})['totalCount'] == 6,
            '后续任务应交快照未包含新入班学生')
    report = c.ok('GET', '/edu/student/capacity/report/basic', tokens[1])
    require(report.get('totalTasks') == 2 and report.get('evaluatedTasks') == 1
            and report.get('qualifiedTasks') == 1,
            '学生报告任务数或及格边界成绩错误')


def scenario_unlock(c):
    x = Context(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    pupil = pupils[0]
    token = ready_student(c, x, pupil)
    higher, _ = x.homework(class_id, course_id, level=2)
    status = c.ok('GET', '/edu/student/course/unlock-status', token, {'courseId': course_id})
    levels = {r['level']: r for r in status['levels']}
    require(levels[1]['totalTasks'] == 0 and levels[2]['unlocked'] is True,
            '前置等级无任务时未解锁后续等级')
    existing = c.ok('POST', '/edu/student/homework/submit', token,
                    body={'homeworkId': higher, 'content': 'existing submission'})
    lower, _ = x.homework(class_id, course_id, level=1)
    newer, _ = x.homework(class_id, course_id, level=2)
    status = c.ok('GET', '/edu/student/course/unlock-status', token, {'courseId': course_id})
    levels = {r['level']: r for r in status['levels']}
    require(levels[1]['totalTasks'] >= 1 and levels[2]['unlocked'] is False,
            '前置任务未达标仍允许解锁')
    c.denied('GET', '/edu/student/homework/get', token, {'homeworkId': newer}, code=LEVEL_LOCKED)
    c.denied('POST', '/edu/student/homework/submit', token,
             body={'homeworkId': newer, 'content': 'new task'}, code=LEVEL_LOCKED)
    detail = c.ok('GET', '/edu/student/homework/get', token, {'homeworkId': higher})
    require(detail['recordId'] == existing, '已有提交的任务不可查看')
    c.ok('PUT', '/edu/homework-record/redo', x.teacher_token,
         body={'id': existing, 'redoDeadline': future(10)})
    c.ok('PUT', '/edu/student/homework/update', token, body={'id': existing, 'content': 'resubmission'})


def scenario_ai(c):
    x = Context(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    homework_id, _ = x.homework(class_id, course_id)
    calibration = c.ok('GET', '/edu/ai-review/calibration', x.teacher_token,
                       {'homeworkId': homework_id})
    require(calibration.get('direction') == 'INSUFFICIENT' and not calibration.get('hint'),
            '不足样本的 AI 校准结果错误')
    token = ready_student(c, x, pupils[0])
    record = c.ok('POST', '/edu/student/homework/submit', token,
                  body={'homeworkId': homework_id, 'content': 'no AI draft'})
    c.denied('POST', '/edu/ai-review/feedback', x.teacher_token,
             body={'homeworkRecordId': record, 'feedbackType': 'normal'}, code=1050010014)


SCENARIOS = {
    '匿名与下线接口': scenario_anonymous,
    '学生强制改密': scenario_password,
    '评分轨迹': scenario_history,
    '归档': scenario_archive,
    '公共课': scenario_public,
    '留言': scenario_message,
    '钉钉按配置显示': scenario_dingtalk,
    '统计口径': scenario_metrics,
    '解锁规则': scenario_unlock,
    'AI 校准接口': scenario_ai,
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=SCENARIOS, help='只执行一个场景')
    parser.add_argument('--dry-run', action='store_true', help='只列场景和接口路径，不发请求')
    args = parser.parse_args()
    selected = [args.only] if args.only else list(SCENARIOS)
    if args.dry_run:
        for name in selected:
            paths = (SETUP_PATHS if name not in ('匿名与下线接口', '钉钉按配置显示') else []) + PATHS[name]
            print(f'{name}: ' + ', '.join(PREFIX + p for p in dict.fromkeys(paths)))
        return 0
    try:
        base = local_url(os.environ.get('NUS_E2E_BASE_URL'))
        username = os.environ['NUS_E2E_ADMIN_USERNAME']
        password_file = Path(os.environ['NUS_E2E_ADMIN_PASSWORD_FILE'])
        password = password_file.read_text(encoding='utf-8').rstrip('\r\n')
        require(bool(username and password), '管理员用户名或口令为空')
    except (KeyError, OSError, ValueError, AssertionError) as exc:
        # 不传播 OSError 的路径等细节，也不输出口令文件内容。
        print('配置错误：缺少或不合法的本机 URL、管理员用户名或口令文件', file=sys.stderr)
        return 2
    client = Client(base)
    try:
        client.admin = client.login(username, password)['accessToken']
    except Exception:
        print('管理员登录失败（响应与凭据已隐藏）', file=sys.stderr)
        return 2
    passed = failed = 0
    for name in selected:
        try:
            SCENARIOS[name](client)
            print(f'PASS {name}：关键接口和业务状态断言通过')
            passed += 1
        except Exception as exc:
            # 只输出受控的断言文本；底层请求异常可能包含 URL/响应，故仅报类型。
            reason = str(exc) if isinstance(exc, AssertionError) else type(exc).__name__
            print(f'FAIL {name}：{reason}')
            failed += 1
    print(f'汇总：PASS {passed}，FAIL {failed}')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
