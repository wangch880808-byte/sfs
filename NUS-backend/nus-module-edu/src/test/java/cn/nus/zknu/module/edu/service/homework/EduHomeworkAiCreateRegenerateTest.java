package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduQuestionRewriteRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateRegenerateReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAiCreatePreviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_AI_CREATE_REGENERATE_BUSY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单题重新生成：后台执行、按令牌写回、失败保留原题、确认后丢弃迟到结果。
 */
class EduHomeworkAiCreateRegenerateTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkAiCreatePreviewDO.class);
    }

    @Mock
    private EduHomeworkAiCreatePreviewMapper previewMapper;
    @Mock
    private EduQuestionBankMapper questionBankMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private CapacityIndicatorApi indicatorMapper;
    @Mock
    private AiEduReviewApi aiEduReviewApi;
    @Mock
    private ConfigApi configApi;

    private final List<Runnable> queued = new ArrayList<>();
    private Executor executor = queued::add;
    private EduHomeworkAiCreateRegenerateService service;
    private EduHomeworkAiCreatePreviewDO stored;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        stored = new EduHomeworkAiCreatePreviewDO();
        stored.setId(5L);
        stored.setPreviewId("p-1");
        stored.setTeacherId(7L);
        stored.setTaskStatus("SUCCEEDED");
        stored.setConfirmed(false);
        stored.setExpireTime(LocalDateTime.now().plusHours(1));
        stored.setCourseId(30L);
        stored.setIndicatorSecondId(40L);
        stored.setHomeworkLevel(2);
        stored.setItemsJson(JSONUtil.toJsonStr(List.of(
                Map.of("previewItemId", "item-a", "sourceQuestionId", 100, "homeworkName", "旧标题",
                        "requirement", "旧要求", "success", true),
                Map.of("previewItemId", "item-b", "sourceQuestionId", 101, "homeworkName", "第二题",
                        "requirement", "第二题要求", "success", true))));

        service = new EduHomeworkAiCreateRegenerateService();
        ReflectionTestUtils.setField(service, "eduHomeworkAiCreatePreviewMapper", previewMapper);
        ReflectionTestUtils.setField(service, "eduQuestionBankMapper", questionBankMapper);
        ReflectionTestUtils.setField(service, "capacityCourseApi", courseMapper);
        ReflectionTestUtils.setField(service, "capacityIndicatorApi", indicatorMapper);
        EduHomeworkAiCreatePreviewSupport previewSupport = new EduHomeworkAiCreatePreviewSupport();
        ReflectionTestUtils.setField(previewSupport, "eduHomeworkAiCreatePreviewMapper", previewMapper);
        ReflectionTestUtils.setField(previewSupport, "aiEduReviewApi", aiEduReviewApi);
        ReflectionTestUtils.setField(previewSupport, "configApi", configApi);
        ReflectionTestUtils.setField(service, "previewSupport", previewSupport);
        ReflectionTestUtils.setField(service, "aiTaskExecutor", (Executor) task -> executor.execute(task));

        lenient().when(previewMapper.selectByPreviewId("p-1")).thenAnswer(invocation -> copy(stored));
        lenient().when(previewMapper.selectById(5L)).thenAnswer(invocation -> copy(stored));
        lenient().when(previewMapper.update(any(EduHomeworkAiCreatePreviewDO.class), any(LambdaUpdateWrapper.class)))
                .thenAnswer(invocation -> compareAndSet(invocation.getArgument(1)));
        EduQuestionBankDO question = new EduQuestionBankDO();
        question.setId(100L);
        question.setTitle("原题");
        question.setRequirement("原题要求");
        lenient().when(questionBankMapper.selectById(100L)).thenReturn(question);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void regenerateReturnsAtOnceAndAppliesResultInBackground() {
        EduHomeworkAiCreatePreviewRespVO.Item resp = service.regenerateAiCreateHomeworkQuestion(request());

        assertTrue(resp.getRegenerating());
        assertEquals("旧标题", resp.getHomeworkName());
        assertEquals(1, queued.size());
        verifyNoInteractions(aiEduReviewApi);
        assertTrue(item("item-a").getBool("regenerating"));

        when(aiEduReviewApi.rewriteQuestion(any())).thenReturn(rewrite(true, "新标题", "新要求", null));
        queued.get(0).run();

        JSONObject item = item("item-a");
        assertEquals("新标题", item.getStr("homeworkName"));
        assertEquals("新要求", item.getStr("requirement"));
        assertFalse(item.getBool("regenerating"));
        assertNull(item.getStr("regenerateToken"));
        assertEquals("第二题", item("item-b").getStr("homeworkName"));
    }

    @Test
    void failedRegenerationKeepsPreviousQuestionAndExplains() {
        service.regenerateAiCreateHomeworkQuestion(request());
        when(aiEduReviewApi.rewriteQuestion(any()))
                .thenReturn(rewrite(false, null, null, "今日 AI 用量已达上限，明天再试或联系管理员调整"));
        queued.get(0).run();

        JSONObject item = item("item-a");
        assertEquals("旧标题", item.getStr("homeworkName"));
        assertEquals("旧要求", item.getStr("requirement"));
        assertTrue(item.getBool("success"));
        assertFalse(item.getBool("regenerating"));
        assertTrue(item.getStr("errorMessage").startsWith("重新生成没有成功：今日 AI 用量已达上限"),
                item.getStr("errorMessage"));
        assertTrue(item.getStr("errorMessage").contains("原来的题目已保留"));
    }

    @Test
    void unexpectedErrorInBackgroundStillClearsFlag() {
        service.regenerateAiCreateHomeworkQuestion(request());
        when(aiEduReviewApi.rewriteQuestion(any())).thenThrow(new IllegalStateException("db down"));
        queued.get(0).run();

        JSONObject item = item("item-a");
        assertFalse(item.getBool("regenerating"));
        assertEquals("旧标题", item.getStr("homeworkName"));
        assertTrue(item.getStr("errorMessage").contains("AI 模型调用失败"));
    }

    @Test
    void repeatedClickWhileRunningDoesNotQueueAgain() {
        service.regenerateAiCreateHomeworkQuestion(request());
        EduHomeworkAiCreatePreviewRespVO.Item again = service.regenerateAiCreateHomeworkQuestion(request());

        assertTrue(again.getRegenerating());
        assertEquals(1, queued.size());
    }

    @Test
    void staleFlagAllowsRetry() {
        when(configApi.getConfigValueByKey("edu.ai-create.regenerate-timeout-minutes")).thenReturn("1");
        service.regenerateAiCreateHomeworkQuestion(request());
        JSONArray items = JSONUtil.parseArray(stored.getItemsJson());
        items.getJSONObject(0).set("regenerateStartedAt", System.currentTimeMillis() - 120_000L);
        stored.setItemsJson(items.toString());

        EduHomeworkAiCreatePreviewRespVO.Item resp = service.regenerateAiCreateHomeworkQuestion(request());

        assertTrue(resp.getRegenerating());
        assertEquals(2, queued.size());
    }

    @Test
    void rejectedExecutorClearsFlagAndReportsBusy() {
        executor = task -> {
            throw new TaskRejectedException("full");
        };

        assertServiceException(() -> service.regenerateAiCreateHomeworkQuestion(request()),
                HOMEWORK_AI_CREATE_REGENERATE_BUSY);

        JSONObject item = item("item-a");
        assertFalse(item.getBool("regenerating"));
        assertEquals("旧标题", item.getStr("homeworkName"));
        assertNull(item.getStr("errorMessage"));
    }

    @Test
    void lateResultIsDroppedAfterConfirmAndSupersededTokenIsIgnored() {
        service.regenerateAiCreateHomeworkQuestion(request());
        Runnable first = queued.get(0);
        stored.setConfirmed(true);
        String before = stored.getItemsJson();
        lenient().when(aiEduReviewApi.rewriteQuestion(any())).thenReturn(rewrite(true, "迟到标题", "迟到要求", null));

        first.run();

        assertEquals(before, stored.getItemsJson());

        // 令牌被新的重新生成替换后，旧任务的结果不会写回
        stored.setConfirmed(false);
        JSONArray items = JSONUtil.parseArray(stored.getItemsJson());
        items.getJSONObject(0).set("regenerateToken", "newer-token");
        stored.setItemsJson(items.toString());
        first.run();
        assertEquals("旧标题", item("item-a").getStr("homeworkName"));
    }

    private EduHomeworkAiCreateRegenerateReqVO request() {
        EduHomeworkAiCreateRegenerateReqVO req = new EduHomeworkAiCreateRegenerateReqVO();
        req.setPreviewId("p-1");
        req.setPreviewItemId("item-a");
        return req;
    }

    private JSONObject item(String previewItemId) {
        return JSONUtil.parseArray(stored.getItemsJson()).stream().map(JSONObject.class::cast)
                .filter(item -> previewItemId.equals(item.getStr("previewItemId"))).findFirst().orElseThrow();
    }

    /** 模拟数据库按 id + 原 JSON + 未确认 的条件更新。 */
    private int compareAndSet(LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO> wrapper) {
        wrapper.getSqlSegment(); // 条件参数在生成 SQL 时才登记
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        String newJson = (String) params.get("MPGENVAL1");
        Object id = params.get("MPGENVAL2");
        String expectedJson = (String) params.get("MPGENVAL3");
        Object confirmed = params.get("MPGENVAL4");
        if (!Long.valueOf(5L).equals(id) || !Boolean.FALSE.equals(confirmed)
                || Boolean.TRUE.equals(stored.getConfirmed()) || !stored.getItemsJson().equals(expectedJson)) {
            return 0;
        }
        stored.setItemsJson(newJson);
        return 1;
    }

    private static EduHomeworkAiCreatePreviewDO copy(EduHomeworkAiCreatePreviewDO source) {
        EduHomeworkAiCreatePreviewDO copy = new EduHomeworkAiCreatePreviewDO();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private static AiEduQuestionRewriteRespDTO rewrite(boolean success, String title, String requirement,
                                                       String error) {
        AiEduQuestionRewriteRespDTO dto = new AiEduQuestionRewriteRespDTO();
        dto.setSuccess(success);
        dto.setTitle(title);
        dto.setRequirement(requirement);
        dto.setErrorMessage(error);
        return dto;
    }

}
