package cn.nus.zknu.module.edu.service.notification;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.system.api.notify.NotifyMessageSendApi;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendBatchToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学业务站内信发送门面。通知失败只记录日志，不阻断核心教学操作。
 */
@Service
@Slf4j
public class EduBusinessNotifyService {

    public static final String CLASS_JOINED = "edu.student.class.joined";
    public static final String COURSE_ASSIGNED = "edu.student.course.assigned";
    public static final String HOMEWORK_CREATED = "edu.student.homework.created";
    public static final String HOMEWORK_UPDATED = "edu.student.homework.updated";
    public static final String HOMEWORK_CORRECTED = "edu.student.homework.corrected";
    public static final String HOMEWORK_REDO = "edu.student.homework.redo";
    public static final String HOMEWORK_REMIND = "edu.student.homework.remind";
    public static final String HOMEWORK_EXTENDED = "edu.student.homework.extended";
    public static final String TEACHER_HOMEWORK_SUBMITTED = "edu.teacher.homework.submitted";
    public static final String STUDENT_MESSAGE_REPLY = "edu.student.message.reply";
    public static final String TEACHER_MESSAGE_CREATED = "edu.teacher.message.created";
    /** 自动 AI 初评失败，提醒布置教师到批改页重新生成。 */
    public static final String TEACHER_HOMEWORK_AI_REVIEW_FAILED = "edu.teacher.homework.ai-review-failed";

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    public boolean sendToUser(Long userId, String templateCode,
                              Map<String, Object> templateParams, String targetUrl) {
        if (userId == null || StrUtil.isBlank(templateCode)) {
            return false;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (templateParams != null) {
            params.putAll(templateParams);
        }
        if (StrUtil.isNotBlank(targetUrl)) {
            params.put("targetUrl", targetUrl);
        }
        try {
            NotifySendSingleToUserReqDTO reqDTO = new NotifySendSingleToUserReqDTO();
            reqDTO.setUserId(userId);
            reqDTO.setTemplateCode(templateCode);
            reqDTO.setTemplateParams(params);
            return notifyMessageSendApi.sendSingleMessageToAdmin(reqDTO) != null;
        } catch (Exception ex) {
            log.error("[sendToUser][教学站内信发送失败 userId={}, templateCode={}]",
                    userId, templateCode, ex);
            return false;
        }
    }

    public int sendToUsers(Collection<Long> userIds, String templateCode,
                           Map<String, Object> templateParams, String targetUrl) {
        return sendToUsersWithResult(userIds, templateCode, templateParams, targetUrl).size();
    }

    /** 自动提醒需要准确撤销失败收件人的占位，不能仅凭成功条数推断谁收到通知。 */
    public List<Long> sendToUsersWithResult(Collection<Long> userIds, String templateCode,
                                           Map<String, Object> templateParams, String targetUrl) {
        if (CollUtil.isEmpty(userIds) || StrUtil.isBlank(templateCode)) return List.of();
        List<Long> recipients = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) return List.of();
        Map<String, Object> params = new LinkedHashMap<>();
        if (templateParams != null) params.putAll(templateParams);
        if (StrUtil.isNotBlank(targetUrl)) params.put("targetUrl", targetUrl);
        try {
            NotifySendBatchToUserReqDTO req = new NotifySendBatchToUserReqDTO();
            req.setUserIds(recipients);
            req.setTemplateCode(templateCode);
            req.setTemplateParams(params);
            List<Long> successful = notifyMessageSendApi.sendBatchMessageToAdmin(req);
            return successful == null ? List.of() : successful;
        } catch (Exception ex) {
            log.error("[sendToUsers][教学站内信批量发送失败 templateCode={}, 用户数={}]", templateCode, recipients.size(), ex);
            return List.of();
        }
    }
}
