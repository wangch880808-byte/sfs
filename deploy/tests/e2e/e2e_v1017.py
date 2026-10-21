#!/usr/bin/env python3
"""V1.0.1.7 本机 HTTP 端到端验收；仅用标准库，复用 e2e_v1015 的请求与造数辅助函数。"""
import argparse
import concurrent.futures
import datetime as dt
import json
import os
import secrets
import sys
import threading
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.dont_write_bytecode = True  # __pycache__ 已入库，导入时不要改写它
from e2e_v1015 import (PREFIX, Client, Context, list_items, local_url,  # noqa: E402
                       ready_student, require, student_tasks)

DEADLINE_EXCEEDED = 1050005001
ALREADY_SUBMITTED = 1050005002
RECORD_CONFLICT = 1050005101
UNDO_EXPIRED = 1050005103
EXTENSION_SUBMITTED = 1050004104
REMIND_TOO_FREQUENT = 1050004107
FORBIDDEN = 403
XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
PROMPT_CODES = {'HOMEWORK_REVIEW', 'HOMEWORK_REWRITE', 'CLASS_ANALYSIS', 'STUDENT_ANALYSIS', 'STUDENT_COMMENT'}
AI_QUEUED, AI_PROCESSING, AI_READY, AI_FAILED, AI_SKIPPED = 0, 1, 2, 3, 6
UNLOCK_FIELDS = {'unlocked', 'passedCount', 'requiredCount', 'pendingReviewCount', 'ratio', 'threshold', 'reasonText'}
STUDENT_SETUP = ['/system/auth/login', '/edu/student/profile/password']
SETUP_PATHS = ['/edu/semester/current', '/system/dept/list-all-simple', '/system/dept/create',
               '/capacity/indicator/list', '/edu/class/create', '/system/user/create-teacher',
               '/system/user/create-student', '/capacity/course/create', '/edu/class-course/bind',
               '/edu/class-course/list-by-class', '/edu/homework/create', *STUDENT_SETUP]
PATHS = {
    '批改队列不截断': ['/edu/student/homework/submit', '/edu/homework-record/grading-queue'],
    '批改冲突': ['/edu/student/homework/submit', '/edu/homework-record/get', '/edu/homework-record/correct',
             '/edu/homework-record/redo'],
    '撤销与延时通知': ['/infra/config/get-value-by-key', '/edu/student/homework/submit',
                '/edu/homework-record/correct', '/edu/homework-record/undo', '/edu/homework-record/get',
                '/edu/homework-record/history', '/system/notify-message/my-page'],
    '重复提交': ['/edu/student/homework/submit', '/edu/homework-record/grading-queue'],
    '批改方式归一': ['/edu/homework/get', '/edu/homework/update'],
    'AI 初评排队': ['/edu/student/homework/submit', '/edu/homework-ai-review/get-by-record',
                '/edu/homework-record/grading-queue'],
    '个别延期': ['/edu/homework/extension/set', '/edu/homework/extension/list', '/edu/student/homework/get',
             '/edu/student/homework/list', '/edu/student/homework/submit', '/system/notify-message/my-page'],
    '催交与限频': ['/infra/config/get-value-by-key', '/edu/student/homework/submit', '/edu/homework/remind',
              '/system/notify-message/my-page'],
    '复制任务': ['/edu/homework/copy/targets', '/edu/homework/create', '/edu/homework/get',
             '/edu/student/homework/list', '/edu/homework/delete'],
    '学期收尾': ['/edu/student/homework/submit', '/edu/semester/options', '/edu/semester/wrap-up/preview',
             '/edu/semester/wrap-up/archive'],
    '成绩册原样导回': ['/edu/student/homework/submit', '/edu/homework-record/correct', '/edu/homework-record/redo',
                '/edu/student/homework/update', '/edu/homework-record/gradebook/export',
                '/edu/data-exchange/imports/HOMEWORK_GRADEBOOK/preview',
                '/edu/data-exchange/imports/{batchNo}/confirm', '/edu/data-exchange/imports/{batchNo}',
                '/edu/homework-record/grading-queue'],
    '留言通知与已读': ['/edu/student/message/send', '/system/notify-message/my-page', '/edu/message/detail',
                '/edu/message/reply', '/edu/student/message/detail'],
    '解锁说明': ['/edu/student/course/unlock-status', '/edu/student/homework/submit',
             '/edu/homework-record/correct'],
    '附件上限': ['/infra/config/get-value-by-key', '/edu/student/homework/get'],
    'AI 治理接口': ['/ai/prompt-template/list', '/ai/prompt-template/history', '/ai/call-log/page',
                '/ai/call-log/daily-summary'],
    '公共课审批权限': ['/system/role/simple-list', '/system/user/create', '/system/notify-message/my-page',
                '/capacity/course/page', '/capacity/course/audit-public', '/capacity/course/get'],
}


def at(**delta):
    return (dt.datetime.now() + dt.timedelta(**delta)).strftime('%Y-%m-%d %H:%M:%S')


def parse_time(value):
    # 批改相关的 updateTime 带毫秒（乐观校验按毫秒比较），其余时间到秒
    fmt = '%Y-%m-%d %H:%M:%S.%f' if '.' in value else '%Y-%m-%d %H:%M:%S'
    return dt.datetime.strptime(value, fmt)


def wait_for(probe, timeout, interval=1.0):
    """轮询到 probe() 为真或超时；返回最后一次结果。"""
    end = time.monotonic() + timeout
    while True:
        result = probe()
        if result or time.monotonic() >= end:
            return result
        time.sleep(interval)


