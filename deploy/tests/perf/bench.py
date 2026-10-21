"""性能基准：按角色调用主要查询接口，预热后测 3 次取中位数，并用 performance_schema 统计每次请求的
SQL 条数、耗时、扫描行数（测量前所有接口先完整预热两轮）；同时保存每个接口的返回 JSON，供改造前后逐项比对（compare.py）。
只连本机一次性环境。用法：python3 bench.py --seed seed.json --out <目录>
"""
import argparse, json, os, re, statistics, subprocess, sys, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'e2e'))
from e2e_v1017 import Client17

parser = argparse.ArgumentParser()
parser.add_argument('--seed', default=str(Path(__file__).with_name('seed.json')))
parser.add_argument('--out', required=True)
args = parser.parse_args()
STATE = Path(os.environ.get('NUS_E2E_STATE', Path(os.environ['HOME'], '.cache/nus-e2e')))
BASE = os.environ.get('NUS_E2E_BASE_URL', 'http://127.0.0.1:48280')
if not BASE.startswith(('http://127.0.0.1:', 'http://localhost:')):
    raise SystemExit('只允许连接本机一次性环境')
seed = json.load(open(args.seed))
OUT = Path(args.out)
(OUT / 'snapshots').mkdir(parents=True, exist_ok=True)


def sql(text):
    out = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + (STATE / 'db.pw').read_text().strip(),
                          os.environ.get('NUS_E2E_MYSQL_NAME', 'nus-e2e-mysql'), 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', 'nus'], input=text.encode(), capture_output=True)
    return out.stdout.decode().strip()


def sql_stats():
    row = sql("SELECT COUNT(*), IFNULL(ROUND(SUM(TIMER_WAIT)/1e9,1),0), IFNULL(SUM(ROWS_EXAMINED),0) "
              "FROM performance_schema.events_statements_history_long WHERE CURRENT_SCHEMA='nus'")
    return row.split('\t') if row else ['0', '0', '0']


c = Client17(BASE)
admin = c.login('admin', (STATE / 'admin.pw').read_text().strip())['accessToken']
c.admin = admin
suffix = seed['suffix']
class_id, course_id = seed['bindings'][0]
big_class = seed['classes'][0][0]
teacher_id = int(sql(f"SELECT teacher_id FROM capacity_course WHERE id={course_id}"))
c.ok('PUT', '/system/user/update-password', body={'id': teacher_id, 'password': 'A1bench' + suffix})
teacher_name = sql(f"SELECT username FROM system_users WHERE id={teacher_id}")
teacher = c.login(teacher_name, 'A1bench' + suffix)['accessToken']
student_name = sql(f"SELECT s.username FROM edu_class_student cs JOIN system_users s ON s.id=cs.student_id "
                   f"WHERE cs.class_id={class_id} AND s.username LIKE 'P{suffix}%' LIMIT 1")
student = c.login(student_name, 'A1tpl' + suffix)['accessToken']
homework_id = int(sql(f"SELECT id FROM edu_homework WHERE course_id={course_id} AND class_id={class_id} AND semester_name NOT LIKE '往期%' ORDER BY id LIMIT 1"))
record_id = int(sql(f"SELECT id FROM edu_homework_record WHERE homework_id={homework_id} ORDER BY id LIMIT 1"))
page = {'pageNo': 1, 'pageSize': 10}
class_course_id = int(sql(f"SELECT id FROM edu_class_course WHERE course_id={course_id} AND class_id={class_id} AND deleted=0 LIMIT 1"))

