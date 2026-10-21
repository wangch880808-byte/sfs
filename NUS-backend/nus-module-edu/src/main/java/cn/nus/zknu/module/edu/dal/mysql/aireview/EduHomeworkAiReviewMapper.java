package cn.nus.zknu.module.edu.dal.mysql.aireview;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

@Mapper
public interface EduHomeworkAiReviewMapper extends BaseMapperX<EduHomeworkAiReviewDO> {

    /** 调用方已按任务集合校验查看权限，按主键取最新草案而非最近有分数的草案。 */
    List<EduHomeworkAiReviewDO> selectLatestByRecordIds(@Param("recordIds") Collection<Long> recordIds);

    default EduHomeworkAiReviewDO selectLatestByRecordId(Long homeworkRecordId) {
        return selectOne(new LambdaQueryWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getHomeworkRecordId, homeworkRecordId)
                .orderByDesc(EduHomeworkAiReviewDO::getId)
                .last("LIMIT 1"));
    }

    default EduHomeworkAiReviewDO selectLatestScoredByRecordId(Long homeworkRecordId) {
        return selectOne(new LambdaQueryWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getHomeworkRecordId, homeworkRecordId)
                .isNotNull(EduHomeworkAiReviewDO::getAiScore)
                .orderByDesc(EduHomeworkAiReviewDO::getId)
                .last("LIMIT 1"));
    }

    default int updateIfProcessing(EduHomeworkAiReviewDO update, Long id, String generationToken) {
        return update(update, new LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getId, id)
                .eq(EduHomeworkAiReviewDO::getGenerationToken, generationToken)
                .eq(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.PROCESSING));
    }

    default int failIfQueued(EduHomeworkAiReviewDO update, Long id, String generationToken) {
        return update(update, new LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getId, id)
                .eq(EduHomeworkAiReviewDO::getGenerationToken, generationToken)
                .eq(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.QUEUED));
    }

    /** 认领排队中的初评：仅当仍为排队中且令牌未变时，由排队中改为处理中。 */
    default int claimQueued(Long id, String generationToken) {
        EduHomeworkAiReviewDO update = new EduHomeworkAiReviewDO();
        update.setStatus(HomeworkAiReviewStatusEnum.PROCESSING);
        return update(update, new LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getId, id)
                .eq(EduHomeworkAiReviewDO::getGenerationToken, generationToken)
                .eq(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.QUEUED));
    }

    /**
     * 把遗留的「处理中」初评放回排队：仅当仍为处理中且令牌未变时更新，并换新令牌，
     * 让原执行线程（如果还在）迟到的结果因令牌不符被忽略。
     */
    default int requeueProcessing(Long id, String generationToken, String newGenerationToken) {
        EduHomeworkAiReviewDO update = new EduHomeworkAiReviewDO();
        update.setStatus(HomeworkAiReviewStatusEnum.QUEUED);
        update.setGenerationToken(newGenerationToken);
        update.setUpdateTime(LocalDateTime.now());
        return update(update, new LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getId, id)
                .eq(EduHomeworkAiReviewDO::getGenerationToken, generationToken)
                .eq(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.PROCESSING));
    }

    /** 按入队先后取排队中的初评。 */
    default List<EduHomeworkAiReviewDO> selectQueued(int limit) {
        return selectList(new LambdaQueryWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.QUEUED)
                .orderByAsc(EduHomeworkAiReviewDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /** 某状态下最后更新早于 before 的初评，走 (status, update_time) 索引。 */
    default List<EduHomeworkAiReviewDO> selectStale(int status, LocalDateTime before, int limit) {
        return selectList(new LambdaQueryWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getStatus, status)
                .lt(EduHomeworkAiReviewDO::getUpdateTime, before)
                .orderByAsc(EduHomeworkAiReviewDO::getUpdateTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /** 任务里最新一次 AI 初评为失败、且仍在等待批改的提交份数。 */
    @Select("""
            SELECT COUNT(*) FROM edu_homework_ai_review ar
            JOIN edu_homework_record r ON r.id = ar.homework_record_id AND r.deleted = 0
            WHERE ar.deleted = 0 AND ar.homework_id = #{homeworkId} AND ar.status = 3
              AND r.status IN (1, 4)
              AND NOT EXISTS (SELECT 1 FROM edu_homework_ai_review x
                              WHERE x.homework_record_id = ar.homework_record_id
                                AND x.deleted = 0 AND x.id > ar.id)
            """)
    long countLatestFailedByHomeworkId(@Param("homeworkId") Long homeworkId);
}