class Client17(Client):
    """补充原样下载与多字段上传；同样禁用代理与跳转，只连本机。"""

    def ok(self, method, path, token=None, params=None, body=None, upload=False):
        status, payload = self.call(method, path, self.admin if token is None else token,
                                    params, body, upload)
        require(status == 200 and payload.get('code') == 0,
                f'{path} 失败：HTTP {status}，code={payload.get("code")}，msg={str(payload.get("msg"))[:120]}')
        return payload.get('data')

    def denied(self, method, path, token=None, params=None, body=None, code=None, phrase=None, upload=False):
        status, payload = self.call(method, path, token, params, body, upload)
        require(status >= 400 or payload.get('code') not in (None, 0), f'{path} 意外成功')
        if code is not None:
            require(payload.get('code') == code,
                    f'{path} 错误码应为 {code}，实际 {payload.get("code")}（{str(payload.get("msg"))[:80]}）')
        if phrase is not None:
            require(phrase in str(payload.get('msg', '')), f'{path} 错误原因不含「{phrase}」')
        return payload

    def send(self, method, path, token, params=None, data=None, content_type=None, timeout=30):
        require(path.startswith('/') and not path.startswith('//') and '?' not in path and '#' not in path,
                '非法接口路径')
        url = self.base + PREFIX + path
        if params:
            url += '?' + urllib.parse.urlencode(params, doseq=True)
        headers = {'Authorization': 'Bearer ' + token, 'Accept-Language': 'zh-CN'}
        if content_type:
            headers['Content-Type'] = content_type
        if method not in ('GET', 'HEAD'):
            headers['X-Idempotency-Key'] = str(uuid.uuid4())
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=timeout)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            return response.status, response.headers.get('Content-Type', ''), response.read(32 * 1024 * 1024)

    def download(self, path, token, params):
        status, content_type, raw = self.send('GET', path, token, params)
        require(status == 200 and content_type.startswith(XLSX) and raw[:2] == b'PK',
                f'{path} 未返回 xlsx：HTTP {status}')
        return raw

    def form(self, path, token, fields, filename, content, content_type=XLSX):
        boundary = 'e2e' + secrets.token_hex(12)
        parts = []
        for name, value in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode()
                         + value.encode() + b'\r\n')
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                     f'Content-Type: {content_type}\r\n\r\n'.encode() + content + b'\r\n')
        data = b''.join(parts) + f'--{boundary}--\r\n'.encode()
        status, _, raw = self.send('POST', path, token, data=data,
                                   content_type='multipart/form-data; boundary=' + boundary)
        try:
            payload = json.loads(raw)
        except ValueError:
            payload = {}
        require(status == 200 and payload.get('code') == 0,
                f'{path} 失败：HTTP {status}，code={payload.get("code")}，msg={str(payload.get("msg"))[:120]}')
        return payload.get('data')


SHARED = {}
SEMESTER_TIMEOUT = 600
# 本次运行新建的班级与公共课；结束时归档，免得以后每次切换学期都给它们同步、补发通知
CREATED = {'classes': [], 'public_courses': []}


class Ctx(Context):
    """同一次运行共用一个验收学期、学院和指标，避免每个场景都切换当前学期、触发全量公共课同步。"""

    def __init__(self, client):
        super().__init__(client)
        if 'semester' in SHARED:
            self.semester, self.dept, self.indicator = SHARED['semester'], SHARED['dept'], SHARED['indicator']
            self.semester_ready = True

    def foundation(self):
        if not self.semester_ready:
            # 切换当前学期会同步全部公共课并给学生发「课程已分配」，数据多时远超普通请求的 12 秒，单独计时
            start = dt.date.today() - dt.timedelta(days=1)
            body = {'semesterName': self.semester, 'semesterStartDate': start.isoformat(),
                    'semesterEndDate': (start + dt.timedelta(days=120)).isoformat()}
            began = time.monotonic()
            status, _, raw = self.c.send('PUT', '/edu/semester/current', self.c.admin,
                                         data=json.dumps(body).encode(), content_type='application/json',
                                         timeout=SEMESTER_TIMEOUT)
            require(status == 200 and json.loads(raw).get('code') == 0, f'切换当前学期失败：HTTP {status}')
            SHARED['switch_seconds'] = time.monotonic() - began
            self.semester_ready = True
        result = super().foundation()
        SHARED.update(semester=self.semester, dept=self.dept, indicator=self.indicator)
        return result

    def clazz(self):
        class_id = super().clazz()
        CREATED['classes'].append(class_id)
        return class_id

    def course(self, teacher_id, public=False):
        course_id = super().course(teacher_id, public)
        if public:
            CREATED['public_courses'].append(course_id)
        return course_id

    def login(self, user):
        return self.c.login(user['username'], user['password'])['accessToken']

    def task(self, class_id, course_id, token=None, **fields):
        body = {'homeworkName': self.name('验收任务'), 'homeworkType': 1, 'homeworkLevel': 1,
                'courseId': course_id, 'classId': class_id, 'semesterName': self.semester,
                'requirement': '本机验收', 'deadline': at(days=30), 'autoGrade': 0,
                'submissionMode': 'text', 'aiReviewMode': 0, 'passScore': 60, 'score': 100, 'status': 1}
        body.update(fields)
        return self.c.ok('POST', '/edu/homework/create', token or self.teacher_token, body=body), body

    def bind_with(self, class_id, course_id, **fields):
        start = dt.date.today() - dt.timedelta(days=1)
        self.c.ok('POST', '/edu/class-course/bind', body={
            'courseId': course_id, 'classIds': [class_id], 'semesterName': self.semester,
            'semesterStartDate': start.isoformat(), 'semesterEndDate': (start + dt.timedelta(days=120)).isoformat(),
            **fields})
        rows = self.c.ok('GET', '/edu/class-course/list-by-class', params={'classId': class_id, 'includeArchived': 'true'})
        matches = [r for r in rows if r['courseId'] == course_id and r['semesterName'] == self.semester]
        require(matches, '未找到班课绑定')
        return matches[-1]['id']


def config_int(c, key, default):
    value = c.ok('GET', '/infra/config/get-value-by-key', params={'key': key})
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def confirm_import(c, batch_no, token, body):
    """确认导入（V1.0.1.8 起后台执行）：提交后轮询批次，直到成功、失败或取消。"""
    started = c.ok('POST', f'/edu/data-exchange/imports/{batch_no}/confirm', token, body=body)
    require(started['status'] in ('CONFIRMING', 'SUCCESS'), f'确认导入未开始：{started.get("status")}')
    done = wait_for(lambda: (lambda b: b if b['status'] in ('SUCCESS', 'FAILED', 'CANCELLED') else None)(
        c.ok('GET', f'/edu/data-exchange/imports/{batch_no}', token)), timeout=120, interval=0.5)
    require(done is not None, f'确认导入 120 秒内未结束：{batch_no}')
    return done


