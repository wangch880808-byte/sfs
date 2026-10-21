#!/usr/bin/env python3
"""给本机联调环境灌一套演示数据，供界面截图与人工走查使用。只连本机后端。

数据：一个学院、一个班、一名教师、三名学生、一门课程（含开课班级）、四个实训任务，
以及已批改、已打回、待批改三种提交和一段师生留言。
账号口令随机生成，连同账号名写入状态目录的 demo-accounts.json（权限 0600），不打印；
页面截图需要的业务编号写入 demo-ids.json。

用法（先按 deploy/tests/e2e/local-env.sh 启动环境）：
  NUS_E2E_BASE_URL=http://127.0.0.1:48280 python3 deploy/tests/ui/seed_demo.py
"""
import datetime as dt
import importlib.util
import json
import os
import secrets
import string
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STATE = Path(os.environ.get('NUS_E2E_STATE', Path.home() / '.cache' / 'nus-e2e'))
spec = importlib.util.spec_from_file_location('e2e', ROOT / 'deploy/tests/e2e/e2e_v1015.py')
e2e = importlib.util.module_from_spec(spec)
spec.loader.exec_module(e2e)


def password():
    alphabet = string.ascii_letters + string.digits
    while True:
        value = ''.join(secrets.choice(alphabet) for _ in range(12))
        if any(c.isdigit() for c in value) and any(c.isalpha() for c in value):
            return 'Nu' + value


