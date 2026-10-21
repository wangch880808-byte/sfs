package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkCreateAndDraftPublishTest extends BaseMockitoUnitTest {
    @InjectMocks private EduHomeworkServiceImpl service;
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkRecordMapper recordMapper;
    @Mock private EduClassCourseMapper relationMapper;
    @Mock private EduClassMapper classMapper;
    @Mock private CapacityCourseApi courseApi;
    @Mock private EduAiBusinessAccessService accessService;
    @Mock private EduDataScopeService dataScopeService;
    @Mock private EduHomeworkCreationSupport creationSupport;
    @Mock private EduHomeworkPublishService publishService;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void login() {
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
    }
    @AfterEach void logout() { security.close(); }

    @Test void createRejectsDraftBeforeAnyWrite() {
        var request = request(); request.setStatus(0);
        assertServiceException(() -> service.createHomework(request), HOMEWORK_DRAFT_FORBIDDEN);
        verifyNoInteractions(homeworkMapper, relationMapper, publishService);
    }

    @Test void creationSupportStillDefaultsToPublished() {
        var homework = new EduHomeworkDO(); homework.setPassScore(60);
        new EduHomeworkCreationSupport().fillHomeworkDefaults(homework);
        assertEquals(1, homework.getStatus());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {1})
    void createWithSourceUsesSelectedRelationAndPublishes(Integer status) {
        var request = request(); request.setStatus(status); request.setClassCourseId(11L); request.setSourceHomeworkId(90L);
        var older = relation(11L, 0, "2026-2027 上学期");
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(12L, 0, "2026-2027 下学期"), older));
        var source = new EduHomeworkDO(); source.setStatus(3);
        when(accessService.requireHomeworkViewAccess(90L)).thenReturn(source);
        when(creationSupport.generateUniqueName(3L, 2L, "任务")).thenReturn("任务(1)");
        doAnswer(call -> { call.<EduHomeworkDO>getArgument(0).setStatus(1); return null; })
                .when(creationSupport).fillHomeworkDefaults(any());
        doAnswer(call -> { call.<EduHomeworkDO>getArgument(0).setId(100L); return 1; })
                .when(homeworkMapper).insert(any(EduHomeworkDO.class));
        assertEquals(100L, service.createHomework(request));
        var capture = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper).insert(capture.capture());
        var created = capture.getValue();
        assertEquals(90L, created.getSourceHomeworkId());
        assertEquals("2026-2027 上学期", created.getSemesterName());
        assertEquals(7L, created.getTeacherId());
        assertEquals(1, created.getStatus());
        verify(accessService).requireHomeworkViewAccess(90L);
        verify(relationMapper, times(1)).selectList(any());
        verify(relationMapper, never()).selectById(any());
        verify(publishService).snapshotAssigneesIfPublished(created);
        verify(publishService).notifyPublishedHomework(created, EduBusinessNotifyService.HOMEWORK_CREATED);
    }

    @Test void invalidRelationIdStatusOrSemesterIsRejected() {
        var request = request(); request.setClassCourseId(11L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(12L, 0, "term")));
        assertServiceException(() -> service.createHomework(request), HOMEWORK_CLASS_COURSE_MISMATCH);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 1, "term")));
        assertServiceException(() -> service.createHomework(request), HOMEWORK_CLASS_COURSE_MISMATCH);
        request.setSemesterName("another");
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 0, "term")));
        assertServiceException(() -> service.createHomework(request), HOMEWORK_CLASS_COURSE_MISMATCH);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 0, "")));
        assertServiceException(() -> service.createHomework(request), HOMEWORK_CLASS_COURSE_MISMATCH);
        request.setSemesterName(null);
        when(relationMapper.selectList(any())).thenReturn(List.of());
        assertServiceException(() -> service.createHomework(request), HOMEWORK_CLASS_COURSE_MISMATCH);
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
    }

    @Test void sourceWithoutViewAccessIsForbidden() {
        var request = request(); request.setSourceHomeworkId(90L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 0, "term")));
        doThrow(exception(FORBIDDEN)).when(accessService).requireHomeworkViewAccess(90L);
        assertServiceException(() -> service.createHomework(request), FORBIDDEN);
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
        verifyNoInteractions(publishService);
    }

    @Test void publicAssignmentPermissionStillAppliesToSelectedRelation() {
        var request = request(); request.setClassCourseId(11L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 0, "term")));
        doThrow(exception(FORBIDDEN)).when(accessService).validateCourseClassAccess(2L, 3L);
        assertServiceException(() -> service.createHomework(request), FORBIDDEN);
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 1, 2, 3})
    void oldDraftAlwaysPublishesAndMayChangeScope(Integer requestedStatus) {
        var request = request(); request.setId(1L); request.setStatus(requestedStatus);
        var draft = new EduHomeworkDO(); draft.setId(1L); draft.setTeacherId(7L); draft.setStatus(0);
        draft.setClassId(99L); draft.setCourseId(98L);
        var published = new EduHomeworkDO(); published.setId(1L); published.setStatus(1);
        when(homeworkMapper.selectById(1L)).thenReturn(draft, published);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(11L, 0, "term")));
        service.updateHomework(request);
        var capture = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper).updateById(capture.capture());
        assertEquals(1, capture.getValue().getStatus());
        assertEquals(3L, capture.getValue().getClassId());
        verify(publishService).snapshotAssigneesIfPublished(capture.getValue());
        verify(publishService).notifyUpdatedHomework(draft, published);
    }

    @Test void oldDraftRequiresDeadlineBeforeWrite() {
        var draft = new EduHomeworkDO(); draft.setId(1L); draft.setTeacherId(7L); draft.setStatus(0);
        when(homeworkMapper.selectById(1L)).thenReturn(draft);
        var request = request(); request.setId(1L); request.setDeadline(null);
        assertServiceException(() -> service.updateHomework(request), HOMEWORK_DEADLINE_REQUIRED);
        verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
        verifyNoInteractions(publishService);
    }

    private EduHomeworkSaveReqVO request() {
        var request = new EduHomeworkSaveReqVO(); request.setCourseId(2L); request.setClassId(3L);
        request.setHomeworkName("任务"); request.setScore(100); request.setPassScore(60);
        request.setDeadline(LocalDateTime.now().plusDays(1)); return request;
    }
    private EduClassCourseDO relation(long id, int status, String term) {
        var relation = new EduClassCourseDO(); relation.setId(id); relation.setCourseId(2L);
        relation.setClassId(3L); relation.setStatus(status); relation.setSemesterName(term); return relation;
    }
}
