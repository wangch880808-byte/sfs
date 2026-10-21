package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EduHomeworkArchiveAccessServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduHomeworkArchiveAccessService service;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;

    @BeforeEach
    void activeParents() {
        EduClassDO clazz = new EduClassDO();
        clazz.setStatus(0);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setStatus(0);
        when(courseMapper.getCourse(2L)).thenReturn(course);
    }

    @Test
    void expiredHomeworkCanBeViewedButCannotBeSubmitted() {
        EduHomeworkDO homework = homework(2, "2026-2027-1");
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setSemesterName("2026-2027-1");
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(binding));

        assertTrue(service.canView(homework));
        assertFalse(service.canSubmit(homework));
    }

    @Test
    void emptyHomeworkSemesterAcceptsDifferentBindingSemester() {
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setSemesterName("2026-2027-1");
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(binding));

        assertTrue(service.canView(homework(2, null)));
    }

    @Test
    void emptyBindingSemesterAcceptsDifferentHomeworkSemester() {
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(new EduClassCourseDO()));

        assertTrue(service.canView(homework(2, "2026-2027-1")));
    }

    @Test
    void twoDifferentNonemptySemestersDoNotMatch() {
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setSemesterName("2025-2026-2");
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(binding));

        assertFalse(service.canView(homework(2, "2026-2027-1")));
    }

    @Test
    void matchingSemestersIgnoreSurroundingSpaces() {
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setSemesterName(" 2026-2027-1 ");
        when(classCourseMapper.selectList(any())).thenReturn(java.util.List.of(binding));

        assertTrue(service.canView(homework(2, "2026-2027-1")));
    }

    private static EduHomeworkDO homework(int status, String semesterName) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setStatus(status);
        homework.setClassId(1L);
        homework.setCourseId(2L);
        homework.setSemesterName(semesterName);
        return homework;
    }
}