def notices(c, token, code, **params):
    """当前用户收到的指定模板通知；params 按模板参数过滤（值统一按字符串比较）。"""
    rows = list_items(c.ok('GET', '/system/notify-message/my-page', token, {'pageNo': 1, 'pageSize': 100}))
    result = []
    for row in rows:
        values = row.get('templateParams') or {}
        if row.get('templateCode') == code and all(str(values.get(k)) == str(v) for k, v in params.items()):
            result.append(row)
    return result


def submit(c, token, homework_id, content='e2e'):
    return c.ok('POST', '/edu/student/homework/submit', token, body={'homeworkId': homework_id, 'content': content})


def queue(c, token, homework_id):
    return c.ok('GET', '/edu/homework-record/grading-queue', token, {'homeworkId': homework_id})


def scenario_queue(c, size):
    x = Ctx(c)
    class_id = x.clazz()
    teacher = x.user('teacher')
    x.teacher_token = x.login(teacher)
    course_id = x.course(teacher['id'])
    x.bind(class_id, course_id)
    dept, _ = x.foundation()
    pupils = [{'username': x.username('q'), 'password': x.password(), 'nickname': x.name('队列学生')}
              for _ in range(size)]

    def create(pupil):
        pupil['id'] = c.ok('POST', '/system/user/create-student', body={
            'username': pupil['username'], 'nickname': pupil['nickname'], 'password': pupil['password'],
            'deptId': dept, 'profile': {'gradeYear': dt.date.today().year, 'classIds': [class_id]}})

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            list(pool.map(create, pupils))
        homework_id, _ = x.task(class_id, course_id)
        # 学生首次登录要改密码（密码哈希较耗 CPU），并发低一些，避免本机繁忙时请求超时
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            records = list(pool.map(lambda p: submit(c, ready_student(c, x, p), homework_id, 'queue'), pupils))
        return check_queue(c, x, homework_id, records, size)
    finally:
        # 归档这个大班：否则以后每次切换学期都要给这些学生补发公共课通知，越跑越慢
        c.call('DELETE', '/edu/class/delete', c.admin, {'id': class_id})


def check_queue(c, x, homework_id, records, size):
    data = queue(c, x.teacher_token, homework_id)
    require(data['total'] == size and len(data['list']) == size,
            f'批改队列被截断：应为 {size}，total={data["total"]}，list={len(data["list"])}')
    require({r['id'] for r in data['list']} == set(records), '批改队列与实际提交不一致')
    require(all(r['status'] == 1 and r['studentName'] and r['studentNo'] and r['updateTime'] for r in data['list']),
            '队列行缺少状态、学生姓名、学号或更新时间')
    # 旧分页接口最大 200 条，确认队列不受它限制
    page = c.ok('GET', '/edu/homework-record/page', x.teacher_token,
                {'homeworkId': homework_id, 'pageNo': 1, 'pageSize': 100})
    require(page['total'] == size, '分页接口的提交总数不一致')
    other = x.user('teacher')
    c.denied('GET', '/edu/homework-record/grading-queue', x.login(other), {'homeworkId': homework_id})
    return f'{size} 份提交全部返回'


def scenario_conflict(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    homework_id, _ = x.task(class_id, course_id)
    record_id = submit(c, token, homework_id)
    loaded = c.ok('GET', '/edu/homework-record/get', x.teacher_token, {'id': record_id})
    read_time = loaded['updateTime']
    require(read_time, '/edu/homework-record/get 未返回 updateTime')
    stale = (parse_time(read_time) - dt.timedelta(minutes=1)).strftime('%Y-%m-%d %H:%M:%S')
    c.denied('PUT', '/edu/homework-record/correct', x.teacher_token,
             body={'id': record_id, 'score': 80, 'comment': 'stale', 'expectedUpdateTime': stale}, code=RECORD_CONFLICT)
    record = c.ok('GET', '/edu/homework-record/get', x.teacher_token, {'id': record_id})
    require(record['status'] == 1 and record['score'] is None, '冲突被拒后记录仍被改动')
    time.sleep(1.1)  # update_time 精确到秒：确保批改写入的时间与读取时不同
    graded = c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
                  body={'id': record_id, 'score': 80, 'comment': 'fresh', 'expectedUpdateTime': read_time})
    require(graded['status'] == 2 and graded['score'] == 80 and graded['updateTime'] != read_time,
            '按最新 updateTime 批改未生效')
    # 另一位教师（或另一页签）仍拿着旧的读取时间
    c.denied('PUT', '/edu/homework-record/correct', x.teacher_token,
             body={'id': record_id, 'score': 85, 'comment': 'late', 'expectedUpdateTime': read_time}, code=RECORD_CONFLICT)
    c.denied('PUT', '/edu/homework-record/redo', x.teacher_token,
             body={'id': record_id, 'redoDeadline': at(days=7), 'expectedUpdateTime': read_time}, code=RECORD_CONFLICT)
    record = c.ok('GET', '/edu/homework-record/get', x.teacher_token, {'id': record_id})
    require(record['status'] == 2 and record['score'] == 80, '过期的 expectedUpdateTime 改动了记录')
    redone = c.ok('PUT', '/edu/homework-record/redo', x.teacher_token,
                  body={'id': record_id, 'redoDeadline': at(days=7), 'expectedUpdateTime': graded['updateTime']})
    require(redone['status'] == 3, '按批改返回的 updateTime 打回未生效')


