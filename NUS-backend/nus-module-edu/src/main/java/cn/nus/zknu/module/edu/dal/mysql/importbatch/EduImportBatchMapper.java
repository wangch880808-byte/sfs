package cn.nus.zknu.module.edu.dal.mysql.importbatch;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

import static cn.nus.zknu.module.edu.enums.importbatch.EduImportBatchStatusEnum.*;

@Mapper
public interface EduImportBatchMapper extends BaseMapperX<EduImportBatchDO> {

    default EduImportBatchDO selectByBatchNo(String batchNo) {
        return selectOne(EduImportBatchDO::getBatchNo, batchNo);
    }

    default PageResult<EduImportBatchDO> selectPage(EduImportBatchPageReqVO reqVO, Long operatorId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EduImportBatchDO>()
                .eqIfPresent(EduImportBatchDO::getOperatorId, operatorId)
                .eqIfPresent(EduImportBatchDO::getBizType, reqVO.getBizType())
                .eqIfPresent(EduImportBatchDO::getStatus, reqVO.getStatus())
                .orderByDesc(EduImportBatchDO::getId));
    }

    default int claimForConfirmation(Long id, String expectedStatus, String confirmingStatus, LocalDateTime startedAt) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, confirmingStatus)
                .set(EduImportBatchDO::getConfirmTime, startedAt)
                .eq(EduImportBatchDO::getId, id)
                .eq(EduImportBatchDO::getStatus, expectedStatus));
    }

    /** 成功状态与业务数据同事务提交；若超时处理先改为 FAILED，则必须回滚业务事务。 */
    default int completeConfirmation(EduImportBatchDO batch) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, SUCCESS.getStatus())
                .set(EduImportBatchDO::getConfirmTime, batch.getConfirmTime())
                .set(EduImportBatchDO::getInsertedCount, batch.getInsertedCount())
                .set(EduImportBatchDO::getUpdatedCount, batch.getUpdatedCount())
                .set(EduImportBatchDO::getSkippedCount, batch.getSkippedCount())
                .eq(EduImportBatchDO::getId, batch.getId())
                .eq(EduImportBatchDO::getStatus, CONFIRMING.getStatus()));
    }

    default int resetConfirmation(Long id) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, PREVIEW_READY.getStatus())
                .set(EduImportBatchDO::getConfirmTime, null)
                .eq(EduImportBatchDO::getId, id)
                .eq(EduImportBatchDO::getStatus, CONFIRMING.getStatus()));
    }

    /** 与确认领取竞争时，只能取消仍处于预检状态的批次。 */
    default int cancelPreview(Long id) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, CANCELLED.getStatus())
                .eq(EduImportBatchDO::getId, id)
                .in(EduImportBatchDO::getStatus, PREVIEW_READY.getStatus(), PREVIEW_INVALID.getStatus()));
    }

    default int failConfirmation(Long id, String reason) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, FAILED.getStatus())
                .set(EduImportBatchDO::getFailureReason, reason)
                .eq(EduImportBatchDO::getId, id)
                .eq(EduImportBatchDO::getStatus, CONFIRMING.getStatus()));
    }

    /** id 为空时供启动扫描使用；状态和时间条件在行锁取得后仍需成立。 */
    default int failTimedOutConfirmations(Long id, LocalDateTime cutoff, String reason) {
        return update(new EduImportBatchDO(), new LambdaUpdateWrapper<EduImportBatchDO>()
                .set(EduImportBatchDO::getStatus, FAILED.getStatus())
                .set(EduImportBatchDO::getFailureReason, reason)
                .eq(id != null, EduImportBatchDO::getId, id)
                .eq(EduImportBatchDO::getStatus, CONFIRMING.getStatus())
                .lt(EduImportBatchDO::getConfirmTime, cutoff));
    }

}
