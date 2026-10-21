package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 已发布任务保存后的变更通知：只有截止时间、任务内容、附件要求、提交方式、满分或及格线改变时才通知学生，
 * 并在通知里写明改了什么。
 */
@Service
public class EduHomeworkChangeNotifier {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduBusinessNotifyService businessNotifyService;

    /**
     * @param before 保存前的任务
     * @param after  保存后的任务
     * @return 通知的学生人数；没有关键变更或任务不在发布状态时为 0
     */
    public int notifyKeyChanges(EduHomeworkDO before, EduHomeworkDO after) {
        if (after == null || !Objects.equals(after.getStatus(), EduHomeworkStatusEnum.PUBLISHED)) {
            return 0;
        }
        List<String> changes = HomeworkChangeDetector.describeKeyChanges(before, after);
        if (changes.isEmpty()) {
            return 0;
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(after.getCourseId());
        if (course == null) {
            return 0;
        }
        List<Long> studentIds = assigneeMapper.selectListByHomeworkId(after.getId()).stream()
                .map(EduHomeworkAssigneeDO::getStudentId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("courseName", course.getCourseName());
        params.put("homeworkName", after.getHomeworkName());
        params.put("changes", HomeworkChangeDetector.join(changes));
        params.put("deadline", after.getDeadline() == null ? "未设置" : after.getDeadline().format(TIME_FORMAT));
        return businessNotifyService.sendToUsers(studentIds, EduBusinessNotifyService.HOMEWORK_UPDATED, params,
                "/student/homework/submit/" + after.getId());
    }

}
