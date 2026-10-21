package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertSame;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduAiBusinessAccessPublicCourseTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduAiBusinessAccessService service;
    @Mock
    private EduDataScopeService dataScopeService;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;

    @ParameterizedTest
    @CsvSource({"true,0", "true,1", "false,0", "false,1"})
    void aiCorrectionStillRequiresOwnerAndActiveBinding(boolean owner, int bindingStatus) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setCourseId(2L);
        homework.setClassId(3L);
        homework.setTeacherId(owner ? 7L : 99L);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(classCourseMapper.selectOne(any())).thenReturn(bindingStatus == 0 ? new EduClassCourseDO() : null);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            if (bindingStatus == 1) {
                assertServiceException(() -> service.requireHomeworkCorrectionAccess(1L), CLASS_COURSE_NOT_EXISTS);
            } else if (!owner) {
                assertServiceException(() -> service.requireHomeworkCorrectionAccess(1L), FORBIDDEN);
            } else {
                assertSame(homework, service.requireHomeworkCorrectionAccess(1L));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "false,true,false", "false,false,false", "false,true,true"})
    void publicTaskWriteRequiresAuthorOrAdminWhileReadAllStaysReadOnly(boolean owner, boolean readAll, boolean admin) {
        var task = new EduHomeworkDO(); task.setId(1L); task.setCourseId(2L); task.setClassId(3L);
        task.setTeacherId(owner ? 7L : 8L);
        when(homeworkMapper.selectById(1L)).thenReturn(task);
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        lenient().when(dataScopeService.canViewAllPublic(2L)).thenReturn(readAll);
        lenient().when(dataScopeService.isGlobalAdmin()).thenReturn(admin);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            if (owner || admin) assertSame(task, service.requireHomeworkAccess(1L));
            else assertServiceException(() -> service.requireHomeworkAccess(1L), FORBIDDEN);
            if (owner || readAll) assertSame(task, service.requireHomeworkViewAccess(1L));
            else assertServiceException(() -> service.requireHomeworkViewAccess(1L), FORBIDDEN);
            if (owner) assertSame(task, service.requireHomeworkCorrectionAccess(1L));
            else assertServiceException(() -> service.requireHomeworkCorrectionAccess(1L), FORBIDDEN);
        }
        verifyNoInteractions(classCourseMapper);
        verify(dataScopeService, never()).canAssignToPublicClass(anyLong(), anyLong());
        verify(dataScopeService, never()).validateClassAccess(anyLong());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void publicNewTaskRequiresClaimOrAdmin(boolean canAssign) {
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(dataScopeService.canAssignToPublicClass(2L, 3L)).thenReturn(canAssign);
        if (canAssign) {
            when(classCourseMapper.selectOne(any())).thenReturn(new EduClassCourseDO());
            service.validateCourseClassAccess(2L, 3L);
        } else assertServiceException(() -> service.validateCourseClassAccess(2L, 3L), FORBIDDEN);
        verify(dataScopeService, never()).validateClassAccess(anyLong());
    }

    @Test
    void publicAnalysisUsesViewingInsteadOfAssignment() {
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(classCourseMapper.selectOne(any())).thenReturn(new EduClassCourseDO());
        service.validateCourseClassViewAccess(2L, 3L);
        verify(dataScopeService).validateClassCourseAccess(3L, 2L);
        verify(dataScopeService, never()).canAssignToPublicClass(anyLong(), anyLong());
        verify(classCourseMapper).selectOne(any());
    }

    @Test
    void unclaimedTeacherCannotViewOrCorrectAnotherPublicTask() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setCourseId(2L);
        homework.setClassId(3L);
        homework.setTeacherId(99L);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> service.requireHomeworkAccess(1L), FORBIDDEN);
            assertServiceException(() -> service.requireHomeworkCorrectionAccess(1L), FORBIDDEN);
        }
        verify(dataScopeService, never()).validateClassAccess(3L);
    }
}
