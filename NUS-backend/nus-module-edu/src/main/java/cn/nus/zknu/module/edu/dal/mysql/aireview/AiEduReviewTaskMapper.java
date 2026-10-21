package cn.nus.zknu.module.edu.dal.mysql.aireview;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiEduReviewTaskMapper extends BaseMapperX<AiEduReviewTaskDO> {
    default int claim(Long id, String token) {
        AiEduReviewTaskDO update = new AiEduReviewTaskDO();
        update.setGenerationToken(token);
        return update(update, new LambdaUpdateWrapper<AiEduReviewTaskDO>()
                .eq(AiEduReviewTaskDO::getId, id)
                .eq(AiEduReviewTaskDO::getStatus, 0)
                .isNull(AiEduReviewTaskDO::getGenerationToken));
    }

    default int updateIfClaimed(AiEduReviewTaskDO update, Long id, String token) {
        return update(update, new LambdaUpdateWrapper<AiEduReviewTaskDO>()
                .eq(AiEduReviewTaskDO::getId, id)
                .eq(AiEduReviewTaskDO::getStatus, 0)
                .eq(AiEduReviewTaskDO::getGenerationToken, token));
    }

    default int failIfUnclaimed(Long id, Integer failCount, String reason) {
        AiEduReviewTaskDO update = new AiEduReviewTaskDO();
        update.setStatus(2);
        update.setFailCount(failCount);
        update.setErrorMessage(reason);
        return update(update, new LambdaUpdateWrapper<AiEduReviewTaskDO>()
                .eq(AiEduReviewTaskDO::getId, id)
                .eq(AiEduReviewTaskDO::getStatus, 0)
                .isNull(AiEduReviewTaskDO::getGenerationToken));
    }

    default int failIfClaimedWithCounts(AiEduReviewTaskDO update, AiEduReviewTaskDO original) {
        return update(update, new LambdaUpdateWrapper<AiEduReviewTaskDO>()
                .eq(AiEduReviewTaskDO::getId, original.getId())
                .eq(AiEduReviewTaskDO::getStatus, 0)
                .eq(AiEduReviewTaskDO::getGenerationToken, original.getGenerationToken())
                .eq(AiEduReviewTaskDO::getSuccessCount, original.getSuccessCount())
                .eq(AiEduReviewTaskDO::getFailCount, original.getFailCount()));
    }
}
