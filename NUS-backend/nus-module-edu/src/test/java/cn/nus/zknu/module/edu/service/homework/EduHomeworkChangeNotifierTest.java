package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EduHomeworkChangeNotifierTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduHomeworkChangeNotifier notifier;
    @Mock
    private CapacityCourseApi capacityCourseMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduBusinessNotifyService businessNotifyService;

    @Test
    void keyChangeNotifiesAssigneesWithWhatChanged() {
        EduHomeworkDO before = homework();
        EduHomeworkDO after = homework();
        after.setDeadline(LocalDateTime.of(2026, 10, 12, 18, 0));
        after.setPassScore(70);
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setCourseName("普通话");
        when(capacityCourseMapper.getCourse(2L)).thenReturn(course);
        EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
        assignee.setStudentId(10L);
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(List.of(assignee));
        when(businessNotifyService.sendToUsers(eq(List.of(10L)), eq(EduBusinessNotifyService.HOMEWORK_UPDATED),
                eq(Map.of("courseName", "普通话", "homeworkName", "朗读",
                        "changes", "截止时间改为 2026-10-12 18:00；及格线改为 70 分",
                        "deadline", "2026-10-12 18:00")),
                eq("/student/homework/submit/1"))).thenReturn(1);

        assertEquals(1, notifier.notifyKeyChanges(before, after));
    }

    @Test
    void nonKeyChangeSendsNothing() {
        EduHomeworkDO after = homework();
        after.setHomeworkName("新名字");
        after.setAiRubricJson("{}");

        assertEquals(0, notifier.notifyKeyChanges(homework(), after));
        verifyNoInteractions(businessNotifyService, assigneeMapper, capacityCourseMapper);
    }

    @Test
    void unpublishedTaskSendsNothing() {
        EduHomeworkDO after = homework();
        after.setStatus(2);
        after.setScore(90);

        assertEquals(0, notifier.notifyKeyChanges(homework(), after));
        verifyNoInteractions(businessNotifyService);
    }

    private static EduHomeworkDO homework() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setCourseId(2L);
        homework.setStatus(1);
        homework.setHomeworkName("朗读");
        homework.setDeadline(LocalDateTime.of(2026, 10, 10, 18, 0));
        homework.setRequirement("<p>朗读</p>");
        homework.setSubmissionMode("audio");
        homework.setRequiredAttachmentTypesJson("[\"audio\"]");
        homework.setScore(100);
        homework.setPassScore(60);
        return homework;
    }

}
