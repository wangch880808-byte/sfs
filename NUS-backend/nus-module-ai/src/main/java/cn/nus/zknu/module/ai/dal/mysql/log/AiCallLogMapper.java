package cn.nus.zknu.module.ai.dal.mysql.log;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogPageReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiCallLogMapper extends BaseMapperX<AiCallLogDO> {

    default PageResult<AiCallLogDO> selectPage(AiCallLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AiCallLogDO>()
                .eqIfPresent(AiCallLogDO::getBusinessCode, reqVO.getBusinessCode())
                .eqIfPresent(AiCallLogDO::getModelId, reqVO.getModelId())
                .likeIfPresent(AiCallLogDO::getModelName, reqVO.getModelName())
                .eqIfPresent(AiCallLogDO::getSuccess, reqVO.getSuccess())
                .eqIfPresent(AiCallLogDO::getOperatorId, reqVO.getOperatorId())
                .betweenIfPresent(AiCallLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AiCallLogDO::getId));
    }

    /** 自某时刻起的令牌合计；拒绝的调用没有令牌，不影响合计。 */
    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM ai_call_log WHERE create_time >= #{since} AND deleted = 0")
    Long selectTotalTokensSince(@Param("since") LocalDateTime since);

    /** 自某时刻起某人在页面上发起的模型调用次数；不含后台调用，也不含因额度或熔断被拒绝的调用。 */
    @Select("SELECT COUNT(*) FROM ai_call_log WHERE operator_id = #{operatorId} AND create_time >= #{since}"
            + " AND background = 0 AND deleted = 0"
            + " AND (error_type IS NULL OR error_type NOT IN ('QUOTA_EXCEEDED', 'CIRCUIT_OPEN'))")
    Long selectCallCountByOperatorSince(@Param("operatorId") Long operatorId, @Param("since") LocalDateTime since);

    @Select("<script>"
            + "SELECT DATE(create_time) AS statDate, business_code AS businessCode,"
            + " COUNT(*) AS callCount,"
            + " SUM(CASE WHEN success = 1 THEN 0 ELSE 1 END) AS failedCount,"
            + " COALESCE(SUM(prompt_tokens), 0) AS promptTokens,"
            + " COALESCE(SUM(completion_tokens), 0) AS completionTokens,"
            + " COALESCE(SUM(total_tokens), 0) AS totalTokens"
            + " FROM ai_call_log WHERE deleted = 0 AND create_time &gt;= #{begin} AND create_time &lt; #{end}"
            + "<if test='businessCode != null and businessCode != \"\"'> AND business_code = #{businessCode}</if>"
            + " GROUP BY DATE(create_time), business_code"
            + " ORDER BY statDate DESC, businessCode"
            + "</script>")
    List<Map<String, Object>> selectDailySummary(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
                                                 @Param("businessCode") String businessCode);

    @Delete("DELETE FROM ai_call_log WHERE create_time < #{createTime} LIMIT #{limit}")
    Integer deleteByCreateTimeLt(@Param("createTime") LocalDateTime createTime, @Param("limit") Integer limit);

}
