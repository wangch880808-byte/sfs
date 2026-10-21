#!/usr/bin/env python3
"""V1.0.1.10 公共课教师认领班级的端到端验收（只连本机一次性环境）。

同一门已上线的公共课里：负责人、两位普通教师、一位课程团队成员、管理员。
场景：认领与认领选项、只看自己的班级、布置任务、放弃与清除、统计越权、留言接收。
运行方式与 e2e_v1018.py 相同（NUS_E2E_BASE_URL、NUS_E2E_ADMIN_USERNAME、NUS_E2E_ADMIN_PASSWORD_FILE）。
"""
import argparse
import concurrent.futures
import os
from pathlib import Path
import sys
import time
import traceback

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from e2e_v1015 import PREFIX, local_url, ready_student, require  # noqa: E402
from e2e_v1017 import (Client17, CREATED, SHARED, FORBIDDEN, SETUP_PATHS, notices, submit,  # noqa: E402
                       wait_for)
from e2e_v1018 import Ctx18, long_json  # noqa: E402

CLAIM = '/edu/class-course/claim'
OPTIONS = '/edu/class-course/claim-options'
RELEASE = '/edu/class-course/release'
DETAIL = '/edu/course-manage/detail'
PATHS = {
    '认领与选项': [OPTIONS, CLAIM, '/capacity/course/audit-public', '/edu/class-course/list-by-class'],
    '只看自己的班级': [DETAIL, '/edu/class-course/list-by-course', '/edu/course-manage/class-detail'],
    '布置任务': ['/edu/homework/create', DETAIL, '/edu/homework/copy/targets', '/edu/homework/get'],
    '放弃与清除': [RELEASE, CLAIM, '/edu/student/homework/submit', '/edu/homework-record/correct',
              '/edu/homework/create'],
    '统计越权': ['/edu/statistics/course-options', '/edu/statistics/class-options',
             '/edu/statistics/work-trend', '/edu/statistics/course-analysis'],
    '留言接收': ['/edu/student/message/send', '/system/notify-message/my-page', '/edu/message/student-list'],
    '负责人、封存与删除': [DETAIL, '/edu/homework/create', '/edu/homework/delete', '/edu/homework/remove-impact',
                  '/edu/homework/remove', '/edu/homework/remove-list'],
}
SECRETS = []
WORLD = {}


def world(c):
    """一门已上线公共课 + 4 个班 + 负责人、两位教师、团队成员；各场景共用，按顺序执行。"""
    if WORLD:
        return WORLD
    x = Ctx18(c)
    _, indicator = x.foundation()
    owner, t1, t2, team = (x.user('teacher') for _ in range(4))
    for user in (owner, t1, t2, team):
        SECRETS.append(user['password'])
    classes = [x.clazz() for _ in range(4)]
    x.teacher_token = x.login(owner)
    course = x.multipart_course({'indicatorId': indicator, 'isPublicCourse': 0, 'courseName': x.name('认领公共课'),
                                 'teacherId': owner['id'], 'teachAssistantIds': str(team['id']), 'status': 0},
                                x.teacher_token)
    CREATED['public_courses'].append(course)
    long_json(c, 'POST', '/capacity/course/audit-public', {'id': course, 'approved': True, 'remark': '认领验收'})

    def linked(class_id):
        rows = c.ok('GET', '/edu/class-course/list-by-class', params={'classId': class_id, 'includeArchived': 'true'})
        return next((r for r in rows if r['courseId'] == course and r['status'] == 0), None)
    for class_id in classes:
        require(wait_for(lambda: linked(class_id), 60), '公共课未自动关联班级')
    WORLD.update(x=x, course=course, classes=classes, owner=owner, t1=t1, t2=t2, team=team,
                 tokens={name: x.login(user) for name, user in
                         (('owner', owner), ('t1', t1), ('t2', t2), ('team', team))})
    return WORLD


def options(c, token, course):
    return {r['classId']: r for r in c.ok('GET', OPTIONS, token, {'courseId': course})}


def claim(c, token, course, class_ids):
    return c.ok('POST', CLAIM, token, body={'courseId': course, 'classIds': class_ids})


def detail(c, token, course):
    return c.ok('GET', DETAIL, token, {'courseId': course})


def task_body(w, class_id, name):
    x = w['x']
    return {'homeworkName': x.name(name), 'homeworkType': 1, 'homeworkLevel': 1, 'courseId': w['course'],
            'classId': class_id, 'semesterName': x.semester, 'requirement': '认领验收',
            'deadline': (time.strftime('%Y-%m-%d', time.localtime(time.time() + 30 * 86400)) + ' 23:59:00'),
            'autoGrade': 0, 'submissionMode': 'text', 'aiReviewMode': 0, 'passScore': 60, 'score': 100, 'status': 1}


