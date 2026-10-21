package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动 AI 初评失败时通知任务的布置教师。同一任务在 edu.ai-review.fail-notify-hours 内最多通知一次，
 * 去重用 Redis 的 SET NX EX，多实例下同样只发一条。
 */
@Component
@Slf4j
public class HomeworkAiReviewFailureNotifier {

    static final String DEDUP_KEY_PREFIX = "edu:ai-review:fail-notify:";
    static final int MAX_REASON_LENGTH = 80;

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkAiReviewMapper reviewMapper;
    @Resource
    private EduBusinessNotifyService notifyService;
    @Resource
    private EduAiReviewPolicy policy;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @param homeworkId 失败初评所属任务
     * @param cause      失败原因（一句话，不含“下一步”）
     * @return 是否实际发出了通知
     */
    public boolean notifyFailure(Long homeworkId, String cause) {
        if (homeworkId == null) {
            return false;
        }
        try {
            EduHomeworkDO homework = homeworkMapper.selectById(homeworkId);
            if (homework == null || homework.getTeacherId() == null) {
                return false;
            }
            if (!acquireWindow(homeworkId)) {
                return false;
            }
            long failCount = Math.max(1, reviewMapper.countLatestFailedByHomeworkId(homeworkId));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("homeworkId", homeworkId);
            params.put("homeworkName", StrUtil.blankToDefault(homework.getHomeworkName(), "未命名任务"));
            params.put("failCount", failCount);
            params.put("reason", HomeworkAiReviewFailures.truncate(StrUtil.blankToDefault(cause, "原因未知"), MAX_REASON_LENGTH));
            return notifyService.sendToUser(homework.getTeacherId(),
                    EduBusinessNotifyService.TEACHER_HOMEWORK_AI_REVIEW_FAILED, params,
                    "/capacity/homework_manager/homework/correct/" + homeworkId);
        } catch (Exception ex) {
            log.error("[notifyFailure][AI 初评失败通知没有发出 homeworkId={}]", homeworkId, ex);
            return false;
        }
    }

    private boolean acquireWindow(Long homeworkId) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(DEDUP_KEY_PREFIX + homeworkId,
                String.valueOf(System.currentTimeMillis()), Duration.ofHours(policy.failNotifyHours()));
        return Boolean.TRUE.equals(acquired);
    }
}
