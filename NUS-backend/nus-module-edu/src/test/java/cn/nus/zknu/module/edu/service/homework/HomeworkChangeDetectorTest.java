package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeworkChangeDetectorTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 10, 10, 18, 0);

    @Test
    void unchangedKeyFieldsProduceNoNotification() {
        EduHomeworkDO before = homework();
        EduHomeworkDO after = homework();
        after.setHomeworkName("改了名字");
        after.setAiRubricJson("{\"a\":1}");
        after.setRequirement("  <p>朗读第一课</p>\n");
        after.setRequiredAttachmentTypesJson("[\"audio\", \"image\"]");
        after.setDeadline(DEADLINE.plusSeconds(30));

        assertTrue(HomeworkChangeDetector.describeKeyChanges(before, after).isEmpty());
    }

    @Test
    void everyKeyFieldIsDescribedInFixedOrder() {
        EduHomeworkDO before = homework();
        EduHomeworkDO after = homework();
        after.setDeadline(DEADLINE.plusDays(2));
        after.setRequirement("<p>朗读第二课</p>");
        after.setRequiredAttachmentTypesJson("[\"video\"]");
        after.setSubmissionMode("video");
        after.setScore(90);
        after.setPassScore(54);

        List<String> changes = HomeworkChangeDetector.describeKeyChanges(before, after);

        assertEquals(List.of("截止时间改为 2026-10-12 18:00", "任务内容已修改", "附件要求已修改",
                "提交方式改为视频", "满分改为 90 分", "及格线改为 54 分"), changes);
        assertEquals("截止时间改为 2026-10-12 18:00；任务内容已修改；附件要求已修改；提交方式改为视频；满分改为 90 分；及格线改为 54 分",
                HomeworkChangeDetector.join(changes));
    }

    @Test
    void removingDeadlineIsDescribed() {
        EduHomeworkDO after = homework();
        after.setDeadline(null);

        assertEquals(List.of("取消了截止时间"), HomeworkChangeDetector.describeKeyChanges(homework(), after));
    }

    @Test
    void addingDeadlineToTaskWithoutOneIsDescribed() {
        EduHomeworkDO before = homework();
        before.setDeadline(null);

        assertEquals(List.of("截止时间改为 2026-10-10 18:00"),
                HomeworkChangeDetector.describeKeyChanges(before, homework()));
    }

    @Test
    void nullInputsAreSafe() {
        assertTrue(HomeworkChangeDetector.describeKeyChanges(null, homework()).isEmpty());
        assertTrue(HomeworkChangeDetector.describeKeyChanges(homework(), null).isEmpty());
    }

    private static EduHomeworkDO homework() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setHomeworkName("朗读");
        homework.setDeadline(DEADLINE);
        homework.setRequirement("<p>朗读第一课</p>");
        homework.setRequiredAttachmentTypesJson("[\"audio\",\"image\"]");
        homework.setSubmissionMode("mixed");
        homework.setScore(100);
        homework.setPassScore(60);
        return homework;
    }

}