def scenario_claim(c):
    w = world(c)
    course, (a, b, cc, d) = w['course'], w['classes']
    tk = w['tokens']
    first = options(c, tk['t1'], course)
    require(all(first[k]['state'] == 'AVAILABLE' for k in (a, b, cc, d)), '新上线公共课的班级应都可认领')
    claim(c, tk['t1'], course, [a])
    mine = options(c, tk['t1'], course)
    require(mine[a]['state'] == 'MINE', '自己认领的班级应标为已认领')
    seen = c.ok('GET', OPTIONS, tk['t2'], {'courseId': course})
    taken = next(r for r in seen if r['classId'] == a)
    require(taken['state'] == 'TAKEN' and taken['claimTeacherName'] == w['t1']['nickname'], '他人认领的班级未标明认领人')
    states = [r['state'] for r in seen]
    order = {'AVAILABLE': 0, 'MINE': 1, 'TAKEN': 2}
    require(states == sorted(states, key=order.get), '认领选项未按可认领、已认领、他人认领排序')
    c.denied('POST', CLAIM, tk['t2'], body={'courseId': course, 'classIds': [a, b]}, phrase='已被')
    require(options(c, tk['t2'], course)[b]['state'] == 'AVAILABLE', '整体失败的认领不应部分生效')
    claim(c, tk['t2'], course, [b, cc])
    # 两位教师同时认领同一班级，只能有一人成功
    def grab(token):
        status, payload = c.call('POST', CLAIM, token, body={'courseId': course, 'classIds': [d]})
        return payload.get('code') == 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(grab, (tk['t1'], tk['t2'])))
    require(results.count(True) == 1, f'并发认领同一班级的结果异常：{results}')
    winner = 't1' if results[0] else 't2'
    WORLD['d_owner'] = winner
    require(options(c, tk[winner], course)[d]['state'] == 'MINE', '并发认领后认领人不一致')
    return f'并发认领由 {winner} 获得'


def scenario_visibility(c):
    w = world(c)
    course, (a, b, cc, d) = w['course'], w['classes']
    tk = w['tokens']
    mine_t1 = {a} | ({d} if WORLD.get('d_owner') == 't1' else set())
    view = detail(c, tk['t1'], course)
    require(view.get('claimMode') is True and not view.get('viewAllReadOnly'), '普通教师的公共课详情标记错误')
    require({r['classId'] for r in view['classes'] if not r.get('archived')} == mine_t1, '教师看到了未认领的班级')
    require(all(r['canOperate'] for r in view['classes']), '教师自己认领的班级应可操作')
    rows = c.ok('GET', '/edu/class-course/list-by-course', tk['t1'], {'courseId': course})
    require({r['classId'] for r in rows} == mine_t1, 'list-by-course 未按认领收窄')
    owner_view = detail(c, tk['owner'], course)
    require(owner_view.get('viewAllReadOnly') is True, '负责人应只读查看全部')
    owner_classes = {r['classId']: r for r in owner_view['classes']}
    require({a, b, cc, d} <= set(owner_classes), '负责人看不到全部班级')
    require(not owner_classes[a]['canOperate'] and owner_classes[a]['claimTeacherName'] == w['t1']['nickname'],
            '负责人不应能操作他人认领的班级，且应看到认领人')
    team_view = detail(c, tk['team'], course)
    require(not [r for r in team_view['classes'] if not r.get('archived')], '团队成员没有认领却看到了班级')
    admin_view = detail(c, c.admin, course)
    require({a, b, cc, d} <= {r['classId'] for r in admin_view['classes']}, '管理员看不到全部班级')
    b_link = next(r['classCourseId'] for r in owner_view['classes'] if r['classId'] == b)
    c.denied('GET', '/edu/course-manage/class-detail', tk['t1'], {'classCourseId': b_link})
    owner_detail = c.ok('GET', '/edu/course-manage/class-detail', tk['owner'], {'classCourseId': b_link})
    require(owner_detail.get('canOperate') is False and owner_detail.get('teacherName') == w['t2']['nickname'],
            '负责人查看班级详情应只读，授课教师应为认领人')


def scenario_assign(c):
    w = world(c)
    course, (a, b, _, _) = w['course'], w['classes']
    tk = w['tokens']
    homework = c.ok('POST', '/edu/homework/create', tk['t1'], body=task_body(w, a, '认领班任务'))
    WORLD['t1_homework'] = homework
    c.denied('POST', '/edu/homework/create', tk['t1'], body=task_body(w, b, '越权任务'))
    c.denied('POST', '/edu/homework/create', tk['owner'], body=task_body(w, a, '负责人越权任务'))
    t2_homework = c.ok('POST', '/edu/homework/create', tk['t2'], body=task_body(w, b, '二号老师任务'))
    WORLD['t2_homework'] = t2_homework
    t1_tasks = {t['homeworkId'] for t in detail(c, tk['t1'], course)['tasks']}
    require(homework in t1_tasks and t2_homework not in t1_tasks, '教师看到了别人布置的任务')
    owner_tasks = {t['homeworkId'] for t in detail(c, tk['owner'], course)['tasks']}
    require({homework, t2_homework} <= owner_tasks, '负责人看不到全部任务')
    c.ok('GET', '/edu/homework/get', tk['owner'], {'id': homework})
    targets = c.ok('GET', '/edu/homework/copy/targets', tk['t1'])
    target_classes = {t['classId'] for t in targets if t.get('courseId') == course}
    require(b not in target_classes, '复制目标出现了别人认领的班级')


