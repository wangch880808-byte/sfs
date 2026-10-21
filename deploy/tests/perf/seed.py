"""性能基准造数（只连本机一次性环境，见 README）。

默认：1 学院 4 专业、6000 学生（每班约 50 人，即 120 班）、20 教师/课程、每课 6 班、每班课 2 任务、约 70% 提交，
并造边界数据（移出班级的学生、归档任务、归档班课、打回重交、空分数并列）。
结构走接口（由业务逻辑生成关联数据），学生与提交记录用 SQL 按模板批量复制。
用法：python3 seed.py [--students N] [--out seed.json]
"""
import argparse, json, os, subprocess, sys, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'e2e'))
from e2e_v1017 import Client17
from e2e_v1018 import Ctx18

parser = argparse.ArgumentParser()
parser.add_argument('--students', type=int, default=6000)
parser.add_argument('--out', default=str(Path(__file__).with_name('seed.json')))
args = parser.parse_args()
STATE = Path(os.environ.get('NUS_E2E_STATE', Path(os.environ['HOME'], '.cache/nus-e2e')))
BASE = os.environ.get('NUS_E2E_BASE_URL', 'http://127.0.0.1:48280')
if not BASE.startswith(('http://127.0.0.1:', 'http://localhost:')):
    raise SystemExit('只允许连接本机一次性环境')
