package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkNotifyStateDO;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EduHomeworkNotifyStateMapper extends BaseMapperX<EduHomeworkNotifyStateDO> {

    default EduHomeworkNotifyStateDO selectByHomeworkId(Long homeworkId) {
        return selectOne(EduHomeworkNotifyStateDO::getHomeworkId, homeworkId);
    }

    /**
     * 抢占催交时间：上次催交早于 {@code allowedBefore}（含）或从未催交时才写入，返回 1 表示抢到。
     */
    @Update("UPDATE edu_homework_notify_state SET manual_remind_time = #{now}, update_time = NOW() "
            + "WHERE homework_id = #{homeworkId} AND deleted = 0 "
            + "AND (manual_remind_time IS NULL OR manual_remind_time <= #{allowedBefore})")
    int claimManualRemind(@Param("homeworkId") Long homeworkId, @Param("now") LocalDateTime now,
                          @Param("allowedBefore") LocalDateTime allowedBefore);

    /** 催交一条都没发出时恢复上次催交时间；期间已被别人重新抢占的不动。 */
    @Update("UPDATE edu_homework_notify_state SET manual_remind_time = #{previous}, update_time = NOW() "
            + "WHERE homework_id = #{homeworkId} AND deleted = 0 AND manual_remind_time = #{claimed}")
    int releaseManualRemind(@Param("homeworkId") Long homeworkId, @Param("claimed") LocalDateTime claimed,
                            @Param("previous") LocalDateTime previous);

    /** 全局扫描边界为两轮扫描之间发布的新任务提供连续的首次查询起点。 */
    @Insert("INSERT INTO edu_homework_digest_scan (id, scan_until) VALUES (1, #{since})"
            + " ON DUPLICATE KEY UPDATE scan_until = scan_until")
    int initializeGlobalDigestCursor(@Param("since") LocalDateTime since);

    @Select("SELECT scan_until FROM edu_homework_digest_scan WHERE id = 1")
    LocalDateTime selectGlobalDigestCursor();

    /** 全轮扫描完成才推进；并发或乱序运行不会把边界倒退。 */
    @Update("UPDATE edu_homework_digest_scan SET scan_until = #{cutoff} WHERE id = 1 AND scan_until < #{cutoff}")
    int advanceGlobalDigestCursor(@Param("cutoff") LocalDateTime cutoff);

    /** 首次扫描就持久化起点（含无提交任务）；已有催交状态行也补齐游标，后续不重置。 */
    @Insert("INSERT INTO edu_homework_notify_state (homework_id, digest_until)"
            + " SELECT h.id, #{since} FROM edu_homework h WHERE h.deleted = 0"
            + " AND h.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", " + EduHomeworkStatusEnum.CLOSED + ")"
            + " ON DUPLICATE KEY UPDATE digest_until = COALESCE(digest_until, VALUES(digest_until))")
    int initializeDigestCursors(@Param("since") LocalDateTime since);

    /** 无待汇总提交时推进扫描游标；有未通知提交时保留起点，限频期间也不会丢失。 */
    @Update("UPDATE edu_homework_notify_state s SET s.digest_until = #{cutoff}, s.update_time = NOW()"
            + " WHERE s.deleted = 0 AND s.digest_until < #{cutoff}"
            + " AND EXISTS (SELECT 1 FROM edu_homework h WHERE h.id = s.homework_id AND h.deleted = 0"
            + " AND h.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", " + EduHomeworkStatusEnum.CLOSED + "))"
            + " AND NOT EXISTS (SELECT 1 FROM edu_homework_record r WHERE r.homework_id = s.homework_id"
            + " AND r.deleted = 0 AND r.status IN (" + EduHomeworkRecordStatusEnum.SUBMITTED + ", "
            + EduHomeworkRecordStatusEnum.RESUBMITTED + ")"
            + " AND r.submit_time > s.digest_until AND r.submit_time <= #{cutoff})")
    int advanceEmptyDigestCursors(@Param("cutoff") LocalDateTime cutoff);

    /** 游标条件更新与通知限频一起抢占；同一批提交只能被一个扫描发送。 */
    @Update("UPDATE edu_homework_notify_state SET digest_until = #{until}, digest_notify_time = #{now},"
            + " update_time = NOW() WHERE homework_id = #{homeworkId} AND deleted = 0"
            + " AND digest_until <=> #{expected} AND digest_until < #{until}"
            + " AND (digest_notify_time IS NULL OR digest_notify_time <= #{gateAfter})")
    int advanceDigest(@Param("homeworkId") Long homeworkId, @Param("expected") LocalDateTime expected,
                      @Param("until") LocalDateTime until, @Param("now") LocalDateTime now,
                      @Param("gateAfter") LocalDateTime gateAfter);

    /** 查询窗口始终从持久化游标开始；通知间隔只限制发送频率，不改变窗口。 */
    @Select("SELECT r.homework_id AS homeworkId, s.digest_until AS digestUntil, "
            + "COUNT(*) AS submittedCount, "
            + "SUM(CASE WHEN r.status = " + EduHomeworkRecordStatusEnum.RESUBMITTED
            + " THEN 1 ELSE 0 END) AS resubmittedCount "
            + "FROM edu_homework_record r "
            + "JOIN edu_homework h ON h.id = r.homework_id AND h.deleted = 0 AND h.status IN ("
            + EduHomeworkStatusEnum.PUBLISHED + ", " + EduHomeworkStatusEnum.CLOSED + ") "
            + "JOIN edu_homework_notify_state s ON s.homework_id = r.homework_id AND s.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.status IN (" + EduHomeworkRecordStatusEnum.SUBMITTED + ", "
            + EduHomeworkRecordStatusEnum.RESUBMITTED + ") "
            + "AND r.submit_time <= #{cutoff} AND r.submit_time > s.digest_until "
            + "AND (s.digest_notify_time IS NULL OR s.digest_notify_time <= #{gateAfter}) "
            + "GROUP BY r.homework_id, s.digest_until")
    List<DigestRow> selectPendingDigests(@Param("cutoff") LocalDateTime cutoff,
                                       @Param("gateAfter") LocalDateTime gateAfter);

    /** 新提交汇总的统计结果 */
    @lombok.Data
    class DigestRow {
        private Long homeworkId;
        private LocalDateTime digestUntil;
        private Long submittedCount;
        private Long resubmittedCount;
    }

}
