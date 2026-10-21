package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduSemesterUsageDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_CURRENT_NOT_SET;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_NOT_CONFIGURED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class EduSemesterOptionServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduSemesterOptionService service;
    @Mock
    private EduSemesterService semesterService;
    @Mock
    private EduClassCourseMapper classCourseMapper;

    @Test
    void currentSemesterFirstThenHistoryNewestFirst() {
        current("2026-2027 上学期");
        when(classCourseMapper.selectSemesterUsage()).thenReturn(List.of(
                usage("2024-2025-2", 3, 0), usage("2026-2027 上学期", 8, 7), usage("2025-2026 下学期", 5, 2),
                usage("未设置", 1, 1)));

        List<EduSemesterOptionRespVO> options = service.listOptions();

        assertEquals(List.of("2026-2027 上学期", "2025-2026 下学期", "2024-2025-2"),
                options.stream().map(EduSemesterOptionRespVO::getSemesterName).toList());
        assertTrue(options.get(0).getCurrent());
        assertEquals(LocalDate.of(2026, 8, 1), options.get(0).getSemesterStartDate());
        assertEquals(7, options.get(0).getActiveClassCourseCount());
        assertFalse(options.get(1).getCurrent());
    }

    @Test
    void blankNameResolvesToCurrentSemester() {
        current("2026-2027 上学期");
        when(classCourseMapper.selectSemesterUsage()).thenReturn(List.of());

        assertEquals("2026-2027 上学期", service.resolve("  ").getSemesterName());
    }

    @Test
    void oldFreeTextNameStaysSelectableButNewTextIsRejected() {
        current("2026-2027 上学期");
        when(classCourseMapper.selectSemesterUsage()).thenReturn(List.of(usage("E2E-旧学期", 1, 1)));

        assertEquals("E2E-旧学期", service.resolve(" E2E-旧学期 ").getSemesterName());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.resolve("随手输入的学期"));
        assertEquals(SEMESTER_NOT_CONFIGURED.getCode(), ex.getCode());
    }

    @Test
    void unsetCurrentSemesterIsRejectedForBlankName() {
        current("未设置");
        when(classCourseMapper.selectSemesterUsage()).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.resolve(null));
        assertEquals(SEMESTER_CURRENT_NOT_SET.getCode(), ex.getCode());
    }

    private void current(String name) {
        EduSemesterRespVO semester = new EduSemesterRespVO();
        semester.setSemesterName(name);
        semester.setSemesterStartDate(LocalDate.of(2026, 8, 1));
        semester.setSemesterEndDate(LocalDate.of(2027, 1, 31));
        when(semesterService.getCurrentSemester()).thenReturn(semester);
    }

    private EduSemesterUsageDO usage(String name, int count, int active) {
        EduSemesterUsageDO usage = new EduSemesterUsageDO();
        usage.setSemesterName(name);
        usage.setLinkCount(count);
        usage.setActiveLinkCount(active);
        return usage;
    }
}