def scenario_release(c):
    w = world(c)
    course, (a, b, cc, _) = w['course'], w['classes']
    tk, x = w['tokens'], w['x']
    pupil = x.user('student', a)
    SECRETS.append(pupil['password'])
    token = ready_student(c, x, pupil)
    record = submit(c, token, WORLD['t1_homework'])
    a_link = next(r['classCourseId'] for r in detail(c, tk['t1'], course)['classes'] if r['classId'] == a)
    c.denied('PUT', RELEASE, tk['t2'], {'id': a_link})
    c.ok('PUT', RELEASE, tk['t1'], {'id': a_link})
    require(a not in {r['classId'] for r in detail(c, tk['t1'], course)['classes'] if not r.get('archived')},
            '放弃后仍显示该班级')
    c.ok('GET', '/edu/homework/get', tk['t1'], {'id': WORLD['t1_homework']})
    c.ok('PUT', '/edu/homework-record/correct', tk['t1'], body={'id': record, 'score': 88})
    c.denied('POST', '/edu/homework/create', tk['t1'], body=task_body(w, a, '放弃后任务'))
    claim(c, tk['t2'], course, [a])
    c_link = next(r['classCourseId'] for r in detail(c, c.admin, course)['classes'] if r['classId'] == cc)
    c.ok('PUT', RELEASE, c.admin, {'id': c_link})
    require(options(c, tk['t1'], course)[cc]['state'] == 'AVAILABLE', '管理员清除后班级应可认领')


def scenario_stats(c):
    w = world(c)
    course, (a, b, _, _) = w['course'], w['classes']
    tk = w['tokens']
    courses = {r['id'] if 'id' in r else r.get('courseId') for r in c.ok('GET', '/edu/statistics/course-options', tk['t2'])}
    require(course in courses, '认领教师的课程选项里没有这门公共课')
    classes = c.ok('GET', '/edu/statistics/class-options', tk['t2'], {'courseId': course})
    ids = {r['id'] if 'id' in r else r.get('classId') for r in classes}
    require(b in ids, '认领教师的班级选项里没有自己的班级')
    # 团队成员与普通教师一样：没有认领班级时不能查这门公共课的统计
    c.denied('GET', '/edu/statistics/class-options', tk['team'], {'courseId': course})
    c.denied('GET', '/edu/statistics/work-trend', tk['team'], {'courseId': course, 'classId': b})
    c.denied('GET', '/edu/statistics/course-analysis', tk['team'], {'courseId': course, 'classId': b})
    c.ok('GET', '/edu/statistics/course-analysis', tk['t2'], {'courseId': course, 'classId': b})


def scenario_message(c):
    w = world(c)
    course, (_, b, _, _) = w['course'], w['classes']
    tk, x = w['tokens'], w['x']
    pupil = x.user('student', b)
    SECRETS.append(pupil['password'])
    token = ready_student(c, x, pupil)
    c.ok('POST', '/edu/student/message/send', token, body={'classId': b, 'courseId': course, 'content': '认领留言'})
    code, scope = 'edu.teacher.message.created', {'classId': b, 'courseId': course}
    require(wait_for(lambda: notices(c, tk['t2'], code, **scope), 10), '认领教师未收到学生留言通知')
    for who in ('t1', 'team'):
        require(not notices(c, tk[who], code, **scope), f'{who} 不应收到其他教师班级的留言通知')
    c.denied('GET', '/edu/message/student-list', tk['t1'],
             {'classId': b, 'courseId': course, 'pageNo': 1, 'pageSize': 10})
    c.ok('GET', '/edu/message/student-list', tk['t2'], {'classId': b, 'courseId': course, 'pageNo': 1, 'pageSize': 10})


