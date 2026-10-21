package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_STATUS_DRAFT_FORBIDDEN;
import static org.mockito.Mockito.*;

class EduHomeworkArchiveServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private EduHomeworkPublishService publishService;
    @Mock
    private EduHomeworkAiCreateService aiCreateService;
    @Mock
    private EduHomeworkStatisticsService statisticsService;

    @Mock
    private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;

    @InjectMocks
    private EduHomeworkServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkRecordService recordService;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduAiBusinessAccessService accessService;
    @Mock
    private cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock
    private EduHomeworkChangeLogService homeworkChangeLogService;

    @BeforeEach
    void setUpCollaborators() {
        EduHomeworkCreationSupport creationSupport = new EduHomeworkCreationSupport();
        ReflectionTestUtils.setField(creationSupport, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(service, "creationSupport", creationSupport);
    }

    @Test
    void expiredHomeworkAcceptsUnchangedStatusOnUpdate() {
        EduHomeworkDO current = expiredHomework();
        when(homeworkMapper.selectById(1L)).thenReturn(current);
        when(courseMapper.getCourse(2L)).thenReturn(new CapacityCourseRespDTO());
        when(classMapper.selectById(3L)).thenReturn(new EduClassDO());
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setStatus(0);
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(binding));
        EduHomeworkSaveReqVO request = updateRequest(2);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            service.updateHomework(request);
        }

        verify(homeworkMapper).updateById(argThat((EduHomeworkDO item) -> item.getId().equals(1L)
                && item.getStatus().equals(2)));
    }

    @Test
    void expiredHomeworkCannotBecomeDraft() {
        when(homeworkMapper.selectById(1L)).thenReturn(expiredHomework());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            assertServiceException(() -> service.updateHomework(updateRequest(0)), HOMEWORK_STATUS_DRAFT_FORBIDDEN);
        }
        verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
    }

    private static EduHomeworkDO expiredHomework() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setTeacherId(10L);
        homework.setStatus(2);
        homework.setCourseId(2L);
        homework.setClassId(3L);
        return homework;
    }

    private static EduHomeworkSaveReqVO updateRequest(int status) {
        EduHomeworkSaveReqVO request = new EduHomeworkSaveReqVO();
        request.setId(1L);
        request.setStatus(status);
        request.setCourseId(2L);
        request.setClassId(3L);
        request.setPassScore(60);
        request.setScore(100);
        return request;
    }

    @Test
    void draftRestoresToDraftAndKeepsRecords() {
        EduHomeworkDO draft = new EduHomeworkDO();
        draft.setId(1L);
        draft.setTeacherId(10L);
        draft.setStatus(0);
        EduHomeworkDO archived = new EduHomeworkDO();
        archived.setId(1L);
        archived.setTeacherId(10L);
        archived.setStatus(3);
        archived.setStatusBeforeArchive(0);
        when(homeworkMapper.selectById(1L)).thenReturn(draft, archived);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            service.deleteHomework(1L);
            service.restoreHomework(1L);
        }

        verify(homeworkMapper).updateById(argThat((EduHomeworkDO item) -> item.getId().equals(1L)
                && item.getStatus().equals(3) && item.getStatusBeforeArchive().equals(0)));
        verify(homeworkMapper).updateById(argThat((EduHomeworkDO item) -> item.getId().equals(1L)
                && item.getStatus().equals(0)));
        verify(recordService, never()).deleteByHomeworkId(1L);
        verify(homeworkMapper, never()).deleteById(1L);
    }
}
