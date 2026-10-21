package cn.nus.zknu.module.edu.dal.mysql.message;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import cn.nus.zknu.module.edu.controller.admin.message.vo.StudentConversationRespVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageStudentListRespVO;

import java.util.List;

@Mapper
public interface EduMessageMapper extends BaseMapperX<EduMessageDO> {

    /** 最新留言按编号判定，未读口径包含历史空 read_status，与会话读取保持一致。 */
    @Select("""
            SELECT m.student_id AS studentId, m.create_time AS lastMessageTime,
                   m.content AS lastMessageContent, summary.unread_count AS unreadCount
            FROM edu_message m
            JOIN (SELECT student_id, MAX(id) AS latest_id,
                         SUM(CASE WHEN type = 0 AND (read_status IS NULL OR read_status != 1)
                             THEN 1 ELSE 0 END) AS unread_count
                  FROM edu_message
                  WHERE class_id = #{classId} AND course_id = #{courseId} AND deleted = 0
                  GROUP BY student_id) summary ON summary.latest_id = m.id
            WHERE m.deleted = 0
            ORDER BY m.id DESC
            """)
    List<MessageStudentListRespVO> selectTeacherStudentSummaries(@Param("classId") Long classId,
                                                               @Param("courseId") Long courseId);

    /** 会话仍按班级与课程分组，先选代表学期，避免不同认领人将同一会话拆成多行。 */
    @Select("""
            SELECT cc.class_id AS classId, cc.course_id AS courseId,
                   cc.class_name AS className, c.course_name AS courseName,
                   teacher.nickname AS teacherName
            FROM (
                SELECT binding.class_id, binding.course_id, cl.class_name, binding.claim_teacher_id,
                       ROW_NUMBER() OVER (PARTITION BY binding.class_id, binding.course_id ORDER BY
                           CASE WHEN binding.claim_teacher_id IS NULL THEN 1 ELSE 0 END,
                           CASE WHEN TRIM(binding.semester_name) = TRIM((SELECT cfg.`value` FROM infra_config cfg
                               WHERE cfg.config_key = 'edu.current.semester.name' AND cfg.deleted = 0
                               ORDER BY cfg.id DESC LIMIT 1)) THEN 0 ELSE 1 END,
                           binding.id DESC) AS teacher_rank
                FROM edu_class_student cs
                JOIN edu_class cl ON cl.id = cs.class_id AND cl.deleted = 0 AND cl.status = 0
                JOIN edu_class_course binding ON binding.class_id = cs.class_id
                    AND binding.deleted = 0 AND binding.status = 0
                WHERE cs.student_id = #{studentId} AND cs.deleted = 0 AND cs.status = 0
            ) cc
            JOIN capacity_course c ON c.id = cc.course_id AND c.deleted = 0 AND c.status = 0
            LEFT JOIN system_users teacher ON teacher.id = CASE WHEN c.is_public_course = 0
                THEN COALESCE(cc.claim_teacher_id, c.teacher_Id) ELSE c.teacher_Id END AND teacher.deleted = 0
            WHERE cc.teacher_rank = 1
            ORDER BY c.course_name, cc.class_name
            """)
    List<StudentConversationRespVO> selectStudentConversations(@Param("studentId") Long studentId);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM edu_class_student cs
                JOIN edu_class cl ON cl.id = cs.class_id AND cl.deleted = 0 AND cl.status = 0
                JOIN edu_class_course cc ON cc.class_id = cs.class_id AND cc.deleted = 0 AND cc.status = 0
                JOIN capacity_course c ON c.id = cc.course_id AND c.deleted = 0 AND c.status = 0
                WHERE cs.student_id = #{studentId} AND cs.class_id = #{classId}
                  AND cc.course_id = #{courseId} AND cs.deleted = 0 AND cs.status = 0)
            """)
    boolean existsStudentConversation(@Param("studentId") Long studentId,
                                      @Param("classId") Long classId, @Param("courseId") Long courseId);
}