def scenario_undo(c):
    undo_seconds = config_int(c, 'edu.grading.undo-seconds', 10)
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    homework_id, _ = x.task(class_id, course_id)
    record_id = submit(c, token, homework_id)
    code = 'edu.student.homework.corrected'
    name = c.ok('GET', '/edu/homework/get', x.teacher_token, {'id': homework_id})['homeworkName']
    count = lambda: len(notices(c, token, code, homeworkName=name))  # noqa: E731
    require(count() == 0, '批改前已有批改通知')
    first = c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
                 body={'id': record_id, 'score': 70, 'comment': 'first'})
    if undo_seconds <= 0:
        require(first.get('undoToken') is None, '撤销时长为 0 仍返回撤销凭据')
        require(wait_for(lambda: count() == 1, 15), '撤销时长为 0 时通知未在下一轮发出')
        return '撤销时长配置为 0：只验证立即通知'
    require(first.get('undoToken') and 0 < first['undoSeconds'] <= undo_seconds and first.get('undoDeadline'),
            '批改未返回撤销凭据、剩余秒数或撤销截止时间')
    require(count() == 0, '撤销期内已通知学生')
    undone = c.ok('PUT', '/edu/homework-record/undo', x.teacher_token, body={'undoToken': first['undoToken']})
    require(undone['status'] == 1 and undone['score'] is None, '撤销未恢复为待批改')
    record = c.ok('GET', '/edu/homework-record/get', x.teacher_token, {'id': record_id})
    require(record['status'] == 1 and record['score'] is None, '撤销后记录仍是已批改')
    events = [h['eventType'] for h in c.ok('GET', '/edu/homework-record/history', x.teacher_token, {'recordId': record_id})]
    require(events[-1:] == ['UNDO'] and 'CORRECT' in events, f'撤销未写入轨迹：{events}')
    c.denied('PUT', '/edu/homework-record/undo', x.teacher_token, body={'undoToken': first['undoToken']})
    started = time.monotonic()
    second = c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
                  body={'id': record_id, 'score': 90, 'comment': 'second'})
    require(second.get('undoToken'), '第二次批改未返回撤销凭据')
    early = []
    # 撤销期内反复确认学生还没收到；之后最多等撤销期 + 3 秒宽限 + 5 秒轮询 + 余量
    while time.monotonic() - started < second['undoSeconds'] - 1:
        early.append(count())
        time.sleep(1)
    require(not any(early), '撤销期内学生已收到批改通知')
    require(wait_for(lambda: count() >= 1, undo_seconds + 3 + 5 + 10), '撤销期过后学生仍未收到批改通知')
    time.sleep(1)
    rows = notices(c, token, code)
    require(len(rows) == 1, f'学生应只收到 1 条批改通知（被撤销的不发），实际 {len(rows)}')
    require(str((rows[0].get('templateParams') or {}).get('score')) == '90', '通知中的成绩不是最终成绩')
    c.denied('PUT', '/edu/homework-record/undo', x.teacher_token, body={'undoToken': second['undoToken']},
             code=UNDO_EXPIRED)
    return f'撤销时长 {undo_seconds} 秒，通知约 {round(time.monotonic() - started)} 秒后核对'


def scenario_duplicate(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(2)
    first_token, second_token = (ready_student(c, x, p) for p in pupils)
    homework_id, _ = x.task(class_id, course_id)
    submit(c, first_token, homework_id)
    c.denied('POST', '/edu/student/homework/submit', first_token, body={'homeworkId': homework_id, 'content': 'again'},
             code=ALREADY_SUBMITTED, phrase='请勿重复提交')
    # 并发提交：各请求带不同幂等键，只能成功一份，其余都应是同一条友好提示
    barrier = threading.Barrier(8, timeout=60)

    def race(_):
        barrier.wait()
        return c.call('POST', '/edu/student/homework/submit', second_token,
                      body={'homeworkId': homework_id, 'content': 'race'})

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(race, range(8)))
    codes = sorted(payload.get('code') for _, payload in results)
    require(codes.count(0) == 1, f'并发提交应只成功 1 份，实际错误码 {codes}')
    require(all(code in (0, ALREADY_SUBMITTED) for code in codes), f'并发提交出现非友好错误：{codes}')
    rows = queue(c, x.teacher_token, homework_id)['list']
    require(sorted(r['studentId'] for r in rows) == sorted(p['id'] for p in pupils), '重复提交产生了多条记录')


def scenario_grading_mode(c):
    x = Ctx(c)
    class_id, teacher, _, course_id, _ = x.bundle(0)

    def mode(homework_id):
        data = c.ok('GET', '/edu/homework/get', x.teacher_token, {'id': homework_id})
        return data['autoGrade'], data['aiReviewMode']

    legacy, _ = x.task(class_id, course_id, autoGrade=1, aiReviewMode=0)
    require(mode(legacy) == (1, 1), f'新建 autoGrade=1、aiReviewMode=0 未归一为 (1, 1)：{mode(legacy)}')
    manual, _ = x.task(class_id, course_id, autoGrade=0, aiReviewMode=1)
    require(mode(manual) == (0, 0), f'新建 autoGrade=0、aiReviewMode=1 未归一为 (0, 0)：{mode(manual)}')
    updated, body = x.task(class_id, course_id)
    c.ok('PUT', '/edu/homework/update', x.teacher_token, body={**body, 'id': updated, 'autoGrade': 1, 'aiReviewMode': 0})
    require(mode(updated) == (1, 1), f'更新为 autoGrade=1、aiReviewMode=0 未归一：{mode(updated)}')


