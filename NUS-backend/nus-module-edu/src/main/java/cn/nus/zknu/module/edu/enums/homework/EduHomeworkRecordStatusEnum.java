package cn.nus.zknu.module.edu.enums.homework;

import java.util.List;

/**
 * 学生提交记录状态（edu_homework_record.status）。
 */
public interface EduHomeworkRecordStatusEnum {

    int NOT_SUBMITTED = 0;
    int SUBMITTED = 1;
    int CORRECTED = 2;
    int REDO = 3;
    int RESUBMITTED = 4;

    /** 等待教师批改：首次提交与打回后重交。 */
    List<Integer> PENDING_REVIEW = List.of(SUBMITTED, RESUBMITTED);
    /** 教师可以批改（含对已批改记录复评）。 */
    List<Integer> CORRECTABLE = List.of(SUBMITTED, CORRECTED, RESUBMITTED);
}
