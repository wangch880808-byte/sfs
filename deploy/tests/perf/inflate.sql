-- 放大 10 倍：模拟多个学期积累的任务、分配、提交和站内信（前 9 份标为往期学期，第 10 份即当前数据）
DROP TABLE IF EXISTS tmp_k;
CREATE TABLE tmp_k (k INT PRIMARY KEY);
INSERT INTO tmp_k VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9);
CREATE TABLE tmp_hw AS SELECT * FROM edu_homework WHERE deleted=0;
INSERT INTO edu_homework (homework_name,homework_type,homework_level,course_id,class_id,semester_name,indicator_first_id,
  indicator_second_id,requirement,deadline,auto_grade,submission_mode,ai_review_mode,required_attachment_types_json,ai_rubric_json,
  ai_reference_json,ai_confidence_threshold,pass_score,score,teacher_id,status,status_before_archive,source_homework_id,creator,
  create_time,updater,update_time,deleted)
SELECT CONCAT(h.homework_name,'#',k.k),homework_type,homework_level,course_id,class_id,CONCAT('往期',k.k),indicator_first_id,
  indicator_second_id,requirement,deadline - INTERVAL k.k*120 DAY,auto_grade,submission_mode,ai_review_mode,required_attachment_types_json,
  ai_rubric_json,ai_reference_json,ai_confidence_threshold,pass_score,score,teacher_id,status,status_before_archive,source_homework_id,
  creator,create_time - INTERVAL k.k*120 DAY,updater,update_time,0
FROM tmp_hw h CROSS JOIN tmp_k k;
CREATE TABLE tmp_map AS SELECT h.id old_id, n.id new_id FROM tmp_hw h JOIN tmp_k k
  JOIN edu_homework n ON n.homework_name = CONCAT(h.homework_name,'#',k.k) COLLATE utf8mb4_unicode_ci;
ALTER TABLE tmp_map ADD INDEX (old_id);
INSERT INTO edu_homework_assignee (homework_id,student_id,class_id,assigned_time,creator,create_time,updater,update_time,deleted)
SELECT m.new_id,a.student_id,a.class_id,a.assigned_time,a.creator,a.create_time,a.updater,a.update_time,0
FROM edu_homework_assignee a JOIN tmp_map m ON m.old_id=a.homework_id WHERE a.deleted=0;
INSERT INTO edu_homework_record (homework_id,student_id,content,attachment_urls,submit_time,score,comment,correct_type,correct_time,
  status,redo_count,redo_deadline,creator,create_time,updater,update_time,deleted)
SELECT m.new_id,r.student_id,r.content,r.attachment_urls,r.submit_time - INTERVAL 30 DAY,IFNULL(r.score,70),r.comment,1,
  IFNULL(r.correct_time,NOW()),2,r.redo_count,NULL,r.creator,r.create_time,r.updater,r.update_time,0
FROM edu_homework_record r JOIN tmp_map m ON m.old_id=r.homework_id WHERE r.deleted=0;
INSERT INTO system_notify_message (user_id,user_type,template_id,template_code,template_nickname,template_content,template_type,
  template_params,read_status,read_time,creator,create_time,updater,update_time,deleted)
SELECT user_id,user_type,template_id,template_code,template_nickname,template_content,template_type,template_params,1,NOW(),
  creator,create_time - INTERVAL k.k*120 DAY,updater,update_time,0
FROM system_notify_message n CROSS JOIN tmp_k k WHERE n.deleted=0;
DROP TABLE tmp_k; DROP TABLE tmp_hw; DROP TABLE tmp_map;
ANALYZE TABLE edu_homework, edu_homework_assignee, edu_homework_record, system_notify_message;
SELECT (SELECT COUNT(*) FROM edu_homework), (SELECT COUNT(*) FROM edu_homework_assignee), (SELECT COUNT(*) FROM edu_homework_record), (SELECT COUNT(*) FROM system_notify_message);
