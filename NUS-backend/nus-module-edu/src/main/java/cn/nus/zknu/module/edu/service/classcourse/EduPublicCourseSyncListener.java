package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.module.capacity.api.course.PublicCourseChangedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 课程事务提交后再读取课程状态，同步失败交由每日任务兜底。 */
@Component
@Slf4j
public class EduPublicCourseSyncListener {

    @Resource
    private EduPublicCourseSyncScheduler syncScheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCourseChanged(PublicCourseChangedEvent event) {
        try {
            syncScheduler.submitCourse(event.courseId());
        } catch (Exception ex) {
            log.error("[onCourseChanged][公共课同步失败 courseId={}]", event.courseId(), ex);
        }
    }
}
