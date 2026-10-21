package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_REDO_SCOPE_ARCHIVED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.MESSAGE_SCOPE_ARCHIVED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 归档后历史可见（只读）与“打回、回复留言”在归档范围内被拦下。 */
class EduHomeworkHistoryVisibilityTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduHomeworkArchiveAccessService service;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;

    @Test
    void historyVisibilityIgnoresArchivedParentsButNotDrafts() {
        assertTrue(service.canViewHistory(homework(1), false));
        assertTrue(service.canViewHistory(homework(2), false));
        assertFalse(service.canViewHistory(homework(0), true), "草稿不给学生看");
        assertFalse(service.canViewHistory(null, true));
        verifyNoInteractions(classMapper, courseMapper, classCourseMapper);
    }

    @Test
    void archivedTaskIsVisibleOnlyWhenTheStudentSubmitted() {
        assertTrue(service.canViewHistory(homework(3), true));
        assertFalse(service.canViewHistory(homework(3), false));
    }

    @Test
    void archivedClassStillAllowsHistoryButNotCurrentViewOrSubmit() {
        parents(1, 0, 0);
        EduHomeworkDO homework = homework(1);

        assertFalse(service.canView(homework));
        assertFalse(service.canSubmit(homework));
        assertTrue(service.canViewHistory(homework, false));
    }

    @Test
    void redoIsBlockedWhenTheClassIsArchived() {
        when(homeworkMapper.selectById(9L)).thenReturn(homework(1));
        parents(1, 0, 0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.validateRedoScopeActive(9L));

        assertEquals(HOMEWORK_REDO_SCOPE_ARCHIVED.getCode(), ex.getCode());
        assertEquals("没有打回：任务所在的班级已封存，封存后的成绩只能查看。如需打回，请先恢复班级", ex.getMessage());
    }

    @Test
    void redoIsBlockedWhenTheClassCourseIsArchived() {
        when(homeworkMapper.selectById(9L)).thenReturn(homework(1));
        parents(0, 0, 1);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.validateRedoScopeActive(9L));

        assertTrue(ex.getMessage().contains("班课已封存"));
    }

    @Test
    void redoIsAllowedWhileEverythingIsActive() {
        when(homeworkMapper.selectById(9L)).thenReturn(homework(1));
        parents(0, 0, 0);

        assertDoesNotThrow(() -> service.validateRedoScopeActive(9L));
    }

    @Test
    void replyIsBlockedWhenTheCourseIsArchived() {
        parents(0, 1, 0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateMessageScopeActive(1L, 2L));

        assertEquals(MESSAGE_SCOPE_ARCHIVED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("课程已封存"));
    }

    @Test
    void replyIsAllowedWhileEverythingIsActive() {
        parents(0, 0, 0);

        assertDoesNotThrow(() -> service.validateMessageScopeActive(1L, 2L));
    }

    private void parents(int classStatus, int courseStatus, int bindingStatus) {
        EduClassDO clazz = new EduClassDO();
        clazz.setStatus(classStatus);
        lenient().when(classMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setStatus(courseStatus);
        lenient().when(courseMapper.getCourse(2L)).thenReturn(course);
        // 查询条件里已限定 status = 0：班课归档时查不到在用的绑定
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setStatus(0);
        lenient().when(classCourseMapper.selectList(any()))
                .thenReturn(bindingStatus == 0 ? List.of(binding) : List.of());
    }

    private static EduHomeworkDO homework(int status) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(9L);
        homework.setStatus(status);
        homework.setClassId(1L);
        homework.setCourseId(2L);
        return homework;
    }
}
