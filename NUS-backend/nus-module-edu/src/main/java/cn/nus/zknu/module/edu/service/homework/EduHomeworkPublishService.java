package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/** 发布任务的学生快照与创建、修改通知；沿用调用方事务。 */
@Service
public class EduHomeworkPublishService {

    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassStudentMapper eduClassStudentMapper;
    @Resource
    private EduHomeworkAssigneeMapper eduHomeworkAssigneeMapper;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduHomeworkChangeNotifier homeworkChangeNotifier;

    public void snapshotAssigneesIfPublished(EduHomeworkDO homework) {
        if (!Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)
                || eduHomeworkAssigneeMapper.selectCount(EduHomeworkAssigneeDO::getHomeworkId, homework.getId()) > 0) {
            return;
        }
        LocalDateTime assignedTime = LocalDateTime.now();
        List<EduHomeworkAssigneeDO> assignees = eduClassStudentMapper.selectListByClassId(homework.getClassId()).stream()
                .map(relation -> {
                    EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
                    assignee.setHomeworkId(homework.getId());
                    assignee.setStudentId(relation.getStudentId());
                    assignee.setClassId(homework.getClassId());
                    assignee.setAssignedTime(assignedTime);
                    return assignee;
                })
                .toList();
        if (!assignees.isEmpty()) {
            eduHomeworkAssigneeMapper.insertBatch(assignees);
        }
    }

    public void notifyPublishedHomework(EduHomeworkDO homework, String templateCode) {
        if (homework == null || !Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)) {
            return;
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(homework.getCourseId());
        if (course == null) {
            return;
        }
        List<Long> studentIds = eduHomeworkAssigneeMapper.selectListByHomeworkId(homework.getId()).stream()
                .map(EduHomeworkAssigneeDO::getStudentId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("courseName", course.getCourseName());
        params.put("homeworkName", homework.getHomeworkName());
        params.put("deadline", homework.getDeadline() == null ? "未设置"
                : homework.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        businessNotifyService.sendToUsers(studentIds, templateCode, params,
                "/student/homework/submit/" + homework.getId());
    }

    public void notifyUpdatedHomework(EduHomeworkDO current, EduHomeworkDO updated) {
        if (updated != null && Objects.equals(updated.getStatus(), EduHomeworkStatusEnum.PUBLISHED)) {
            if (Objects.equals(current.getStatus(), EduHomeworkStatusEnum.DRAFT)) {
                notifyPublishedHomework(updated, EduBusinessNotifyService.HOMEWORK_CREATED);
            } else {
                // 已发布任务只在关键字段改变时通知，并写明改了什么
                homeworkChangeNotifier.notifyKeyChanges(current, updated);
            }
        }
    }

}