def write_private(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    os.chmod(path, 0o600)


def main():
    base = e2e.local_url(os.environ.get('NUS_E2E_BASE_URL', 'http://127.0.0.1:48280'))
    c = e2e.Client(base)
    admin_pw = (STATE / 'admin.pw').read_text(encoding='utf-8').rstrip('\r\n')
    c.admin = c.login('admin', admin_pw)['accessToken']
    x = e2e.Context(c)
    today = dt.date.today()
    semester = f'{today.year}-{today.year + 1}-1' if today.month >= 8 else f'{today.year - 1}-{today.year}-2'
    start = today - dt.timedelta(days=30)
    end = today + dt.timedelta(days=90)
    c.ok('PUT', '/edu/semester/current', body={
        'semesterName': semester, 'semesterStartDate': start.isoformat(), 'semesterEndDate': end.isoformat()})
    x.semester, x.semester_ready = semester, True

    depts = c.ok('GET', '/system/dept/list-all-simple')
    root = next((d for d in depts if not d.get('parentId')), depts[0])
    dept_id = c.ok('POST', '/system/dept/create', body={
        'name': '信息工程学院', 'parentId': root['id'], 'sort': 10, 'status': 0, 'deptType': 'COLLEGE'})
    x.dept = dept_id
    x.foundation()
    class_id = c.ok('POST', '/edu/class/create', body={
        'className': f'软件工程 {today.year - 1} 级 1 班', 'gradeYear': today.year - 1, 'deptId': dept_id,
        'educationLevel': 2, 'schoolingYear': 4, 'status': 0})

    accounts = {}

    def create(kind, username, nickname):
        pw = password()
        body = {'username': username, 'nickname': nickname, 'password': pw, 'deptId': dept_id}
        if kind == 'student':
            body['profile'] = {'gradeYear': today.year - 1, 'classIds': [class_id]}
        user_id = c.ok('POST', '/system/user/create-' + kind, body=body)
        accounts[username] = {'id': user_id, 'nickname': nickname, 'password': pw, 'role': kind}
        return user_id

    teacher_id = create('teacher', 'demoteacher', '王敏')
    for username, nickname in (('demostudent1', '李晓雨'), ('demostudent2', '张子涵'), ('demostudent3', '赵一鸣')):
        create('student', username, nickname)
    x.teacher_token = c.login('demoteacher', accounts['demoteacher']['password'])['accessToken']

    course_id = x.multipart_course({
        'indicatorId': x.indicator, 'isPublicCourse': 1, 'courseName': 'Web 前端开发实训',
        'courseIntroduction': '从页面结构、样式到交互逻辑，完成一个可上线的前端小项目。',
        'teacherId': teacher_id, 'status': 0}, c.admin)
    x.bind(class_id, course_id)

    homework_ids = []
    for name, level, days in (('页面结构与语义化标签', 1, 7), ('响应式布局练习', 1, 10),
                              ('表单校验与交互反馈', 2, 14), ('组件化重构', 2, 21)):
        body = {'homeworkName': name, 'homeworkType': 1, 'homeworkLevel': level, 'courseId': course_id,
                'classId': class_id, 'semesterName': semester,
                'requirement': '按任务说明完成，并提交实现思路与关键截图说明。',
                'deadline': (dt.datetime.now() + dt.timedelta(days=days)).strftime('%Y-%m-%d %H:%M:%S'),
                'autoGrade': 0, 'submissionMode': 'text', 'aiReviewMode': 0, 'passScore': 60,
                'score': 100, 'status': 1}
        homework_ids.append(c.ok('POST', '/edu/homework/create', x.teacher_token, body=body))

    tokens = {}
    for username in ('demostudent1', 'demostudent2', 'demostudent3'):
        old = accounts[username]['password']
        token = c.login(username, old)['accessToken']
        new = password()
        c.ok('PUT', '/edu/student/profile/password', token, body={'oldPassword': old, 'newPassword': new})
        accounts[username]['password'] = new
        tokens[username] = c.login(username, new)['accessToken']

    def submit(username, homework_id, content):
        return c.ok('POST', '/edu/student/homework/submit', tokens[username],
                    body={'homeworkId': homework_id, 'content': content})

    graded = submit('demostudent1', homework_ids[0], '使用 header、nav、main、footer 划分页面结构，并为图片补充 alt 文本。')
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
         body={'id': graded, 'score': 88, 'comment': '结构清晰，语义化标签使用得当；注意标题层级不要跳级。'})
    redo = submit('demostudent2', homework_ids[0], '页面用 div 实现，补充了部分注释。')
    c.ok('PUT', '/edu/homework-record/correct', x.teacher_token,
         body={'id': redo, 'score': 52, 'comment': '大部分区域仍用 div，请改用语义化标签后重新提交。'})
    c.ok('PUT', '/edu/homework-record/redo', x.teacher_token,
         body={'id': redo, 'redoDeadline': (dt.datetime.now() + dt.timedelta(days=5)).strftime('%Y-%m-%d %H:%M:%S')})
    pending = submit('demostudent3', homework_ids[0], '完成了页面骨架，导航与页脚使用语义化标签。')
    submit('demostudent1', homework_ids[1], '使用 flex 与媒体查询完成三种断点的布局。')

    c.ok('POST', '/edu/student/message/send', tokens['demostudent1'],
         body={'classId': class_id, 'courseId': course_id, 'content': '老师，响应式布局需要兼容到多窄的屏幕？'})
    c.ok('POST', '/edu/message/reply', x.teacher_token,
         body={'classId': class_id, 'courseId': course_id, 'studentId': accounts['demostudent1']['id'],
               'content': '以 360px 宽为下限即可，记得在说明里写出断点。'})
    c.ok('POST', '/edu/student/message/send', tokens['demostudent3'],
         body={'classId': class_id, 'courseId': course_id, 'content': '任务一已提交，麻烦老师有空批改。'})

    STATE.mkdir(parents=True, exist_ok=True)
    write_private(STATE / 'demo-accounts.json', accounts)
    ids = {'classId': class_id, 'courseId': course_id, 'homeworkIds': homework_ids,
           'records': {'graded': graded, 'redo': redo, 'pending': pending}, 'semester': semester}
    (STATE / 'demo-ids.json').write_text(json.dumps(ids, ensure_ascii=False, indent=2), encoding='utf-8')
    print('demo data ready:', json.dumps(ids, ensure_ascii=False))


if __name__ == '__main__':
    sys.exit(main())
