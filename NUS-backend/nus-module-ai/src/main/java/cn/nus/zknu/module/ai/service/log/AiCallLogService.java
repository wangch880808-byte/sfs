package cn.nus.zknu.module.ai.service.log;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogDailySummaryRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogSummaryReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;

import java.util.List;

/**
 * AI 调用日志。
 */
public interface AiCallLogService {

    /** 异步写入一条调用日志；写入失败只记警告，不影响模型调用。 */
    void record(AiCallLogDO log);

    PageResult<AiCallLogRespVO> getPage(AiCallLogPageReqVO reqVO);

    List<AiCallLogDailySummaryRespVO> getDailySummary(AiCallLogSummaryReqVO reqVO);

    /** 物理删除超过保留天数的日志，返回删除条数。 */
    int cleanExpired(int retentionDays);

}
