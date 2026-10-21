package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemindRespVO;

/**
 * 任务提醒：截止前自动提醒、教师催交、教师新提交汇总。
 */
public interface EduHomeworkRemindService {

    /** 教师催交：立即通知还没提交、也还没过实际截止时间的学生，同一任务按配置限频 */
    EduHomeworkRemindRespVO remindUnsubmitted(Long homeworkId);

    /** 定时任务：实际截止时间在配置小时数内、还没提交的学生，每人每个任务只提醒一次 */
    String remindBeforeDeadline();

    /** 定时任务：按任务汇总新的待批改提交，通知发布教师；同一提交不会重复计数 */
    String sendTeacherDigests();

}
