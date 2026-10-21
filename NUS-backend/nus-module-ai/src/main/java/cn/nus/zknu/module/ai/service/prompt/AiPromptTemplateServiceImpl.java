package cn.nus.zknu.module.ai.service.prompt;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateCreateReqVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateRespVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateSummaryRespVO;
import cn.nus.zknu.module.ai.dal.dataobject.prompt.AiPromptTemplateDO;
import cn.nus.zknu.module.ai.dal.mysql.prompt.AiPromptTemplateMapper;
import cn.nus.zknu.module.ai.enums.prompt.AiPromptCodeEnum;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    /** text 列按 utf8mb4 最多约 21000 个汉字，留出余量。 */
    static final int MAX_CONTENT_LENGTH = 20000;

    @Resource
    private AiPromptTemplateMapper promptTemplateMapper;
    @Resource
    private NusAiProperties aiProperties;
    @Resource
    private AdminUserApi adminUserApi;

    private final Map<String, CachedPrompt> cache = new ConcurrentHashMap<>();
    private LongSupplier nanoClock = System::nanoTime;

    @Override
    public String getActiveContent(AiPromptCodeEnum code) {
        long now = nanoClock.getAsLong();
        CachedPrompt cached = cache.get(code.getCode());
        if (cached != null && now - cached.loadedAt() < cacheTtlNanos()) {
            return cached.content();
        }
        String content = loadActiveContent(code);
        cache.put(code.getCode(), new CachedPrompt(content, now));
        return content;
    }

    private String loadActiveContent(AiPromptCodeEnum code) {
        try {
            AiPromptTemplateDO active = promptTemplateMapper.selectActive(code.getCode());
            if (active != null && StrUtil.isNotBlank(active.getContent())) {
                return active.getContent();
            }
        } catch (Exception ex) {
            // 提示词表不可用时不能让 AI 调用失败，回退到内置内容
            log.warn("[loadActiveContent][读取提示词失败，使用内置内容] businessCode={}", code.getCode(), ex);
        }
        return code.getDefaultContent();
    }

    private long cacheTtlNanos() {
        return Math.max(0L, aiProperties.getPromptCacheTtl().toNanos());
    }

    @Override
    public List<AiPromptTemplateSummaryRespVO> getSummaryList() {
        return Arrays.stream(AiPromptCodeEnum.values()).map(code -> {
            AiPromptTemplateDO active = promptTemplateMapper.selectActive(code.getCode());
            AiPromptTemplateDO latest = promptTemplateMapper.selectLatest(code.getCode());
            AiPromptTemplateSummaryRespVO resp = new AiPromptTemplateSummaryRespVO();
            resp.setBusinessCode(code.getCode());
            resp.setName(code.getName());
            resp.setBusinessName(code.getBusiness().getName());
            resp.setDefaultContent(code.getDefaultContent());
            resp.setLatestVersion(latest != null ? latest.getVersion() : null);
            boolean usingDefault = active == null || StrUtil.isBlank(active.getContent());
            resp.setUsingDefault(usingDefault);
            resp.setContent(usingDefault ? code.getDefaultContent() : active.getContent());
            resp.setSameAsDefault(Objects.equals(resp.getContent(), code.getDefaultContent()));
            if (!usingDefault) {
                resp.setActiveId(active.getId());
                resp.setActiveVersion(active.getVersion());
                resp.setActiveTime(active.getCreateTime());
            }
            return resp;
        }).toList();
    }

    @Override
    public List<AiPromptTemplateRespVO> getHistory(String businessCode) {
        AiPromptCodeEnum code = validateCode(businessCode);
        List<AiPromptTemplateDO> list = promptTemplateMapper.selectListByBusinessCode(code.getCode());
        Map<Long, AdminUserRespDTO> users = loadCreators(list);
        return list.stream().map(item -> {
            AiPromptTemplateRespVO resp = new AiPromptTemplateRespVO();
            resp.setId(item.getId());
            resp.setBusinessCode(item.getBusinessCode());
            resp.setVersion(item.getVersion());
            resp.setContent(item.getContent());
            resp.setActive(Boolean.TRUE.equals(item.getActive()));
            resp.setRemark(item.getRemark());
            resp.setCreateTime(item.getCreateTime());
            Long creatorId = parseUserId(item.getCreator());
            AdminUserRespDTO user = creatorId == null ? null : users.get(creatorId);
            resp.setCreatorName("system".equals(item.getCreator()) ? "系统内置"
                    : user != null ? user.getNickname() : "用户已不可用");
            return resp;
        }).toList();
    }

    private Map<Long, AdminUserRespDTO> loadCreators(Collection<AiPromptTemplateDO> list) {
        List<Long> ids = list.stream().map(item -> parseUserId(item.getCreator()))
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return adminUserApi.getUserMap(ids);
        } catch (Exception ex) {
            log.warn("[loadCreators][读取保存人失败]", ex);
            return Map.of();
        }
    }

    private static Long parseUserId(String creator) {
        return StrUtil.isNumeric(creator) ? Long.valueOf(creator) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(AiPromptTemplateCreateReqVO reqVO) {
        AiPromptCodeEnum code = validateCode(reqVO.getBusinessCode());
        String content = validateContent(code, reqVO.getContent());
        AiPromptTemplateDO latest = promptTemplateMapper.selectLatest(code.getCode());
        AiPromptTemplateDO record = new AiPromptTemplateDO();
        record.setBusinessCode(code.getCode());
        record.setVersion(latest != null && latest.getVersion() != null ? latest.getVersion() + 1 : 1);
        record.setContent(content);
        record.setActive(false);
        record.setRemark(StrUtil.trimToNull(reqVO.getRemark()));
        try {
            promptTemplateMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw exception(PROMPT_TEMPLATE_VERSION_CONFLICT);
        }
        if (!Boolean.FALSE.equals(reqVO.getActivate())) {
            activate(record);
        }
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateVersion(Long id) {
        AiPromptTemplateDO record = promptTemplateMapper.selectById(id);
        if (record == null) {
            throw exception(PROMPT_TEMPLATE_NOT_EXISTS);
        }
        validateCode(record.getBusinessCode());
        activate(record);
    }

    private void activate(AiPromptTemplateDO record) {
        promptTemplateMapper.deactivateOthers(record.getBusinessCode(), record.getId());
        promptTemplateMapper.activate(record.getId());
        evictAfterCommit(record.getBusinessCode());
    }

    private void evictAfterCommit(String businessCode) {
        cache.remove(businessCode);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 提交前被其他请求读到的旧内容，在提交后再清一次
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.remove(businessCode);
                }
            });
        }
    }

    private AiPromptCodeEnum validateCode(String businessCode) {
        return AiPromptCodeEnum.of(businessCode).orElseThrow(() -> exception(PROMPT_TEMPLATE_BUSINESS_INVALID));
    }

    private String validateContent(AiPromptCodeEnum code, String content) {
        String normalized = StrUtil.nullToEmpty(content).replace("\r\n", "\n").strip();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw exception(PROMPT_TEMPLATE_TOO_LONG, MAX_CONTENT_LENGTH);
        }
        for (String fragment : code.getRequiredFragments()) {
            if (!normalized.contains(fragment)) {
                throw exception(PROMPT_TEMPLATE_REQUIRED_FRAGMENT, fragment);
            }
        }
        return normalized;
    }

    @VisibleForTesting
    void setNanoClock(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    private record CachedPrompt(String content, long loadedAt) {
    }

}