def scenario_owner_remove(c):
    """V1.0.1.10 追加：课程详情的负责人与任务状态；任务删除只给布置者和管理员。"""
    w = world(c)
    course, (a, b, _, _) = w['course'], w['classes']
    tk = w['tokens']
    view = detail(c, tk['owner'], course)
    for row in view['classes']:
        if row.get('claimTeacherId'):
            require(row.get('ownerId') == row['claimTeacherId'] and row.get('ownerName') == row.get('claimTeacherName'),
                    f'公共课班级负责人应为认领人：{row}')
        else:
            require(row.get('ownerId') is None and row.get('ownerName') == '未认领', f'未认领的班级负责人应为「未认领」：{row}')
    tasks = {t['homeworkId']: t for t in view['tasks']}
    t2_homework = WORLD['t2_homework']
    require(tasks[t2_homework].get('teacherId') == w['t2']['id'] and tasks[t2_homework].get('teacherName')
            and tasks[t2_homework].get('status') == 1, f'任务负责人或状态不对：{tasks.get(t2_homework)}')
    # 封存的任务仍出现在课程详情里，状态为 3
    sealed = c.ok('POST', '/edu/homework/create', tk['t2'], body=task_body(w, b, '封存后删除'))
    c.ok('DELETE', '/edu/homework/delete', tk['t2'], {'id': sealed})
    row = next((t for t in detail(c, tk['t2'], course)['tasks'] if t['homeworkId'] == sealed), None)
    require(row and row.get('status') == 3, f'课程详情应包含已封存的任务：{row}')
    # 删除：别的教师、课程负责人都不能删；布置者能删（含已封存）；管理员能删别人的
    for who in ('t1', 'owner', 'team'):
        c.denied('GET', '/edu/homework/remove-impact', tk[who], {'ids': str(sealed)})
        c.denied('DELETE', '/edu/homework/remove', tk[who], {'id': sealed})
    c.denied('DELETE', '/edu/homework/remove-list', tk['t2'], {'ids': f'{sealed},{WORLD["t1_homework"]}'})
    require(any(t['homeworkId'] == sealed for t in detail(c, tk['t2'], course)['tasks']), '整批删除失败时不应删掉任何任务')
    impact = c.ok('GET', '/edu/homework/remove-impact', tk['t2'], {'ids': str(sealed)})
    require(impact['homeworkCount'] == 1 and impact['submittedCount'] == 0, f'删除影响范围错误：{impact}')
    c.ok('DELETE', '/edu/homework/remove', tk['t2'], {'id': sealed})
    require(all(t['homeworkId'] != sealed for t in detail(c, tk['t2'], course)['tasks']), '删除的任务仍在课程详情里')
    extra = c.ok('POST', '/edu/homework/create', tk['t2'], body=task_body(w, b, '管理员删除'))
    c.ok('DELETE', '/edu/homework/remove-list', None, {'ids': str(extra)})
    require(all(t['homeworkId'] != extra for t in detail(c, tk['owner'], course)['tasks']), '管理员删除未生效')


SCENARIOS = {'认领与选项': scenario_claim, '只看自己的班级': scenario_visibility, '布置任务': scenario_assign,
             '放弃与清除': scenario_release, '统计越权': scenario_stats, '留言接收': scenario_message,
             '负责人、封存与删除': scenario_owner_remove}


def redact(value):
    text = str(value)
    for secret in SECRETS:
        text = text.replace(secret, '[口令已隐藏]')
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='只列场景和接口路径，不发请求')
    parser.add_argument('--debug', action='store_true', help='失败时打印调用栈（口令隐藏）')
    parser.add_argument('--keep-data', action='store_true', help='结束后不归档本次新建的班级与公共课')
    args = parser.parse_args()
    if args.dry_run:
        for name in SCENARIOS:
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
    client.admin = client.login(username, password)['accessToken']
    passed = failed = 0
    # 场景按顺序共享同一门公共课，前一个失败时后面的结果仅供参考
    for name, scenario in SCENARIOS.items():
        started = time.monotonic()
        try:
            note = scenario(client)
            extra = f'（{note}）' if note else ''
            print(f'PASS {name}：关键接口和业务状态断言通过{extra}，{time.monotonic() - started:.0f} 秒', flush=True)
            passed += 1
        except Exception as exc:
            reason = redact(exc) if isinstance(exc, AssertionError) else type(exc).__name__ + ': ' + redact(exc)
            print(f'FAIL {name}：{reason}', flush=True)
            if args.debug:
                print(redact(traceback.format_exc()), file=sys.stderr)
            failed += 1
    if not args.keep_data:
        archived = [path for path, ids in (('/edu/class/delete', CREATED['classes']),
                                           ('/capacity/course/delete', CREATED['public_courses']))
                    for item in ids if client.call('DELETE', path, client.admin, {'id': item})[1].get('code') == 0]
        print(f'INFO 已归档本次新建的班级与公共课 {len(archived)} 个（--keep-data 可保留）')
    if 'switch_seconds' in SHARED:
        print(f'INFO 切换当前学期耗时 {SHARED["switch_seconds"]:.0f} 秒')
    print(f'汇总：PASS {passed}，FAIL {failed}')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
