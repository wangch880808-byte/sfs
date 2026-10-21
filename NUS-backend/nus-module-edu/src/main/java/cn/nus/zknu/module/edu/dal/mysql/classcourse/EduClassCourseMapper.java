package cn.nus.zknu.module.edu.dal.mysql.classcourse;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduSemesterUsageDO;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EduClassCourseMapper extends BaseMapperX<EduClassCourseDO> {

    /** 公共课同步创建关联时写入的创建人与更新人。 */
    String SYNC_OPERATOR = "system";
    /** 公共课同步归档关联时写入的更新人；人工归档或恢复会被登录用户覆盖。 */
    String SYNC_ARCHIVE_MARKER = "system-sync";

    /** 同步创建、且从未被人工改动过的在用关联；关联下没有进行中的任务和待批改提交。 */
    String IDLE_AUTO_LINK_CONDITION = " cc.deleted = 0 AND cc.status = 0 AND cc.backfill_pending = 0 AND cc.claim_teacher_id IS NULL"
            + " AND cc.creator = '" + SYNC_OPERATOR + "' AND cc.updater = '" + SYNC_OPERATOR + "'"
            + " AND NOT EXISTS (SELECT 1 FROM edu_homework h WHERE h.deleted = 0"
            + "   AND h.class_id = cc.class_id AND h.course_id = cc.course_id"
            + "   AND (TRIM(COALESCE(h.semester_name, '')) = '' OR TRIM(h.semester_name) = TRIM(cc.semester_name))"
            + "   AND h.status = " + EduHomeworkStatusEnum.PUBLISHED
            + "   AND (h.deadline IS NULL OR h.deadline &gt; NOW()))"
            + " AND NOT EXISTS (SELECT 1 FROM edu_homework h"
            + "   JOIN edu_homework_record r ON r.homework_id = h.id AND r.deleted = 0"
            + "     AND r.status IN (" + EduHomeworkRecordStatusEnum.SUBMITTED + ", "
            + EduHomeworkRecordStatusEnum.RESUBMITTED + ")"
            + "   WHERE h.deleted = 0 AND h.class_id = cc.class_id AND h.course_id = cc.course_id"
            + "   AND (TRIM(COALESCE(h.semester_name, '')) = '' OR TRIM(h.semester_name) = TRIM(cc.semester_name))"
            + "   AND h.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", " + EduHomeworkStatusEnum.CLOSED + "))";

    String ARCHIVE_IDLE_AUTO_LINK_PREFIX = "<script>UPDATE edu_class_course cc SET cc.status = 1,"
            + " cc.updater = '" + SYNC_ARCHIVE_MARKER + "', cc.update_time = NOW() WHERE"
            + IDLE_AUTO_LINK_CONDITION;

    /** 学期切换后：归档非当前学期中空闲的同步关联。 */
    @Update(ARCHIVE_IDLE_AUTO_LINK_PREFIX
            + " AND TRIM(cc.semester_name) &lt;&gt; #{currentSemester}</script>")
    int archiveIdleAutoLinksOutsideSemester(@Param("currentSemester") String currentSemester);

    /** 公共课下线后：归档该课程空闲的同步关联；courseId 为空时处理所有已不在线的课程。 */
    @Update(ARCHIVE_IDLE_AUTO_LINK_PREFIX
            + " <if test='courseId != null'> AND cc.course_id = #{courseId}</if>"
            + " AND cc.course_id NOT IN (SELECT co.id FROM capacity_course co WHERE co.deleted = 0"
            + "   AND co.status = 0 AND co.is_public_course = 0 AND co.public_audit_status = 2)</script>")
    int archiveIdleAutoLinksOfOfflineCourses(@Param("courseId") Long courseId);

    /** 公共课重新上线后：恢复当前学期中被同步归档（而非人工归档）的关联。 */
    @Update("<script>UPDATE edu_class_course cc SET cc.status = 0, cc.updater = '" + SYNC_OPERATOR + "',"
            + " cc.update_time = NOW()"
            + " WHERE cc.deleted = 0 AND cc.status = 1 AND cc.creator = '" + SYNC_OPERATOR + "'"
            + " AND cc.claim_teacher_id IS NULL AND cc.updater = '" + SYNC_ARCHIVE_MARKER + "' AND TRIM(cc.semester_name) = #{semesterName}"
            + " <if test='courseId != null'> AND cc.course_id = #{courseId}</if>"
            + " AND cc.course_id IN (SELECT co.id FROM capacity_course co WHERE co.deleted = 0"
            + "   AND co.status = 0 AND co.is_public_course = 0 AND co.public_audit_status = 2)</script>")
    int restoreSyncArchivedLinks(@Param("courseId") Long courseId, @Param("semesterName") String semesterName);

    /** 同步与补发共用课程行锁；跨学期也串行，避免锁释放前读取旧快照。 */
    @Select("SELECT id FROM capacity_course WHERE deleted = 0 AND id = #{courseId} FOR UPDATE")
    Long lockPublicCourse(@Param("courseId") Long courseId);

    /** 当前读：在等待课程锁后仍能看到刚提交的待补发批次。 */
    @Select("<script>SELECT * FROM edu_class_course WHERE deleted = 0 AND backfill_pending = 1"
            + " <if test='courseId != null'>AND course_id = #{courseId}</if>"
            + " <if test='semesterName != null'>AND TRIM(semester_name) = #{semesterName}</if>"
            + " ORDER BY course_id, id FOR UPDATE</script>")
    List<EduClassCourseDO> selectPendingBackfillLinks(@Param("courseId") Long courseId,
                                                    @Param("semesterName") String semesterName);

    /** 每日同步兜底扫描，包括之前学期失败后留下的标记；具体处理时再加课程锁。 */
    @Select("SELECT * FROM edu_class_course WHERE deleted = 0 AND backfill_pending = 1 ORDER BY course_id, id")
    List<EduClassCourseDO> selectBackfillBacklog();

    /** 与任务、指派一起提交；失败回滚时标记仍保留。 */
    @Update("UPDATE edu_class_course SET backfill_pending = 0, update_time = NOW()"
            + " WHERE deleted = 0 AND course_id = #{courseId} AND TRIM(semester_name) = #{semesterName}"
            + " AND backfill_pending = 1")
    int completeBackfill(@Param("courseId") Long courseId, @Param("semesterName") String semesterName);

    /** 原生条件更新绕过自动填充，防止认领使同步关联被误判为人工修改。 */
    @Update("UPDATE edu_class_course SET claim_teacher_id = #{teacherId}, claim_time = NOW()"
            + " WHERE deleted = 0 AND id = #{id} AND claim_teacher_id IS NULL AND status = 0")
    int claim(@Param("id") Long id, @Param("teacherId") Long teacherId);

    /** 只有恢复同步归档行时才还原同步标记，其他更新人保持原值。 */
    @Update("UPDATE edu_class_course SET claim_teacher_id = #{teacherId}, claim_time = NOW(), status = 0,"
            + " updater = CASE WHEN updater = 'system-sync' THEN 'system' ELSE updater END"
            + " WHERE deleted = 0 AND id = #{id} AND claim_teacher_id IS NULL AND status = 1")
    int claimArchived(@Param("id") Long id, @Param("teacherId") Long teacherId);

    /** 携带原认领人作条件，避免管理员的旧请求清除后来教师的新认领。 */
    @Update("UPDATE edu_class_course SET claim_teacher_id = NULL, claim_time = NULL"
            + " WHERE deleted = 0 AND id = #{id} AND claim_teacher_id = #{teacherId}")
    int release(@Param("id") Long id, @Param("teacherId") Long teacherId);

    @Select("SELECT * FROM edu_class_course WHERE deleted = 0 AND id = #{id} FOR UPDATE")
    EduClassCourseDO selectClaimById(@Param("id") Long id);

    /** 课程锁之后使用当前读，避免 MySQL 可重复读仍返回等待锁之前的快照。 */
    @Select("SELECT * FROM edu_class_course WHERE deleted = 0 AND course_id = #{courseId}"
            + " AND class_id = #{classId} AND TRIM(semester_name) = #{semesterName}"
            + " ORDER BY status, id DESC LIMIT 1 FOR UPDATE")
    EduClassCourseDO selectClaimRelation(@Param("courseId") Long courseId, @Param("classId") Long classId,
                                         @Param("semesterName") String semesterName);

    /** 班课关联里出现过的全部学期名称，带日期范围与关联数。 */
    @Select("SELECT TRIM(semester_name) AS semesterName, MIN(semester_start_date) AS startDate,"
            + " MAX(semester_end_date) AS endDate, COUNT(*) AS linkCount,"
            + " SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS activeLinkCount"
            + " FROM edu_class_course WHERE deleted = 0 AND TRIM(COALESCE(semester_name, '')) <> ''"
            + " GROUP BY TRIM(semester_name)")
    List<EduSemesterUsageDO> selectSemesterUsage();

}