# 每班约 50 人、每门课 6 个班，班级数和课程数随学生数等比放大（默认 6000 人即 120 班、20 门课）
CLASSES = max(20, args.students // 50)
STUDENTS, COURSES, CLASSES_PER_COURSE, TASKS_PER_BINDING = args.students, max(4, CLASSES // 6), 6, 2


def sql(text):
    out = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + (STATE / 'db.pw').read_text().strip(),
                          os.environ.get('NUS_E2E_MYSQL_NAME', 'nus-e2e-mysql'), 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', 'nus'], input=text.encode(),
                         capture_output=True)
    if out.returncode:
        raise SystemExit(out.stderr.decode())
    return out.stdout.decode().strip()


c = Client17(BASE)
c.admin = c.login('admin', (STATE / 'admin.pw').read_text().strip())['accessToken']
x = Ctx18(c)
major0, _ = x.foundation()
college = x.dept
majors = [major0] + [c.ok('POST', '/system/dept/create', body={
    'name': x.name('专业'), 'deptCode': x.name('MJ'), 'parentId': college, 'sort': 999, 'status': 0,
    'deptType': 'MAJOR'}) for _ in range(3)]
t0 = time.monotonic()
classes = []
for i in range(CLASSES):
    dept = majors[i % len(majors)]
    classes.append((c.ok('POST', '/edu/class/create', body={
        'className': x.name('压测班'), 'gradeYear': 2025, 'deptId': dept, 'educationLevel': 2,
        'schoolingYear': 4, 'status': 0}), dept))
print(f'班级 {len(classes)} 个，{time.monotonic() - t0:.0f} 秒', flush=True)

template = c.ok('POST', '/system/user/create-student', body={
    'username': 'TPL' + x.suffix, 'nickname': '模板学生', 'password': 'A1tpl' + x.suffix, 'deptId': classes[0][1],
    'profile': {'gradeYear': 2025, 'classIds': [classes[0][0]]}})
values = ','.join(f"({i},'P{x.suffix}{i:05d}',{classes[i % CLASSES][0]},{classes[i % CLASSES][1]})"
                  for i in range(STUDENTS))
sql(f"""
DROP TABLE IF EXISTS tmp_gen;
CREATE TABLE tmp_gen (seq INT PRIMARY KEY, username VARCHAR(64), class_id BIGINT, dept_id BIGINT) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO tmp_gen VALUES {values};
INSERT INTO system_users (username,password,user_type,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,
  login_ip,login_date,creator,create_time,updater,update_time,deleted,password_change_required)
SELECT g.username,u.password,u.user_type,CONCAT('压测学生',g.seq),NULL,g.dept_id,u.post_ids,NULL,NULL,g.seq % 2,NULL,0,
  '',NULL,u.creator,NOW(),u.updater,NOW(),0,0 FROM tmp_gen g JOIN system_users u ON u.id={template};
INSERT INTO system_user_profile (user_id,user_type,professional_title,grade_year,class_ids,creator,create_time,updater,update_time,deleted)
SELECT s.id,p.user_type,p.professional_title,p.grade_year,NULL,p.creator,NOW(),p.updater,NOW(),0
  FROM tmp_gen g JOIN system_users s ON s.username=g.username JOIN system_user_profile p ON p.user_id={template};
INSERT INTO system_user_role (user_id,role_id,creator,create_time,updater,update_time,deleted)
SELECT s.id,r.role_id,r.creator,NOW(),r.updater,NOW(),0
  FROM tmp_gen g JOIN system_users s ON s.username=g.username JOIN system_user_role r ON r.user_id={template} AND r.deleted=0;
INSERT INTO edu_class_student (class_id,student_id,status,remark,creator,join_time,create_time,updater,update_time,deleted)
SELECT g.class_id,s.id,cs.status,NULL,cs.creator,NOW(),NOW(),cs.updater,NOW(),0
  FROM tmp_gen g JOIN system_users s ON s.username=g.username JOIN edu_class_student cs ON cs.student_id={template} AND cs.deleted=0;
DROP TABLE tmp_gen;
""")
print('学生', sql(f"SELECT COUNT(*) FROM system_users WHERE username LIKE 'P{x.suffix}%'"), flush=True)

t0 = time.monotonic()
bindings = []
teacher_tokens = []
for k in range(COURSES):
    teacher = x.user('teacher')
    token = x.login(teacher)
    teacher_tokens.append(token)
    course = x.course(teacher['id'])
    for j in range(CLASSES_PER_COURSE):
        class_id = classes[(k * CLASSES_PER_COURSE + j) % CLASSES][0]
        x.bind_with(class_id, course)
        for _ in range(TASKS_PER_BINDING):
            x.task(class_id, course, token)
        bindings.append((class_id, course))
    print(f'课程 {k + 1}/{COURSES}，{time.monotonic() - t0:.0f} 秒', flush=True)

# 提交记录：约 70% 已提交；其中一半已批改、少量打回重交
sql("""
INSERT INTO edu_homework_record (homework_id,student_id,content,attachment_urls,submit_time,score,comment,correct_type,
  correct_time,status,redo_count,redo_deadline,creator,create_time,updater,update_time,deleted)
SELECT a.homework_id,a.student_id,'压测提交内容',NULL,NOW() - INTERVAL (a.id % 500) MINUTE,
  CASE WHEN a.id % 10 < 4 THEN 50 + a.id % 50 END,NULL,CASE WHEN a.id % 10 < 4 THEN 1 END,
  CASE WHEN a.id % 10 < 4 THEN NOW() END,
  CASE WHEN a.id % 10 < 4 THEN 2 WHEN a.id % 10 = 4 THEN 4 ELSE 1 END,
  CASE WHEN a.id % 10 = 4 THEN 1 ELSE 0 END,NULL,'1',NOW(),'1',NOW(),0
FROM edu_homework_assignee a WHERE a.deleted=0 AND a.id % 10 < 7
  AND NOT EXISTS (SELECT 1 FROM edu_homework_record r WHERE r.homework_id=a.homework_id AND r.student_id=a.student_id);
""")
print(sql("""SELECT CONCAT('任务 ',(SELECT COUNT(*) FROM edu_homework WHERE deleted=0),'，分配 ',
  (SELECT COUNT(*) FROM edu_homework_assignee WHERE deleted=0),'，提交 ',(SELECT COUNT(*) FROM edu_homework_record),
  '，站内信 ',(SELECT COUNT(*) FROM system_notify_message))"""))

# 边界数据：都放在第 1 门课上，基准接口正好覆盖
first_class, first_course = bindings[0]
second_class = bindings[1][0]
owner_hw = sql(f"SELECT id FROM edu_homework WHERE course_id={first_course} AND class_id={first_class} ORDER BY id")
owner_hw = [int(v) for v in owner_hw.split()]
removed = [int(v) for v in sql(f"SELECT student_id FROM edu_class_student WHERE class_id={first_class} AND deleted=0 "
                                   f"ORDER BY student_id LIMIT 3").split()]
c.ok('POST', '/edu/class/student/remove', body={'classId': first_class, 'studentIds': removed})
archived_hw = int(sql(f"SELECT id FROM edu_homework WHERE course_id={first_course} AND class_id={second_class} ORDER BY id LIMIT 1"))
c.ok('DELETE', '/edu/homework/delete', teacher_tokens[0], params={'id': archived_hw})
third_class = bindings[2][0]
archived_binding = int(sql(f"SELECT id FROM edu_class_course WHERE course_id={first_course} AND class_id={third_class} AND deleted=0"))
c.ok('DELETE', '/edu/class-course/unbind', params={'id': archived_binding})
# 打回重交、空分数并列、同分并列
sql(f"""
UPDATE edu_homework_record SET status=3, redo_count=1, redo_deadline=NOW() + INTERVAL 3 DAY
  WHERE homework_id={owner_hw[0]} AND status=2 ORDER BY id LIMIT 3;
UPDATE edu_homework_record SET score=NULL WHERE homework_id={owner_hw[0]} AND status=2 ORDER BY id LIMIT 2;
UPDATE edu_homework_record SET score=88 WHERE homework_id={owner_hw[0]} AND status=2 AND score IS NOT NULL ORDER BY id LIMIT 3;
""")
print(f'边界数据：移出 {len(removed)} 人，归档任务 {archived_hw}，归档班课 {archived_binding}', flush=True)
json.dump({'suffix': x.suffix, 'college': college, 'majors': majors, 'classes': classes, 'bindings': bindings,
           'semester': x.semester, 'removed_students': removed, 'archived_homework': archived_hw,
           'archived_binding': archived_binding},
          open(args.out, 'w'))
