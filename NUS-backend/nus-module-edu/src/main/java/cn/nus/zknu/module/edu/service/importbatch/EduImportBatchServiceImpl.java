package cn.nus.zknu.module.edu.service.importbatch;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.idev.excel.FastExcelFactory;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportErrorExcelVO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchItemMapper;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchMapper;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportBatchStatusEnum;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class EduImportBatchServiceImpl implements EduImportBatchService, ApplicationRunner {

    static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    static final int MAX_ROW_COUNT = 10_000;
    private static final int PREVIEW_EXPIRE_HOURS = 2;
    private static final int DOWNLOAD_URL_EXPIRE_SECONDS = 300;

    static final String CONFIRM_TIMEOUT_KEY = "edu.import.confirm-timeout-minutes";
    static final String FAILURE_REASON = "导入失败，数据未写入，请稍后重试或联系管理员";
    static final String TIMEOUT_REASON = "导入因服务重启或超时中断，数据未写入，请重新上传";
    private static final int DEFAULT_CONFIRM_TIMEOUT_MINUTES = 30;

    private final Map<String, EduImportHandler> handlerMap;

    @Resource
    private EduImportBatchMapper batchMapper;
    @Resource
    private EduImportBatchItemMapper itemMapper;
    @Resource
    private FileApi fileApi;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource(name = "eduImportExecutor")
    private TaskExecutor importExecutor;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private ConfigApi configApi;

    public EduImportBatchServiceImpl(List<EduImportHandler> handlers) {
        this.handlerMap = handlers.stream().collect(Collectors.toUnmodifiableMap(
                handler -> handlerKey(handler.getBizType(), handler.getTemplateVersion()),
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("重复的导入处理器: " + left.getBizType());
                }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduImportBatchRespVO preview(String bizType, String templateVersion, String optionsJson,
                                        MultipartFile file) {
        byte[] content = validateAndReadFile(file);
        validateOptions(optionsJson);
        EduImportHandler handler = getHandler(bizType, templateVersion);
        handler.validatePermission();
        List<EduImportPreviewRow> rows = Optional.ofNullable(handler.preview(content, optionsJson))
                .orElseGet(Collections::emptyList);
        validatePreviewRows(rows);

        String sourceFileUrl = null;
        String errorFileUrl = null;
        try {
            String safeFileName = FileNameUtil.getName(file.getOriginalFilename());
            sourceFileUrl = fileApi.createFile(content, safeFileName,
                    "edu/import/" + normalize(bizType).toLowerCase(Locale.ROOT),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            if (rows.stream().anyMatch(this::isInvalid)) {
                errorFileUrl = createErrorFile(rows, bizType);
            }
            EduImportBatchDO batch = buildBatch(bizType, templateVersion, optionsJson, safeFileName,
                    content, sourceFileUrl, errorFileUrl, rows);
            batchMapper.insert(batch);
            List<EduImportBatchItemDO> items = rows.stream()
                    .map(row -> buildItem(batch.getId(), row))
                    .toList();
            if (!items.isEmpty()) {
                itemMapper.insertBatch(items);
            }
            EduImportBatchRespVO respVO = toRespVO(batch);
            respVO.setSkipReasonCounts(countSkipReasons(rows.stream()
                    .filter(row -> !isInvalid(row) && "SKIP".equals(row.getAction().name()))
                    .map(EduImportPreviewRow::getNormalizedData)));
            return respVO;
        } catch (RuntimeException ex) {
            deleteQuietly(errorFileUrl);
            deleteQuietly(sourceFileUrl);
            throw ex;
        }
    }

    @Override
    public EduImportBatchRespVO confirm(String batchNo, String initialPassword) {
        EduImportBatchDO batch = getOwnedBatch(batchNo);
        if (isConfirmedOrConfirming(batch)) {
            return toRespVO(batch);
        }
        if (!EduImportBatchStatusEnum.PREVIEW_READY.getStatus().equals(batch.getStatus())) {
            throw exception(IMPORT_BATCH_STATUS_INVALID);
        }
        if (batch.getInvalidCount() != null && batch.getInvalidCount() > 0) {
            throw exception(IMPORT_BATCH_HAS_ERRORS);
        }
        if (batch.getExpireTime() == null || batch.getExpireTime().isBefore(LocalDateTime.now())) {
            throw exception(IMPORT_BATCH_EXPIRED);
        }
        EduImportHandler handler = getHandler(batch.getBizType(), batch.getTemplateVersion());
        handler.validatePermission();
        LocalDateTime startedAt = LocalDateTime.now();
        // 独立提交领取状态；异步任务开始前，轮询已经能看到 CONFIRMING。
        int claimed = newTransaction().execute(status -> batchMapper.claimForConfirmation(batch.getId(),
                EduImportBatchStatusEnum.PREVIEW_READY.getStatus(),
                EduImportBatchStatusEnum.CONFIRMING.getStatus(), startedAt));
        if (claimed == 0) {
            // 两个确认请求同时通过校验时，只有领取成功者派发任务。
            EduImportBatchDO current = getOwnedBatch(batchNo);
            if (isConfirmedOrConfirming(current)) {
                return toRespVO(current);
            }
            throw exception(IMPORT_BATCH_STATUS_INVALID);
        }
        batch.setStatus(EduImportBatchStatusEnum.CONFIRMING.getStatus());
        batch.setConfirmTime(startedAt);
        EduImportBatchRespVO response = toRespVO(batch);
        try {
            // 密码只由闭包在内存中传递，不写入批次或日志。
            importExecutor.execute(() -> commitAsync(batch.getId(), handler, StrUtil.trimToNull(initialPassword)));
        } catch (RejectedExecutionException ex) {
            newTransaction().executeWithoutResult(status -> batchMapper.resetConfirmation(batch.getId()));
            throw exception(IMPORT_EXECUTOR_BUSY);
        }
        return response;
    }

    private boolean isConfirmedOrConfirming(EduImportBatchDO batch) {
        return EduImportBatchStatusEnum.SUCCESS.getStatus().equals(batch.getStatus())
                || EduImportBatchStatusEnum.CONFIRMING.getStatus().equals(batch.getStatus());
    }

    private void commitAsync(Long batchId, EduImportHandler handler, String initialPassword) {
        try {
            newTransaction().executeWithoutResult(status -> {
                EduImportBatchDO batch = batchMapper.selectById(batchId);
                if (batch == null || !EduImportBatchStatusEnum.CONFIRMING.getStatus().equals(batch.getStatus())) {
                    return; // 排队期间已被超时回收，不再写业务数据。
                }
                List<EduImportBatchItemDO> validItems = itemMapper.selectListByBatchId(batchId).stream()
                        .filter(item -> "VALID".equals(item.getValidationStatus()))
                        .toList();
                EduImportCommitResult result = handler.commit(batch, validItems, initialPassword);
                batch.setStatus(EduImportBatchStatusEnum.SUCCESS.getStatus());
                batch.setConfirmTime(LocalDateTime.now());
                batch.setInsertedCount(result.insertedCount());
                batch.setUpdatedCount(result.updatedCount());
                batch.setSkippedCount(result.skippedCount());
                // SUCCESS 必须仍能从 CONFIRMING 转换；超时回收先获胜时，全部业务写入回滚。
                if (batchMapper.completeConfirmation(batch) != 1) {
                    throw new IllegalStateException("导入已中断或结果状态未保存");
                }
            });
        } catch (Exception ex) {
            log.error("[commitAsync][导入批次执行失败 batchId={}]", batchId, ex);
            String reason = ex instanceof ServiceException ? ex.getMessage() : FAILURE_REASON;
            // 此时业务事务已回滚；失败状态必须在另一个事务中持久化。
            newTransaction().executeWithoutResult(status -> batchMapper.failConfirmation(batchId, reason));
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private int confirmTimeoutMinutes() {
        try {
            String value = configApi.getConfigValueByKey(CONFIRM_TIMEOUT_KEY);
            if (StrUtil.isBlank(value)) {
                return DEFAULT_CONFIRM_TIMEOUT_MINUTES;
            }
            int minutes = Integer.parseInt(value.trim());
            if (minutes > 0) {
                return minutes;
            }
        } catch (Exception ex) {
            // 配置服务不可用或参数非法时使用默认值。
        }
        log.warn("[confirmTimeoutMinutes][导入超时配置无效或不可用，使用默认值 {}]", DEFAULT_CONFIRM_TIMEOUT_MINUTES);
        return DEFAULT_CONFIRM_TIMEOUT_MINUTES;
    }

    /**
     * 服务启动时，重启前仍在导入的批次已随旧进程中断（导入事务未提交，数据未写入），直接标记失败，
     * 不必等到超时。部署为单个后端实例，启动时不会有其他实例在执行这些批次。
     */
    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime cutoff = LocalDateTime.now();
        int recovered = newTransaction().execute(status -> batchMapper.failTimedOutConfirmations(null, cutoff, TIMEOUT_REASON));
        if (recovered > 0) {
            log.warn("[run][已回收超时导入批次 count={}]", recovered);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduImportBatchRespVO cancel(String batchNo) {
        EduImportBatchDO batch = getOwnedBatch(batchNo);
        getHandler(batch.getBizType(), batch.getTemplateVersion()).validatePermission();
        if (!EduImportBatchStatusEnum.PREVIEW_READY.getStatus().equals(batch.getStatus())
                && !EduImportBatchStatusEnum.PREVIEW_INVALID.getStatus().equals(batch.getStatus())) {
            throw exception(IMPORT_BATCH_STATUS_INVALID);
        }
        if (batchMapper.cancelPreview(batch.getId()) != 1) {
            throw exception(IMPORT_BATCH_STATUS_INVALID);
        }
        batch.setStatus(EduImportBatchStatusEnum.CANCELLED.getStatus());
        return toRespVO(batch);
    }

    @Override
    public EduImportBatchRespVO get(String batchNo) {
        EduImportBatchDO batch = getOwnedBatch(batchNo);
        getHandler(batch.getBizType(), batch.getTemplateVersion()).validatePermission();
        if (EduImportBatchStatusEnum.CONFIRMING.getStatus().equals(batch.getStatus())) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(confirmTimeoutMinutes());
            if (batch.getConfirmTime() != null && batch.getConfirmTime().isBefore(cutoff)) {
                Long batchId = batch.getId();
                newTransaction().executeWithoutResult(status ->
                        batchMapper.failTimedOutConfirmations(batchId, cutoff, TIMEOUT_REASON));
                // 条件更新可能因后台刚提交 SUCCESS 而不命中，返回数据库中的实际状态。
                batch = getOwnedBatch(batchNo);
            }
        }
        EduImportBatchRespVO respVO = toRespVO(batch);
        // 数据库排序规则可能忽略大小写或尾部空格，保留原先严格的状态比较口径。
        respVO.setSkipReasonCounts(countSkipReasons(itemMapper.selectSkipReasonRows(batch.getId()).stream()
                .filter(item -> "VALID".equals(item.getValidationStatus()) && "SKIP".equals(item.getAction()))
                .map(EduImportBatchItemDO::getNormalizedData)));
        return respVO;
    }

    @Override
    public PageResult<EduImportBatchRespVO> getPage(EduImportBatchPageReqVO reqVO) {
        Long operatorId = eduRoleApi.isCurrentUserSuperAdmin() ? null : getLoginUserId();
        PageResult<EduImportBatchDO> page = batchMapper.selectPage(reqVO, operatorId);
        return new PageResult<>(page.getList().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public PageResult<EduImportBatchItemRespVO> getItemPage(String batchNo, PageParam pageParam) {
        EduImportBatchDO batch = getOwnedBatch(batchNo);
        getHandler(batch.getBizType(), batch.getTemplateVersion()).validatePermission();
        PageResult<EduImportBatchItemDO> page = itemMapper.selectPageByBatchId(pageParam, batch.getId());
        return new PageResult<>(BeanUtils.toBean(page.getList(), EduImportBatchItemRespVO.class), page.getTotal());
    }

    @Override
    public String getErrorFileDownloadUrl(String batchNo) {
        EduImportBatchDO batch = getOwnedBatch(batchNo);
        getHandler(batch.getBizType(), batch.getTemplateVersion()).validatePermission();
        if (StrUtil.isBlank(batch.getErrorFileUrl())) {
            throw exception(IMPORT_ERROR_FILE_NOT_FOUND);
        }
        try {
            return fileApi.presignGetUrl(batch.getErrorFileUrl(), DOWNLOAD_URL_EXPIRE_SECONDS);
        } catch (UnsupportedOperationException ex) {
            return batch.getErrorFileUrl();
        }
    }

    private byte[] validateAndReadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw exception(IMPORT_FILE_REQUIRED);
        }
        String fileName = FileNameUtil.getName(file.getOriginalFilename());
        if (!"xlsx".equalsIgnoreCase(FileNameUtil.extName(fileName))) {
            throw exception(IMPORT_FILE_TYPE_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw exception(IMPORT_FILE_SIZE_EXCEEDED);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length < 2 || content[0] != 'P' || content[1] != 'K') {
                throw exception(IMPORT_FILE_TYPE_INVALID);
            }
            return content;
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(IMPORT_FILE_REQUIRED);
        }
    }

    private void validateOptions(String optionsJson) {
        if (StrUtil.isBlank(optionsJson)) {
            return;
        }
        try {
            JSONUtil.parseObj(optionsJson);
        } catch (Exception ex) {
            throw exception(IMPORT_OPTIONS_INVALID);
        }
    }

    private void validatePreviewRows(List<EduImportPreviewRow> rows) {
        if (rows.isEmpty()) {
            throw exception(IMPORT_FILE_EMPTY);
        }
        if (rows.size() > MAX_ROW_COUNT) {
            throw exception(IMPORT_ROW_LIMIT_EXCEEDED);
        }
        Set<String> rowKeys = new HashSet<>();
        for (EduImportPreviewRow row : rows) {
            if (row == null || row.getRowNumber() == null || row.getRowNumber() < 2 || row.getAction() == null
                    || !rowKeys.add(StrUtil.blankToDefault(row.getSheetName(), "") + ":" + row.getRowNumber())) {
                throw exception(IMPORT_PREVIEW_INVALID);
            }
        }
    }

    private EduImportBatchDO buildBatch(String bizType, String templateVersion, String optionsJson,
                                        String sourceFileName, byte[] content, String sourceFileUrl,
                                        String errorFileUrl, List<EduImportPreviewRow> rows) {
        int invalidCount = (int) rows.stream().filter(this::isInvalid).count();
        EduImportBatchDO batch = new EduImportBatchDO();
        batch.setBatchNo("IMP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        batch.setBizType(normalize(bizType));
        batch.setTemplateVersion(templateVersion.trim());
        batch.setSourceFileName(sourceFileName);
        batch.setSourceFileUrl(sourceFileUrl);
        batch.setSourceFileSha256(DigestUtil.sha256Hex(content));
        batch.setStatus(invalidCount == 0 ? EduImportBatchStatusEnum.PREVIEW_READY.getStatus()
                : EduImportBatchStatusEnum.PREVIEW_INVALID.getStatus());
        batch.setTotalCount(rows.size());
        batch.setValidCount(rows.size() - invalidCount);
        batch.setInvalidCount(invalidCount);
        batch.setInsertedCount(countPlannedRows(rows, "INSERT"));
        batch.setUpdatedCount(countPlannedRows(rows, "UPDATE"));
        batch.setSkippedCount(countPlannedRows(rows, "SKIP"));
        batch.setOptionsJson(StrUtil.blankToDefault(optionsJson, "{}"));
        batch.setErrorFileUrl(errorFileUrl);
        batch.setOperatorId(getLoginUserId());
        batch.setExpireTime(LocalDateTime.now().plusHours(PREVIEW_EXPIRE_HOURS));
        return batch;
    }

    private EduImportBatchItemDO buildItem(Long batchId, EduImportPreviewRow row) {
        EduImportBatchItemDO item = new EduImportBatchItemDO();
        item.setBatchId(batchId);
        item.setSheetName(StrUtil.blankToDefault(row.getSheetName(), "数据"));
        item.setRowNumber(row.getRowNumber());
        item.setBusinessKey(row.getBusinessKey());
        item.setNormalizedData(row.getNormalizedData());
        item.setAction(row.getAction().name());
        item.setValidationStatus(isInvalid(row) ? "INVALID" : "VALID");
        item.setValidationErrors(JSONUtil.toJsonStr(Optional.ofNullable(row.getErrors()).orElseGet(Collections::emptyList)));
        item.setBeforeData(row.getBeforeData());
        item.setAfterData(row.getAfterData());
        return item;
    }

    private String createErrorFile(List<EduImportPreviewRow> rows, String bizType) {
        List<EduImportErrorExcelVO> errors = rows.stream()
                .filter(this::isInvalid)
                .map(row -> new EduImportErrorExcelVO(row.getSheetName(), row.getRowNumber(), row.getBusinessKey(),
                        String.join("；", row.getErrors())))
                .toList();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FastExcelFactory.write(output, EduImportErrorExcelVO.class)
                    .sheet("错误回执").doWrite(errors);
            return fileApi.createFile(output.toByteArray(), "导入错误回执.xlsx",
                    "edu/import/error/" + normalize(bizType).toLowerCase(Locale.ROOT),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    /**
     * 跳过行按 normalizedData 里的 skipReason 计数（成绩册用来区分「未改动」「未填成绩」等）。
     * 业务没有写 skipReason 时返回 null，前端照旧只显示跳过总数。
     */
    static Map<String, Integer> countSkipReasons(java.util.stream.Stream<String> normalizedData) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        normalizedData.map(EduImportBatchServiceImpl::skipReasonOf)
                .filter(Objects::nonNull)
                .forEach(reason -> counts.merge(reason, 1, Integer::sum));
        return counts.isEmpty() ? null : counts;
    }

    private static String skipReasonOf(String normalizedData) {
        if (StrUtil.isBlank(normalizedData)) {
            return null;
        }
        try {
            return StrUtil.trimToNull(JSONUtil.parseObj(normalizedData).getStr("skipReason"));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isInvalid(EduImportPreviewRow row) {
        return row.getErrors() != null && !row.getErrors().isEmpty();
    }

    private int countPlannedRows(List<EduImportPreviewRow> rows, String action) {
        return (int) rows.stream()
                .filter(row -> !isInvalid(row) && action.equals(row.getAction().name()))
                .count();
    }

    private EduImportHandler getHandler(String bizType, String templateVersion) {
        EduImportHandler handler = handlerMap.get(handlerKey(bizType, templateVersion));
        if (handler == null) {
            throw exception(IMPORT_HANDLER_NOT_FOUND, bizType, templateVersion);
        }
        return handler;
    }

    private EduImportBatchDO getOwnedBatch(String batchNo) {
        EduImportBatchDO batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null || (!Objects.equals(batch.getOperatorId(), getLoginUserId())
                && !eduRoleApi.isCurrentUserSuperAdmin())) {
            throw exception(IMPORT_BATCH_NOT_FOUND);
        }
        return batch;
    }

    private EduImportBatchRespVO toRespVO(EduImportBatchDO batch) {
        EduImportBatchRespVO respVO = BeanUtils.toBean(batch, EduImportBatchRespVO.class);
        respVO.setHasErrorFile(StrUtil.isNotBlank(batch.getErrorFileUrl()));
        EduImportHandler handler = handlerMap.get(handlerKey(batch.getBizType(), batch.getTemplateVersion()));
        if (handler != null) {
            respVO.setInitialPasswordMode(handler.getInitialPasswordMode());
            respVO.setDefaultInitialPassword(handler.getDefaultInitialPassword());
        }
        return respVO;
    }

    private void deleteQuietly(String url) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        try {
            fileApi.deleteFile(url);
        } catch (Exception ignored) {
            // Best-effort cleanup for an upload whose database transaction failed.
        }
    }

    private static String handlerKey(String bizType, String templateVersion) {
        return normalize(bizType) + ":" + StrUtil.blankToDefault(templateVersion, "").trim();
    }

    private static String normalize(String value) {
        return StrUtil.blankToDefault(value, "").trim().toUpperCase(Locale.ROOT);
    }

}
