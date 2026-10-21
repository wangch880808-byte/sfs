package cn.nus.zknu.module.ai.service.log;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogDailySummaryRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogSummaryReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;
import cn.nus.zknu.module.ai.dal.mysql.log.AiCallLogMapper;
import cn.nus.zknu.module.ai.enums.model.AiCallBusiness;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiCallLogServiceImpl implements AiCallLogService {

    /** 待写入日志的排队上限；数据库长时间不可用时丢弃并告警，不占用内存。 */
    private static final int QUEUE_CAPACITY = 2000;
    private static final int DELETE_BATCH = 1000;
    private static final int DEFAULT_SUMMARY_DAYS = 7;
    private static final int MAX_SUMMARY_DAYS = 93;

    @Resource
    private AiCallLogMapper callLogMapper;
    @Resource
    private AdminUserApi adminUserApi;

    private final ThreadPoolExecutor writerPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY), task -> {
                Thread thread = new Thread(task, "ai-call-log-writer");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private Executor writer = writerPool;

    @PreDestroy
    public void shutdown() throws InterruptedException {
        writerPool.shutdown();
        writerPool.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Override
    public void record(AiCallLogDO callLog) {
        try {
            writer.execute(() -> insertQuietly(callLog));
        } catch (RejectedExecutionException ex) {
            log.warn("[record][AI 调用日志队列已满，丢弃一条] businessCode={}, success={}",
                    callLog.getBusinessCode(), callLog.getSuccess());
        }
    }

    private void insertQuietly(AiCallLogDO callLog) {
        try {
            // 写入线程创建时会继承当时请求的登录信息，这里清掉，避免误用
            SecurityContextHolder.clearContext();
            callLogMapper.insert(callLog);
        } catch (Exception ex) {
            log.warn("[insertQuietly][写入 AI 调用日志失败] businessCode={}, success={}",
                    callLog.getBusinessCode(), callLog.getSuccess(), ex);
        }
    }

    @Override
    public PageResult<AiCallLogRespVO> getPage(AiCallLogPageReqVO reqVO) {
        PageResult<AiCallLogDO> page = callLogMapper.selectPage(reqVO);
        List<Long> operatorIds = page.getList().stream().map(AiCallLogDO::getOperatorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, AdminUserRespDTO> users = operatorIds.isEmpty() ? Map.of() : loadUsers(operatorIds);
        List<AiCallLogRespVO> list = page.getList().stream().map(item -> {
            AiCallLogRespVO resp = new AiCallLogRespVO();
            resp.setId(item.getId());
            resp.setBusinessCode(item.getBusinessCode());
            resp.setBusinessName(AiCallBusiness.nameOf(item.getBusinessCode()));
            resp.setModelId(item.getModelId());
            resp.setModelName(item.getModelName());
            resp.setPlatform(item.getPlatform());
            resp.setBizId(item.getBizId());
            resp.setOperatorId(item.getOperatorId());
            AdminUserRespDTO user = item.getOperatorId() != null ? users.get(item.getOperatorId()) : null;
            resp.setOperatorName(user != null ? user.getNickname() : null);
            resp.setBackground(item.getBackground());
            resp.setPromptTokens(item.getPromptTokens());
            resp.setCompletionTokens(item.getCompletionTokens());
            resp.setTotalTokens(item.getTotalTokens());
            resp.setDurationMs(item.getDurationMs());
            resp.setSuccess(item.getSuccess());
            resp.setErrorType(item.getErrorType());
            resp.setErrorMessage(item.getErrorMessage());
            resp.setCreateTime(item.getCreateTime());
            return resp;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    private Map<Long, AdminUserRespDTO> loadUsers(List<Long> ids) {
        try {
            return adminUserApi.getUserMap(ids);
        } catch (Exception ex) {
            log.warn("[loadUsers][读取发起人失败]", ex);
            return Map.of();
        }
    }

    @Override
    public List<AiCallLogDailySummaryRespVO> getDailySummary(AiCallLogSummaryReqVO reqVO) {
        LocalDateTime[] range = reqVO.getCreateTime();
        LocalDateTime end = range != null && range.length > 1 && range[1] != null
                ? range[1] : LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime begin = range != null && range.length > 0 && range[0] != null
                ? range[0] : end.toLocalDate().minusDays(DEFAULT_SUMMARY_DAYS).atStartOfDay();
        if (begin.isBefore(end.minusDays(MAX_SUMMARY_DAYS))) {
            begin = end.minusDays(MAX_SUMMARY_DAYS);
        }
        return callLogMapper.selectDailySummary(begin, end, StrUtil.trimToNull(reqVO.getBusinessCode()))
                .stream().map(row -> {
                    AiCallLogDailySummaryRespVO resp = new AiCallLogDailySummaryRespVO();
                    resp.setDate(toLocalDate(value(row, "statDate")));
                    resp.setBusinessCode(Objects.toString(value(row, "businessCode"), null));
                    resp.setBusinessName(AiCallBusiness.nameOf(resp.getBusinessCode()));
                    resp.setCallCount(toLong(value(row, "callCount")));
                    resp.setFailedCount(toLong(value(row, "failedCount")));
                    resp.setPromptTokens(toLong(value(row, "promptTokens")));
                    resp.setCompletionTokens(toLong(value(row, "completionTokens")));
                    resp.setTotalTokens(toLong(value(row, "totalTokens")));
                    return resp;
                }).toList();
    }

    /** 列别名在不同驱动下大小写可能不同，按不区分大小写读取。 */
    private static Object value(Map<String, Object> row, String key) {
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        return row.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue).filter(Objects::nonNull).findFirst().orElse(null);
    }

    static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        return value != null ? LocalDate.parse(value.toString().substring(0, 10)) : null;
    }

    static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return new BigDecimal(value.toString()).longValue();
    }

    @Override
    public int cleanExpired(int retentionDays) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            Integer deleted = callLogMapper.deleteByCreateTimeLt(expireTime, DELETE_BATCH);
            int count = deleted != null ? deleted : 0;
            total += count;
            if (count < DELETE_BATCH) {
                break;
            }
        }
        return total;
    }

    @VisibleForTesting
    void setWriter(Executor writer) {
        this.writer = writer;
    }

}
