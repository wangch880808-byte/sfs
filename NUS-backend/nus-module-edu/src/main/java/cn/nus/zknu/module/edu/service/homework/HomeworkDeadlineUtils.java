package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务截止时间工具类：打回重做的记录可以单独设置重做截止时间；未提交的学生可以有个别延期。
 */
public final class HomeworkDeadlineUtils {

    private HomeworkDeadlineUtils() {
    }

    /**
     * 获得有效截止时间：记录设置了重做截止时间时以其为准，否则沿用任务截止时间。
     *
     * @param homework 任务
     * @param record   提交记录，允许为空
     * @return 有效截止时间，为空表示不限
     */
    public static LocalDateTime getEffectiveDeadline(EduHomeworkDO homework, EduHomeworkRecordDO record) {
        if (record != null && record.getRedoDeadline() != null) {
            return record.getRedoDeadline();
        }
        return homework != null ? homework.getDeadline() : null;
    }

    /**
     * 任务截止时间与个人延期取较晚的一个；任务没有截止时间时不限，返回空。
     */
    public static LocalDateTime withPersonalDeadline(LocalDateTime homeworkDeadline, LocalDateTime personalDeadline) {
        if (homeworkDeadline == null) {
            return null;
        }
        return personalDeadline != null && personalDeadline.isAfter(homeworkDeadline)
                ? personalDeadline : homeworkDeadline;
    }

    /**
     * 起作用的个人延期：晚于任务截止时间时返回个人截止时间，否则返回空（任务截止时间已经更晚或不限）。
     */
    public static LocalDateTime getActivePersonalDeadline(LocalDateTime homeworkDeadline, LocalDateTime personalDeadline) {
        return homeworkDeadline != null && personalDeadline != null && personalDeadline.isAfter(homeworkDeadline)
                ? personalDeadline : null;
    }

    /**
     * 学生的实际截止时间：已打回或设置过重做截止时间的记录沿用重做规则（{@link #getEffectiveDeadline}）；
     * 其余情况取任务截止时间与个人延期中较晚的一个。
     *
     * @param homework         任务
     * @param record           提交记录，允许为空
     * @param personalDeadline 个人延期截止时间，允许为空
     * @return 实际截止时间，为空表示不限
     */
    public static LocalDateTime getStudentEffectiveDeadline(EduHomeworkDO homework, EduHomeworkRecordDO record,
                                                            LocalDateTime personalDeadline) {
        return getStudentEffectiveDeadline(homework != null ? homework.getDeadline() : null,
                record != null ? record.getStatus() : null,
                record != null ? record.getRedoDeadline() : null,
                personalDeadline);
    }

    /**
     * 同 {@link #getStudentEffectiveDeadline(EduHomeworkDO, EduHomeworkRecordDO, LocalDateTime)}，
     * 供只查出了几列的统计场景使用（如等级闯关）。
     *
     * @param homeworkDeadline 任务截止时间，允许为空
     * @param recordStatus     提交状态，没有提交记录时为空
     * @param redoDeadline     记录的重做截止时间，允许为空
     * @param personalDeadline 个人延期截止时间，允许为空
     * @return 实际截止时间，为空表示不限
     */
    public static LocalDateTime getStudentEffectiveDeadline(LocalDateTime homeworkDeadline, Integer recordStatus,
                                                            LocalDateTime redoDeadline,
                                                            LocalDateTime personalDeadline) {
        if (redoDeadline != null) {
            return redoDeadline;
        }
        if (Objects.equals(recordStatus, EduHomeworkRecordStatusEnum.REDO)) {
            return homeworkDeadline;
        }
        return withPersonalDeadline(homeworkDeadline, personalDeadline);
    }

    /**
     * 判断截止时间是否已过，未设置截止时间视为不限。
     */
    public static boolean isExpired(LocalDateTime deadline) {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

}
