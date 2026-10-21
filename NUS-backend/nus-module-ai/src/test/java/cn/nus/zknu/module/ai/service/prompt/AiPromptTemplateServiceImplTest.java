package cn.nus.zknu.module.ai.service.prompt;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateCreateReqVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateSummaryRespVO;
import cn.nus.zknu.module.ai.dal.dataobject.prompt.AiPromptTemplateDO;
import cn.nus.zknu.module.ai.dal.mysql.prompt.AiPromptTemplateMapper;
import cn.nus.zknu.module.ai.enums.prompt.AiPromptCodeEnum;
import cn.nus.zknu.module.ai.enums.prompt.AiPromptDefaults;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiPromptTemplateServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiPromptTemplateServiceImpl service;
    @Mock
    private AiPromptTemplateMapper mapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Spy
    private NusAiProperties properties = new NusAiProperties();

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    @BeforeEach
    void setUp() {
        properties.setPromptCacheTtl(Duration.ofSeconds(30));
        service.setNanoClock(now::get);
    }

    @Test
    void activeVersionIsUsedAndCachedUntilTtl() {
        when(mapper.selectActive("HOMEWORK_REWRITE")).thenReturn(version(3, "自定义改写提示 JSON title requirement"));

        assertEquals("自定义改写提示 JSON title requirement", service.getActiveContent(AiPromptCodeEnum.HOMEWORK_REWRITE));
        now.addAndGet(Duration.ofSeconds(29).toNanos());
        assertEquals("自定义改写提示 JSON title requirement", service.getActiveContent(AiPromptCodeEnum.HOMEWORK_REWRITE));
        verify(mapper, times(1)).selectActive("HOMEWORK_REWRITE");

        now.addAndGet(Duration.ofSeconds(2).toNanos());
        service.getActiveContent(AiPromptCodeEnum.HOMEWORK_REWRITE);
        verify(mapper, times(2)).selectActive("HOMEWORK_REWRITE");
    }

    @Test
    void fallsBackToBuiltInDefaultWhenNothingActiveOrDatabaseFails() {
        when(mapper.selectActive("CLASS_ANALYSIS")).thenReturn(null);
        when(mapper.selectActive("STUDENT_COMMENT")).thenReturn(version(2, "   "));
        when(mapper.selectActive("HOMEWORK_REVIEW")).thenThrow(new IllegalStateException("table missing"));

        assertEquals(AiPromptDefaults.CLASS_ANALYSIS, service.getActiveContent(AiPromptCodeEnum.CLASS_ANALYSIS));
        assertEquals(AiPromptDefaults.STUDENT_COMMENT, service.getActiveContent(AiPromptCodeEnum.STUDENT_COMMENT));
        assertEquals(AiPromptDefaults.HOMEWORK_REVIEW, service.getActiveContent(AiPromptCodeEnum.HOMEWORK_REVIEW));
    }

    @Test
    void createVersionIncrementsVersionActivatesAndEvictsCache() {
        when(mapper.selectActive("CLASS_ANALYSIS")).thenReturn(null);
        assertEquals(AiPromptDefaults.CLASS_ANALYSIS, service.getActiveContent(AiPromptCodeEnum.CLASS_ANALYSIS));
        when(mapper.selectLatest("CLASS_ANALYSIS")).thenReturn(version(4, "旧版本"));
        doAnswer(invocation -> {
            invocation.<AiPromptTemplateDO>getArgument(0).setId(99L);
            return 1;
        }).when(mapper).insert(any(AiPromptTemplateDO.class));

        AiPromptTemplateCreateReqVO req = new AiPromptTemplateCreateReqVO();
        req.setBusinessCode("CLASS_ANALYSIS");
        req.setContent("  新的班级分析提示\r\n第二行  ");
        req.setRemark("调整结构");
        assertEquals(99L, service.createVersion(req));

        ArgumentCaptor<AiPromptTemplateDO> captor = ArgumentCaptor.forClass(AiPromptTemplateDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(5, captor.getValue().getVersion());
        assertEquals("新的班级分析提示\n第二行", captor.getValue().getContent());
        assertFalse(captor.getValue().getActive());
        verify(mapper).deactivateOthers("CLASS_ANALYSIS", 99L);
        verify(mapper).activate(99L);

        // 保存后本实例立即读到新内容，不等缓存过期
        when(mapper.selectActive("CLASS_ANALYSIS")).thenReturn(version(5, "新的班级分析提示\n第二行"));
        assertEquals("新的班级分析提示\n第二行", service.getActiveContent(AiPromptCodeEnum.CLASS_ANALYSIS));
    }

    @Test
    void createVersionWithoutActivateKeepsCurrentVersion() {
        when(mapper.selectLatest("STUDENT_ANALYSIS")).thenReturn(null);
        AiPromptTemplateCreateReqVO req = new AiPromptTemplateCreateReqVO();
        req.setBusinessCode("STUDENT_ANALYSIS");
        req.setContent("草稿");
        req.setActivate(false);

        service.createVersion(req);

        ArgumentCaptor<AiPromptTemplateDO> captor = ArgumentCaptor.forClass(AiPromptTemplateDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        verify(mapper, never()).activate(any());
        verify(mapper, never()).deactivateOthers(any(), any());
    }

    @Test
    void createVersionRejectsMissingRequiredFragmentUnknownCodeAndConflict() {
        AiPromptTemplateCreateReqVO req = new AiPromptTemplateCreateReqVO();
        req.setBusinessCode("HOMEWORK_REVIEW");
        req.setContent("请评分并返回 JSON：score comment confidence");
        assertServiceException(() -> service.createVersion(req), PROMPT_TEMPLATE_REQUIRED_FRAGMENT, "[学生作答开始]");

        req.setBusinessCode("NOT_A_PROMPT");
        assertServiceException(() -> service.createVersion(req), PROMPT_TEMPLATE_BUSINESS_INVALID);

        req.setBusinessCode("HOMEWORK_REWRITE");
        req.setContent("返回 JSON：title requirement");
        when(mapper.selectLatest("HOMEWORK_REWRITE")).thenReturn(version(1, AiPromptDefaults.HOMEWORK_REWRITE));
        when(mapper.insert(any(AiPromptTemplateDO.class))).thenThrow(new DuplicateKeyException("uk"));
        assertServiceException(() -> service.createVersion(req), PROMPT_TEMPLATE_VERSION_CONFLICT);
    }

    @Test
    void activateVersionSwitchesActiveRow() {
        AiPromptTemplateDO target = version(2, "第二版");
        target.setId(12L);
        when(mapper.selectById(12L)).thenReturn(target);

        service.activateVersion(12L);

        verify(mapper).deactivateOthers("HOMEWORK_REWRITE", 12L);
        verify(mapper).activate(12L);
        when(mapper.selectById(13L)).thenReturn(null);
        assertServiceException(() -> service.activateVersion(13L), PROMPT_TEMPLATE_NOT_EXISTS);
    }

    @Test
    void summaryMarksDefaultUsage() {
        when(mapper.selectActive(any())).thenReturn(null);
        AiPromptTemplateDO custom = version(2, "自定义 JSON title requirement");
        custom.setId(7L);
        when(mapper.selectActive("HOMEWORK_REWRITE")).thenReturn(custom);
        when(mapper.selectLatest(any())).thenReturn(null);

        List<AiPromptTemplateSummaryRespVO> list = service.getSummaryList();

        assertEquals(AiPromptCodeEnum.values().length, list.size());
        AiPromptTemplateSummaryRespVO rewrite = list.stream()
                .filter(item -> item.getBusinessCode().equals("HOMEWORK_REWRITE")).findFirst().orElseThrow();
        assertFalse(rewrite.getUsingDefault());
        assertFalse(rewrite.getSameAsDefault());
        assertEquals(2, rewrite.getActiveVersion());
        AiPromptTemplateSummaryRespVO review = list.stream()
                .filter(item -> item.getBusinessCode().equals("HOMEWORK_REVIEW")).findFirst().orElseThrow();
        assertTrue(review.getUsingDefault());
        assertTrue(review.getSameAsDefault());
        assertEquals(AiPromptDefaults.HOMEWORK_REVIEW, review.getContent());
    }

    @Test
    void historyDisplaysSeededSystemCreatorWithoutLookingUpNull() {
        AiPromptTemplateDO seeded = version(1, AiPromptDefaults.HOMEWORK_REWRITE);
        seeded.setCreator("system");
        when(mapper.selectListByBusinessCode("HOMEWORK_REWRITE")).thenReturn(List.of(seeded));

        assertEquals("系统内置", service.getHistory("HOMEWORK_REWRITE").get(0).getCreatorName());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void historyHandlesMissingUserAndResolvesNormalUserInOneBatch() {
        AiPromptTemplateDO missing = version(2, "第二版");
        missing.setCreator("10");
        AiPromptTemplateDO normal = version(3, "第三版");
        normal.setCreator("20");
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(20L);
        user.setNickname("测试教师");
        when(mapper.selectListByBusinessCode("HOMEWORK_REWRITE")).thenReturn(List.of(missing, normal));
        when(adminUserApi.getUserMap(List.of(10L, 20L))).thenReturn(Map.of(20L, user));

        var history = service.getHistory("HOMEWORK_REWRITE");

        assertEquals("用户已不可用", history.get(0).getCreatorName());
        assertEquals("测试教师", history.get(1).getCreatorName());
        verify(adminUserApi).getUserMap(List.of(10L, 20L));
    }

    @Test
    void historyHandlesUserApiFailureAlongsideSystemAndEmptyCreators() {
        AiPromptTemplateDO seeded = version(1, "内置");
        seeded.setCreator("system");
        AiPromptTemplateDO normal = version(2, "自定义");
        normal.setCreator("20");
        AiPromptTemplateDO unknown = version(3, "未知保存人");
        when(mapper.selectListByBusinessCode("HOMEWORK_REWRITE")).thenReturn(List.of(seeded, normal, unknown));
        when(adminUserApi.getUserMap(List.of(20L))).thenThrow(new IllegalStateException("用户服务不可用"));

        var history = service.getHistory("HOMEWORK_REWRITE");

        assertEquals("系统内置", history.get(0).getCreatorName());
        assertEquals("用户已不可用", history.get(1).getCreatorName());
        assertEquals("用户已不可用", history.get(2).getCreatorName());
    }

    private static AiPromptTemplateDO version(int version, String content) {
        AiPromptTemplateDO record = new AiPromptTemplateDO();
        record.setBusinessCode(version > 0 ? "HOMEWORK_REWRITE" : null);
        record.setVersion(version);
        record.setContent(content);
        record.setActive(true);
        return record;
    }

}
