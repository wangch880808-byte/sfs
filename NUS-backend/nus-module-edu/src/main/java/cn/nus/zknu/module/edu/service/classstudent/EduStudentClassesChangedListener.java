package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.module.system.api.user.StudentClassesChangedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** 用户管理与教务入口共用任务快照逻辑，任何同步异常都交由原事务回滚。 */
@Component
public class EduStudentClassesChangedListener {

    @Resource
    private EduClassStudentService classStudentService;

    @EventListener
    public void onStudentClassesChanged(StudentClassesChangedEvent event) {
        List<Long> studentIds = List.of(event.studentId());
        for (Long classId : event.removedClassIds()) {
            classStudentService.syncAssigneesForMembershipChange(classId, studentIds, false);
        }
        for (Long classId : event.addedClassIds()) {
            classStudentService.syncAssigneesForMembershipChange(classId, studentIds, true);
        }
    }
}
