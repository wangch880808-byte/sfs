package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionSetReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionStudentRespVO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 任务个别延期：教师为未提交的学生单独延后截止时间，学生的实际截止时间取任务截止时间与个人截止时间中较晚的一个。
 */
public interface EduHomeworkExtensionService {

    /** 任务的全部个别延期（仅任务的发布教师） */
    List<EduHomeworkExtensionRespVO> listExtensions(Long homeworkId);

    /** 任务的学生名单及提交状态、已有延期，供教师选择延期对象（仅任务的发布教师） */
    List<EduHomeworkExtensionStudentRespVO> listStudents(Long homeworkId);

    /**
     * 为一批学生设置（或修改）个别延期，并通知学生。
     *
     * @return 设置的学生人数
     */
    int setExtensions(EduHomeworkExtensionSetReqVO reqVO);

    /** 移除一条个别延期，学生恢复按任务截止时间提交 */
    void deleteExtension(Long id);

    /** 学生在任务上的个人截止时间，没有延期时为空 */
    LocalDateTime getPersonalDeadline(Long homeworkId, Long studentId);

    /** 学生在多个任务上的个人截止时间，key 为任务 ID */
    Map<Long, LocalDateTime> getPersonalDeadlineMap(Long studentId, Collection<Long> homeworkIds);

}
