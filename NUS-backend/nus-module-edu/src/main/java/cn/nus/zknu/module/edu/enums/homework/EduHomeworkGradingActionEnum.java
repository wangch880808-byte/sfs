package cn.nus.zknu.module.edu.enums.homework;

/**
 * 批改操作（edu_homework_grading_action）：可撤销的批改、打回，以及延后发送的学生通知。
 */
public interface EduHomeworkGradingActionEnum {

    String ACTION_CORRECT = "CORRECT";
    String ACTION_REDO = "REDO";
    /** 撤销写入成绩轨迹的事件类型。 */
    String HISTORY_UNDO = "UNDO";

    /** 通知等待撤销期结束后发送。 */
    int NOTIFY_PENDING = 0;
    int NOTIFY_SENT = 1;
    /** 教师在撤销期内撤销了操作，通知不再发送。 */
    int NOTIFY_UNDONE = 2;
    int NOTIFY_FAILED = 3;
}