CASES = [
    ('admin', '/system/auth/get-permission-info', {}),
    ('admin', '/system/user/page', page),
    ('admin', '/edu/user/page', {**page, 'userType': 2}),
    ('admin', '/edu/user/page', {**page, 'userType': 2, 'deptId': seed['college']}),
    ('admin', '/edu/user/page', {**page, 'userType': 2, 'nickname': '压测学生59'}),
    ('admin', '/edu/user/page', {**page, 'userType': 1}),
    ('admin', '/system/dept/list', {}),
    ('admin', '/edu/class/page', page),
    ('admin', '/edu/class/page', {**page, 'deptId': seed['college']}),
    ('admin', '/edu/class/simple-list', {}),
    ('admin', '/edu/class/student/list-assigned', {'classId': big_class}),
    ('admin', '/edu/class/student/page-available', {**page, 'classId': big_class}),
    ('admin', '/capacity/course/page', page),
    ('admin', '/edu/course-manage/detail', {'courseId': course_id}),
    ('admin', '/edu/class-course/list-by-course', {'courseId': course_id}),
    ('admin', '/edu/class-course/list-by-class', {'classId': class_id}),
    ('admin', '/edu/homework/page', page),
    ('admin', '/edu/statistics/admin/dashboard', {}),
    ('admin', '/edu/statistics/total-pass-rate', {}),
    ('admin', '/edu/statistics/work-trend', {'courseId': course_id}),
    ('admin', '/edu/statistics/course-analysis', {'courseId': course_id}),
    ('admin', '/edu/semester/wrap-up/preview', {'semesterName': seed['semester']}),
    ('admin', '/system/notify-message/get-unread-count', {}),
    ('teacher', '/system/auth/get-permission-info', {}),
    ('teacher', '/edu/statistics/teacher/dashboard', {}),
    ('teacher', '/edu/statistics/course-options', {}),
    ('teacher', '/edu/statistics/class-options', {'courseId': course_id}),
    ('teacher', '/edu/statistics/course-analysis', {'courseId': course_id, 'classId': class_id}),
    ('teacher', '/capacity/course/page', page),
    ('teacher', '/edu/course-manage/detail', {'courseId': course_id}),
    ('teacher', '/edu/course-manage/class-detail', {'classCourseId': class_course_id}),
    ('teacher', '/edu/homework/page', page),
    ('teacher', '/edu/homework/get', {'id': homework_id}),
    ('teacher', '/edu/homework/statistics', {'homeworkId': homework_id}),
    ('teacher', '/edu/homework-record/page', {**page, 'homeworkId': homework_id}),
    ('teacher', '/edu/homework-record/correct-page', {**page, 'homeworkId': homework_id}),
    ('teacher', '/edu/homework-record/grading-queue', {'homeworkId': homework_id}),
    ('teacher', '/edu/homework-record/get', {'id': record_id}),
    ('teacher', '/edu/homework-record/correct-page', page),
    ('teacher', '/edu/homework-record/page', page),
    ('admin', '/edu/homework-record/page', {**page, 'homeworkId': homework_id}),
    ('admin', '/edu/homework-record/correct-page', {**page, 'homeworkId': homework_id}),
    ('teacher', '/edu/course-manage/class-detail', {'classCourseId': seed['archived_binding']}),
    ('teacher', '/edu/homework/statistics', {'homeworkId': seed['archived_homework']}),
    ('admin', '/system/user/simple-list', {'userType': 2}),
    ('admin', '/system/user/simple-list', {'userType': 1}),
    ('admin', '/edu/class/student/page-available', {**page, 'classId': big_class, 'keyword': '0059'}),
    ('teacher', '/edu/message/student-list', {**page, 'classId': class_id, 'courseId': course_id}),
    ('teacher', '/system/notify-message/my-page', page),
    ('teacher', '/system/notify-message/get-unread-count', {}),
    ('student', '/system/auth/get-permission-info', {}),
    ('student', '/edu/student/home/overview', {}),
    ('student', '/edu/student/course/list', {}),
    ('student', '/edu/student/course/get', {'courseId': course_id}),
    ('student', '/edu/student/course/unlock-status', {'courseId': course_id}),
    ('student', '/edu/student/homework/list', {**page, 'pageSize': 100}),
    ('student', '/edu/student/homework/list', {**page, 'courseId': course_id}),
    ('student', '/edu/student/homework/get', {'homeworkId': homework_id}),
    ('student', '/edu/student/capacity/analysis', {}),
    ('student', '/edu/student/capacity/report/basic', {}),
    ('student', '/edu/student/capacity/report/skills', {}),
    ('student', '/edu/student/capacity/report/trend', {}),
    ('student', '/edu/student/message/list', page),
    ('student', '/system/notify-message/my-page', page),
    ('student', '/system/notify-message/get-unread-count', {}),
]
tokens = {'admin': admin, 'teacher': teacher, 'student': student}
sql("UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long'")
# 先把全部接口完整跑两轮：后端刚重启时 JIT 未预热，单接口预热一次不够，会把冷启动耗时算进结果
for _ in range(2):
    for role, path, params in CASES:
        c.call('GET', path, tokens[role], params)
results = []
for n, (role, path, params) in enumerate(CASES):
    times = []
    sql("TRUNCATE performance_schema.events_statements_history_long")
    for _ in range(3):
        t0 = time.monotonic()
        status, payload = c.call('GET', path, tokens[role], params)
        times.append((time.monotonic() - t0) * 1000)
    count, sql_ms, examined = sql_stats()
    code = payload.get('code') if isinstance(payload, dict) else status
    label = path + ('?' + '&'.join(f'{k}={v}' for k, v in params.items() if k not in ('pageNo', 'pageSize')) if any(
        k not in ('pageNo', 'pageSize') for k in params) else '')
    name = f"{n:02d}-{role}-{re.sub(r'[^A-Za-z0-9]+', '_', label).strip('_')}"
    (OUT / 'snapshots' / f'{name}.json').write_text(json.dumps(
        {'role': role, 'path': path, 'params': params, 'payload': payload}, ensure_ascii=False, indent=1, sort_keys=True))
    results.append({'name': name, 'role': role, 'label': label, 'median_ms': round(statistics.median(times), 1),
                    'sql_count': int(count) // 3, 'sql_ms': round(float(sql_ms) / 3, 1),
                    'rows_examined': int(examined) // 3, 'code': code})
(OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=1))
for r in sorted(results, key=lambda r: -r['median_ms']):
    print(f"{r['median_ms']:7.0f} ms  SQL {r['sql_count']:4d} 条 {r['sql_ms']:7.1f} ms 扫描 {r['rows_examined']:8d} 行  "
          f"code={r['code']}  [{r['role']}] {r['label']}")
