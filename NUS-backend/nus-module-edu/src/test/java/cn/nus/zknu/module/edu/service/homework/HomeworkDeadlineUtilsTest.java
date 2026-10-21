package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HomeworkDeadlineUtilsTest {

    private static final LocalDateTime TASK = LocalDateTime.of(2026, 10, 10, 18, 0);

    @Test
    void withPersonalDeadline_takesTheLaterOne() {
        assertEquals(TASK.plusDays(2), HomeworkDeadlineUtils.withPersonalDeadline(TASK, TASK.plusDays(2)));
        assertEquals(TASK, HomeworkDeadlineUtils.withPersonalDeadline(TASK, TASK.minusDays(1)));
        assertEquals(TASK, HomeworkDeadlineUtils.withPersonalDeadline(TASK, TASK));
        assertEquals(TASK, HomeworkDeadlineUtils.withPersonalDeadline(TASK, null));
    }

    @Test
    void withPersonalDeadline_taskWithoutDeadlineStaysUnlimited() {
        assertNull(HomeworkDeadlineUtils.withPersonalDeadline(null, TASK.plusDays(2)));
    }

    @Test
    void activePersonalDeadline_onlyWhenLaterThanTaskDeadline() {
        assertEquals(TASK.plusHours(1), HomeworkDeadlineUtils.getActivePersonalDeadline(TASK, TASK.plusHours(1)));
        assertNull(HomeworkDeadlineUtils.getActivePersonalDeadline(TASK, TASK));
        assertNull(HomeworkDeadlineUtils.getActivePersonalDeadline(TASK, TASK.minusHours(1)));
        assertNull(HomeworkDeadlineUtils.getActivePersonalDeadline(null, TASK));
        assertNull(HomeworkDeadlineUtils.getActivePersonalDeadline(TASK, null));
    }

    @Test
    void studentEffectiveDeadline_unsubmittedUsesExtension() {
        assertEquals(TASK.plusDays(3),
                HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), null, TASK.plusDays(3)));
        assertEquals(TASK, HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), null, null));
    }

    @Test
    void studentEffectiveDeadline_redoRulesStayAsBefore() {
        EduHomeworkRecordDO redoWithDeadline = record(3, TASK.plusDays(1));
        assertEquals(TASK.plusDays(1),
                HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), redoWithDeadline, TASK.plusDays(5)));
        // 已打回但没设重做截止时间：沿用任务截止时间，个别延期不改变重做规则
        EduHomeworkRecordDO redoWithoutDeadline = record(3, null);
        assertEquals(TASK,
                HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), redoWithoutDeadline, TASK.plusDays(5)));
        EduHomeworkRecordDO resubmitted = record(4, TASK.plusDays(1));
        assertEquals(TASK.plusDays(1),
                HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), resubmitted, TASK.plusDays(5)));
    }

    @Test
    void studentEffectiveDeadline_submittedWithoutRedoKeepsExtension() {
        assertEquals(TASK.plusDays(2),
                HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), record(1, null), TASK.plusDays(2)));
    }

    @Test
    void studentEffectiveDeadline_columnOverloadMatchesTheRecordVersion() {
        LocalDateTime personal = TASK.plusDays(5);
        EduHomeworkRecordDO[] records = {null, record(1, null), record(2, null), record(3, null),
                record(3, TASK.plusDays(1)), record(4, TASK.plusDays(1)), record(4, null)};
        for (EduHomeworkRecordDO record : records) {
            for (LocalDateTime extension : new LocalDateTime[]{null, personal, TASK.minusDays(1)}) {
                assertEquals(HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework(TASK), record, extension),
                        HomeworkDeadlineUtils.getStudentEffectiveDeadline(TASK,
                                record == null ? null : record.getStatus(),
                                record == null ? null : record.getRedoDeadline(), extension));
            }
        }
        assertNull(HomeworkDeadlineUtils.getStudentEffectiveDeadline(null, null, null, personal));
    }

    private static EduHomeworkDO homework(LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setDeadline(deadline);
        return homework;
    }

    private static EduHomeworkRecordDO record(int status, LocalDateTime redoDeadline) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setStatus(status);
        record.setRedoDeadline(redoDeadline);
        return record;
    }

}