def scenario_ai_queue(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(2)
    tokens = [ready_student(c, x, p) for p in pupils]
    assisted, _ = x.task(class_id, course_id, autoGrade=1, aiReviewMode=1)
    manual, _ = x.task(class_id, course_id)
    record_id = submit(c, tokens[0], assisted, '请评估这份本机验收提交')
    first = c.ok('GET', '/edu/homework-ai-review/get-by-record', x.teacher_token, {'homeworkRecordId': record_id})
    require(first is not None and first.get('status') in (AI_QUEUED, AI_PROCESSING, AI_READY, AI_FAILED, AI_SKIPPED),
            f'提交后没有 AI 初评行：{first and first.get("status")}')

    def settled():
        data = c.ok('GET', '/edu/homework-ai-review/get-by-record', x.teacher_token, {'homeworkRecordId': record_id})
        return data if data['status'] not in (AI_QUEUED, AI_PROCESSING) else None

    final = wait_for(settled, 40, 2) or c.ok('GET', '/edu/homework-ai-review/get-by-record', x.teacher_token,
                                             {'homeworkRecordId': record_id})
    status = final['status']
    if status in (AI_FAILED, AI_SKIPPED):
        require((final.get('errorMessage') or '').strip(), f'AI 初评状态 {status} 但没有写明原因')
    elif status == AI_READY:
        require(final.get('aiScore') is not None, 'AI 初评已就绪但没有分数')
    row = next(r for r in queue(c, x.teacher_token, assisted)['list'] if r['id'] == record_id)
    require(row['aiReviewStatus'] == status, '批改队列中的 AI 初评状态与初评记录不一致')
    manual_record = submit(c, tokens[1], manual)
    require(c.ok('GET', '/edu/homework-ai-review/get-by-record', x.teacher_token,
                 {'homeworkRecordId': manual_record}) is None, '仅人工批改的任务也写入了 AI 初评')
    names = {AI_QUEUED: '排队中', AI_PROCESSING: '处理中', AI_READY: '已就绪', AI_FAILED: '失败', AI_SKIPPED: '已跳过'}
    reason = f'，原因：{(final.get("errorMessage") or "")[:60]}' if status in (AI_FAILED, AI_SKIPPED) else ''
    return f'初评状态：{names.get(status, status)}{reason}'


def scenario_extension(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(2)
    extended, plain = pupils
    tokens = [ready_student(c, x, p) for p in pupils]
    homework_id, body = x.task(class_id, course_id, deadline=at(hours=-1))
    personal = at(days=1)[:-2] + '00'  # 个人截止时间按分钟保存
    count = c.ok('PUT', '/edu/homework/extension/set', x.teacher_token, body={
        'homeworkId': homework_id, 'studentIds': [extended['id']], 'deadline': personal, 'reason': '本机验收延期'})
    require(count == 1, f'延期人数应为 1，实际 {count}')
    rows = c.ok('GET', '/edu/homework/extension/list', x.teacher_token, {'homeworkId': homework_id})
    require([r['studentId'] for r in rows] == [extended['id']], '延期列表不正确')
    mine = c.ok('GET', '/edu/student/homework/get', tokens[0], {'homeworkId': homework_id})
    require(mine['canSubmit'] is True and mine['personalDeadline'] == personal
            and mine['effectiveDeadline'] == personal, '延期学生的详情未返回个人截止时间或不能提交')
    other = c.ok('GET', '/edu/student/homework/get', tokens[1], {'homeworkId': homework_id})
    require(other['canSubmit'] is False and other['personalDeadline'] is None
            and other['effectiveDeadline'] == body['deadline'], '未延期学生的详情不应可提交')
    listed = next(r for r in student_tasks(c, tokens[0], course_id) if r['homeworkId'] == homework_id)
    require(listed.get('effectiveDeadline') == personal, '学生任务列表未显示实际截止时间')
    submit(c, tokens[0], homework_id, 'after deadline with extension')
    c.denied('POST', '/edu/student/homework/submit', tokens[1], body={'homeworkId': homework_id, 'content': 'late'},
             code=DEADLINE_EXCEEDED)
    require(wait_for(lambda: notices(c, tokens[0], 'edu.student.homework.extended'), 5),
            '延期学生未收到延期通知')
    require(not notices(c, tokens[1], 'edu.student.homework.extended'), '未延期学生收到了延期通知')
    c.denied('PUT', '/edu/homework/extension/set', x.teacher_token, body={
        'homeworkId': homework_id, 'studentIds': [extended['id']], 'deadline': at(days=2), 'reason': 'again'},
        code=EXTENSION_SUBMITTED)
    outsider = x.user('teacher')
    c.denied('PUT', '/edu/homework/extension/set', x.login(outsider), body={
        'homeworkId': homework_id, 'studentIds': [plain['id']], 'deadline': at(days=2), 'reason': 'x'})


def scenario_remind(c):
    interval = config_int(c, 'edu.remind.manual-interval-minutes', 60)
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(3)
    done_token, waiting_token = ready_student(c, x, pupils[0]), ready_student(c, x, pupils[1])
    homework_id, _ = x.task(class_id, course_id)
    submit(c, done_token, homework_id)
    outsider = x.user('teacher')
    c.denied('POST', '/edu/homework/remind', x.login(outsider), {'homeworkId': homework_id})
    result = c.ok('POST', '/edu/homework/remind', x.teacher_token, {'homeworkId': homework_id})
    require(result['targetCount'] == 2 and result['notifiedCount'] == 2, f'催交人数错误：{result}')
    require(wait_for(lambda: notices(c, waiting_token, 'edu.student.homework.remind'), 5), '未提交学生没收到催交')
    require(not notices(c, done_token, 'edu.student.homework.remind'), '已提交学生收到了催交')
    if interval > 0:
        require(result.get('nextAvailableTime'), '未返回下次可催交时间')
        c.denied('POST', '/edu/homework/remind', x.teacher_token, {'homeworkId': homework_id},
                 code=REMIND_TOO_FREQUENT)
        require(len(notices(c, waiting_token, 'edu.student.homework.remind')) == 1, '限频后仍重复催交')
        return f'催交间隔 {interval} 分钟'
    c.ok('POST', '/edu/homework/remind', x.teacher_token, {'homeworkId': homework_id})
    return '催交间隔配置为 0：不限频'


def scenario_copy(c):
    """V1.0.1.10：复制 = 打开预填的新建表单，按班课逐个新建，直接发布；不再有草稿。"""
    x = Ctx(c)
    class_id, teacher, pupils, course_id, source_binding = x.bundle()
    target_class = x.clazz()
    target_binding = x.bind(target_class, course_id)
    newcomer = x.user('student', target_class)
    token = ready_student(c, x, newcomer)
    source, body = x.task(class_id, course_id, passScore=70, homeworkLevel=1)
    targets = c.ok('GET', '/edu/homework/copy/targets', x.teacher_token, {'semesterName': x.semester})
    require(any(t['classCourseId'] == target_binding for t in targets), '复制目标里没有新绑定的班课')
    copy = {**body, 'classId': target_class, 'classCourseId': target_binding, 'sourceHomeworkId': source,
            'deadline': at(days=14)}
    copy.pop('status')
    c.denied('POST', '/edu/homework/create', x.teacher_token, body={**copy, 'classCourseId': source_binding})
    c.denied('POST', '/edu/homework/create', x.teacher_token, body={**copy, 'status': 0})
    copied_id = c.ok('POST', '/edu/homework/create', x.teacher_token, body=copy)
    copied = c.ok('GET', '/edu/homework/get', x.teacher_token, {'id': copied_id})
    require(copied['status'] == 1 and copied['classId'] == target_class and copied['passScore'] == 70
            and copied['requirement'] == body['requirement'], f'复制出的任务不对：{copied}')
    require(any(r['homeworkId'] == copied_id for r in student_tasks(c, token, course_id)), '复制后学生看不到任务')
    # 往期封存的任务也能作为复制来源
    c.ok('DELETE', '/edu/homework/delete', x.teacher_token, {'id': source})
    again = c.ok('POST', '/edu/homework/create', x.teacher_token,
                 body={**copy, 'homeworkName': body['homeworkName'] + '（再次复制）'})
    require(c.ok('GET', '/edu/homework/get', x.teacher_token, {'id': again})['status'] == 1, '封存来源复制失败')


def scenario_wrap_up(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, binding = x.bundle()
    token = ready_student(c, x, pupils[0])
    homework_id, _ = x.task(class_id, course_id)
    submit(c, token, homework_id)
    options = c.ok('GET', '/edu/semester/options')
    require(x.semester in json.dumps(options, ensure_ascii=False), '可选学期中没有当前学期')
    c.denied('GET', '/edu/semester/wrap-up/preview', x.teacher_token, {'semesterName': x.semester}, code=FORBIDDEN)
    preview = c.ok('GET', '/edu/semester/wrap-up/preview', params={'semesterName': x.semester})
    require(preview['semesterName'] == x.semester and preview['current'] is True, '收尾预览的学期信息错误')
    row = next((r for r in preview['classCourses'] if r['classCourseId'] == binding), None)
    require(row and row['openTaskCount'] == 1 and row['pendingReviewCount'] == 1 and row['autoCreated'] is False,
            f'收尾预览中本班课的计数错误：{row}')
    require(preview['openTaskCount'] >= 1 and preview['pendingReviewCount'] >= 1, '收尾预览总计数错误')
    c.ok('POST', '/edu/semester/wrap-up/archive',
         body={'semesterName': x.semester, 'classCourseIds': [binding], 'classIds': []})
    rows = c.ok('GET', '/edu/class-course/list-by-class', params={'classId': class_id, 'includeArchived': 'true'})
    require(next(r for r in rows if r['id'] == binding)['status'] == 1, '收尾后班课未归档')
    history = list_items(c.ok('GET', '/edu/student/homework/list', token,
                              {'courseId': course_id, 'scope': 'history', 'pageNo': 1, 'pageSize': 100}))
    require(any(r['homeworkId'] == homework_id for r in history), '归档后学生的往期任务里看不到已提交的任务')


def scenario_gradebook(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle(5)
    tokens = [ready_student(c, x, p) for p in pupils[:4]]
    homework_id, _ = x.task(class_id, course_id)
    graded, pending, redo, resubmitted = (submit(c, t, homework_id, f'gradebook {i}') for i, t in enumerate(tokens))
    for record_id, score in ((graded, 80), (redo, 70), (resubmitted, 65)):
        c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
             body={'id': record_id, 'score': score, 'comment': f'评语 {score}'})
    for record_id in (redo, resubmitted):
        c.ok('PUT', '/edu/homework-record/redo', x.teacher_token, body={'id': record_id, 'redoDeadline': at(days=7)})
    c.ok('PUT', '/edu/student/homework/update', tokens[3], body={'id': resubmitted, 'content': 'second try'})
    snapshot = lambda: sorted((r['id'], r['status'], r['score'], r['updateTime'])  # noqa: E731
                              for r in queue(c, x.teacher_token, homework_id)['list'])
    before = snapshot()
    require([s for _, s, _, _ in before].count(4) == 1, '造数失败：没有已重交记录')
    workbook = c.download('/edu/homework-record/gradebook/export', x.teacher_token, {'homeworkId': homework_id})
    batch = c.form('/edu/data-exchange/imports/HOMEWORK_GRADEBOOK/preview', x.teacher_token,
                   {'optionsJson': json.dumps({'homeworkId': homework_id})}, 'gradebook.xlsx', workbook)
    require(batch['invalidCount'] == 0 and batch['insertedCount'] == 0 and batch['updatedCount'] == 0,
            f'原样导回的预检含写入或错误：{ {k: batch.get(k) for k in ("invalidCount", "insertedCount", "updatedCount")} }')
    require(batch['totalCount'] == 5 and batch['skippedCount'] == 5, f'原样导回应 5 行全部跳过：{batch["skippedCount"]}')
    expected = {'UNCHANGED': 1, 'EMPTY': 2, 'AWAITING_RESUBMIT': 1, 'NOT_SUBMITTED': 1}
    require(batch.get('skipReasonCounts') == expected, f'跳过原因统计错误：{batch.get("skipReasonCounts")}')
    done = confirm_import(c, batch['batchNo'], x.teacher_token, {})
    require(done['status'] == 'SUCCESS' and done['updatedCount'] == 0 and done['insertedCount'] == 0,
            f'确认导入后有写入：{done.get("status")} {done.get("updatedCount")}')
    require(snapshot() == before, '原样导回后提交记录发生变化')


def scenario_message(c):
    x = Ctx(c)
    _, indicator = x.foundation()
    class_id = x.clazz()
    owner, team, helper, outsider = (x.user('teacher') for _ in range(4))
    x.teacher_token = x.login(owner)
    course_id = x.multipart_course({'indicatorId': indicator, 'isPublicCourse': 1, 'courseName': x.name('留言课程'),
                                    'teacherId': owner['id'], 'status': 0, 'teachAssistantIds': str(team['id'])},
                                   c.admin)
    x.bind_with(class_id, course_id, assistantTeacherIds=str(helper['id']))
    pupil = x.user('student', class_id)
    token = ready_student(c, x, pupil)
    tokens = {name: x.login(user) for name, user in
              (('owner', owner), ('team', team), ('helper', helper), ('outsider', outsider))}
    c.ok('POST', '/edu/student/message/send', token,
         body={'classId': class_id, 'courseId': course_id, 'content': 'E2E 留言路由'})
    code = 'edu.teacher.message.created'
    scope = {'classId': class_id, 'courseId': course_id}
    unread = lambda who: [r for r in notices(c, tokens[who], code, **scope) if not r['readStatus']]  # noqa: E731
    for who in ('owner', 'team', 'helper'):
        require(wait_for(lambda: unread(who), 5), f'{who} 未收到学生留言通知')
    require(not notices(c, tokens['outsider'], code, **scope), '非课程团队教师收到了留言通知')
    params = {**scope, 'studentId': pupil['id']}
    c.ok('GET', '/edu/message/detail', tokens['helper'], params)
    require(not unread('helper'), '班课助教打开留言后通知仍未读')
    require(unread('owner') and unread('team'), '一人打开留言却把其他教师的通知也标为已读')
    c.ok('GET', '/edu/message/detail', tokens['owner'], params)
    require(not unread('owner'), '课程负责教师打开留言后通知仍未读')
    c.ok('POST', '/edu/message/reply', tokens['team'], body={**params, 'content': 'E2E 团队回复'})
    reply = 'edu.student.message.reply'
    require(wait_for(lambda: [r for r in notices(c, token, reply, **scope) if not r['readStatus']], 5),
            '学生未收到回复通知')
    c.ok('GET', '/edu/student/message/detail', token, scope)
    require(not [r for r in notices(c, token, reply, **scope) if not r['readStatus']], '学生打开会话后回复通知仍未读')


def scenario_unlock(c):
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    basic, _ = x.task(class_id, course_id, homeworkLevel=1)
    x.task(class_id, course_id, homeworkLevel=2)
    record_id = submit(c, token, basic)

    def levels():
        status = c.ok('GET', '/edu/student/course/unlock-status', token, {'courseId': course_id})
        for level in status['levels']:
            missing = UNLOCK_FIELDS - set(level)
            require(not missing, f'等级 {level.get("level")} 缺少字段 {sorted(missing)}')
            require(level['threshold'] is not None and 0 < level['threshold'] <= 1, '阈值不是 0～1 的小数')
        return {level['level']: level for level in status['levels']}

    waiting = levels()
    require(waiting[1]['requiredCount'] == 1 and waiting[1]['passedCount'] == 0
            and waiting[1]['pendingReviewCount'] == 1, '初级的计数错误')
    require(waiting[2]['unlocked'] is False and (waiting[2]['reasonText'] or '').strip(), '中级未锁定或没有说明原因')
    require(len(waiting[2]['reasonText']) <= 60, '解锁说明超过 60 字')
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token, body={'id': record_id, 'score': 90, 'comment': 'pass'})
    passed = levels()
    require(passed[1]['passedCount'] == 1 and passed[1]['ratio'] == 1 and passed[2]['unlocked'] is True,
            '初级达标后中级未解锁')
    return f'锁定说明：{waiting[2]["reasonText"]}'


def scenario_attachment(c):
    expected = {'maxCount': config_int(c, 'edu.homework.attachment.max-count', 10),
                'maxFileSizeMb': config_int(c, 'edu.homework.attachment.max-file-mb', 100),
                'maxTotalSizeMb': config_int(c, 'edu.homework.attachment.max-total-mb', 300)}
    x = Ctx(c)
    class_id, teacher, pupils, course_id, _ = x.bundle()
    token = ready_student(c, x, pupils[0])
    mixed, _ = x.task(class_id, course_id, submissionMode='mixed')
    text, _ = x.task(class_id, course_id)
    limits = c.ok('GET', '/edu/student/homework/get', token, {'homeworkId': mixed}).get('attachmentLimits')
    require(isinstance(limits, dict), '学生任务详情没有 attachmentLimits')
    require({k: limits.get(k) for k in expected} == expected, f'附件上限与配置不一致：{limits}')
    require(limits.get('allowedTypes') == ['audio', 'image', 'video'], f'图文音视频任务的附件类型错误：{limits}')
    plain = c.ok('GET', '/edu/student/homework/get', token, {'homeworkId': text}).get('attachmentLimits')
    require(isinstance(plain, dict) and plain.get('allowedTypes') == [], f'纯文本任务应不限附件类型：{plain}')
    return f'上限 {expected["maxCount"]} 个、单个 {expected["maxFileSizeMb"]} MB、合计 {expected["maxTotalSizeMb"]} MB'


def scenario_ai_governance(c):
    rows = c.ok('GET', '/ai/prompt-template/list')
    codes = {r['businessCode'] for r in rows}
    require(PROMPT_CODES <= codes, f'提示词列表缺少业务：{sorted(PROMPT_CODES - codes)}')
    require(all(r.get('activeVersion') and (r.get('content') or '').strip() for r in rows if r['businessCode'] in PROMPT_CODES),
            '提示词没有启用版本或内容为空')
    page = c.ok('GET', '/ai/call-log/page', params={'pageNo': 1, 'pageSize': 10})
    require(isinstance(page.get('list'), list) and isinstance(page.get('total'), int), '调用日志分页结构错误')
    summary = c.ok('GET', '/ai/call-log/daily-summary')
    require(isinstance(summary, list), '调用日志汇总结构错误')
    x = Ctx(c)
    teacher_token = x.login(x.user('teacher'))
    c.denied('GET', '/ai/prompt-template/list', teacher_token, code=FORBIDDEN)
    c.denied('GET', '/ai/call-log/page', teacher_token, {'pageNo': 1, 'pageSize': 10}, code=FORBIDDEN)
    # 历史抽屉：升级写入的第 1 版创建者是 system，也要能列出
    for code in sorted(PROMPT_CODES):
        status, payload = c.call('GET', '/ai/prompt-template/history', c.admin, {'businessCode': code})
        require(status == 200 and payload.get('code') == 0,
                f'提示词历史 businessCode={code} 失败：code={payload.get("code")}，msg={payload.get("msg")}')
        require(any(h['active'] for h in payload['data'] or []), f'{code} 没有历史版本或启用版本')
    return f'提示词 {len(rows)} 项，调用日志 {page["total"]} 条'


def scenario_public_audit(c):
    x = Ctx(c)
    dept, _ = x.foundation()
    roles = {r['code']: r['id'] for r in c.ok('GET', '/system/role/simple-list')}
    require('school_admin' in roles and 'college_admin' in roles, '基线缺少学校或学院教务管理员角色')

    def staff(role_code):
        user = {'username': x.username('a'), 'password': x.password(), 'nickname': x.name('教务')}
        c.ok('POST', '/system/user/create', body={**user, 'deptId': dept, 'roleIds': [roles[role_code]]})
        return x.login(user)

    approver, college = staff('school_admin'), staff('college_admin')
    teacher = x.user('teacher')
    x.teacher_token = x.login(teacher)
    course_id = x.course(teacher['id'], public=True)
    course_name = c.ok('GET', '/capacity/course/get', params={'id': course_id})['courseName']
    require(wait_for(lambda: notices(c, approver, 'edu.admin.public-course.pending', courseName=course_name), 5),
            '学校教务管理员未收到公共课待审批通知')
    require(not notices(c, college, 'edu.admin.public-course.pending', courseName=course_name),
            '学院教务管理员收到了待审批通知')
    page = list_items(c.ok('GET', '/capacity/course/page', approver,
                           {'pageNo': 1, 'pageSize': 20, 'courseName': course_name}))
    require(any(r['id'] == course_id for r in page), '学校教务管理员在课程列表里看不到待审批课程')
    pending = c.ok('GET', '/capacity/course/get', approver, {'id': course_id})['publicAuditStatus']
    audit = {'id': course_id, 'approved': True, 'remark': 'E2E 学校审批'}
    c.denied('POST', '/capacity/course/audit-public', college, body=audit, code=FORBIDDEN)
    c.denied('POST', '/capacity/course/audit-public', x.teacher_token, body=audit, code=FORBIDDEN)
    # 审批通过会把课程同步到全部班级并补发，数据多时会超过普通请求的 12 秒，单独计时
    began = time.monotonic()
    status, _, raw = c.send('POST', '/capacity/course/audit-public', approver, data=json.dumps(audit).encode(),
                            content_type='application/json', timeout=SEMESTER_TIMEOUT)
    audit_seconds = time.monotonic() - began
    payload = json.loads(raw or b'{}')
    require(status == 200 and payload.get('code') == 0,
            f'学校教务管理员审批失败：HTTP {status}，code={payload.get("code")}，msg={payload.get("msg")}')
    approved = c.ok('GET', '/capacity/course/get', approver, {'id': course_id})
    require(approved['publicAuditStatus'] != pending and approved.get('publicAuditUserId'), '审批后课程状态未变化')
    require(wait_for(lambda: notices(c, x.teacher_token, 'edu.teacher.public-course.audit-result',
                                     courseName=course_name), 5), '申请教师未收到审批结果通知')
    return f'审批请求耗时 {audit_seconds:.0f} 秒'


SCENARIOS = {
    '批改队列不截断': scenario_queue,
    '批改冲突': scenario_conflict,
    '撤销与延时通知': scenario_undo,
    '重复提交': scenario_duplicate,
    '批改方式归一': scenario_grading_mode,
    'AI 初评排队': scenario_ai_queue,
    '个别延期': scenario_extension,
    '催交与限频': scenario_remind,
    '复制任务': scenario_copy,
    '学期收尾': scenario_wrap_up,
    '成绩册原样导回': scenario_gradebook,
    '留言通知与已读': scenario_message,
    '解锁说明': scenario_unlock,
    '附件上限': scenario_attachment,
    'AI 治理接口': scenario_ai_governance,
    '公共课审批权限': scenario_public_audit,
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=SCENARIOS, action='append', help='只执行指定场景，可重复')
    parser.add_argument('--dry-run', action='store_true', help='只列场景和接口路径，不发请求')
    parser.add_argument('--queue-size', type=int, default=201, help='批改队列场景的提交份数（默认 201，超过旧的 200 上限）')
    parser.add_argument('--debug', action='store_true', help='失败时把调用栈打印到标准错误（不含口令）')
    parser.add_argument('--keep-data', action='store_true', help='结束后不归档本次新建的班级与公共课')
    args = parser.parse_args()
    selected = args.only or list(SCENARIOS)
    if args.dry_run:
        for name in selected:
            paths = SETUP_PATHS + PATHS[name]
            print(f'{name}: ' + ', '.join(PREFIX + p for p in dict.fromkeys(paths)))
        return 0
    try:
        base = local_url(os.environ.get('NUS_E2E_BASE_URL'))
        username = os.environ['NUS_E2E_ADMIN_USERNAME']
        password = Path(os.environ['NUS_E2E_ADMIN_PASSWORD_FILE']).read_text(encoding='utf-8').rstrip('\r\n')
        require(bool(username and password), '管理员用户名或口令为空')
        require(args.queue_size > 200, '--queue-size 须大于 200')
    except (KeyError, OSError, ValueError, AssertionError):
        print('配置错误：缺少或不合法的本机 URL、管理员用户名、口令文件或队列份数', file=sys.stderr)
        return 2
    client = Client17(base)
    try:
        client.admin = client.login(username, password)['accessToken']
    except Exception:
        print('管理员登录失败（响应与凭据已隐藏）', file=sys.stderr)
        return 2
    passed = failed = 0
    for name in selected:
        started = time.monotonic()
        try:
            function = SCENARIOS[name]
            note = function(client, args.queue_size) if function is scenario_queue else function(client)
            extra = f'（{note}）' if note else ''
            print(f'PASS {name}：关键接口和业务状态断言通过{extra}，{time.monotonic() - started:.0f} 秒', flush=True)
            passed += 1
        except Exception as exc:
            reason = str(exc) if isinstance(exc, AssertionError) else type(exc).__name__
            print(f'FAIL {name}：{reason}', flush=True)
            if args.debug:
                traceback.print_exc()
            failed += 1
    if 'switch_seconds' in SHARED:
        level = 'WARN' if SHARED['switch_seconds'] > 12 else 'INFO'
        print(f'{level} 切换当前学期耗时 {SHARED["switch_seconds"]:.0f} 秒（e2e_v1015 每个场景切换一次，请求超时 12 秒）')
    if not args.keep_data:
        archived = [path for path, ids in (('/edu/class/delete', CREATED['classes']),
                                           ('/capacity/course/delete', CREATED['public_courses']))
                    for item in ids if client.call('DELETE', path, client.admin, {'id': item})[1].get('code') == 0]
        print(f'INFO 已归档本次新建的班级与公共课 {len(archived)} 个（--keep-data 可保留）')
    print(f'汇总：PASS {passed}，FAIL {failed}')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
