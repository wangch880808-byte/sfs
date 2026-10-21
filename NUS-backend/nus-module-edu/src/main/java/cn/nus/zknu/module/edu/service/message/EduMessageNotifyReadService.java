package cn.nus.zknu.module.edu.service.message;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.nus.zknu.module.system.dal.mysql.notify.NotifyMessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 打开留言时，把同一会话产生的站内信一并标为已读，避免铃铛未读数和留言未读数对不上。
 * <p>
 * 只处理当前用户自己的未读通知：教师看 {@link EduBusinessNotifyService#TEACHER_MESSAGE_CREATED}，
 * 学生看 {@link EduBusinessNotifyService#STUDENT_MESSAGE_REPLY}。会话从通知参数里识别：
 * 新通知带 classId、courseId、studentId；旧通知只有 targetUrl，从其查询串解析。
 * 标记失败只记日志，不影响打开留言。
 */
@Service
@Slf4j
public class EduMessageNotifyReadService {

    /** 单次最多检查的未读通知条数，防止异常账号的大量未读拖慢打开留言。 */
    static final int MAX_SCAN = 500;

    static final String PARAM_CLASS_ID = "classId";
    static final String PARAM_COURSE_ID = "courseId";
    static final String PARAM_STUDENT_ID = "studentId";
    static final String PARAM_TARGET_URL = "targetUrl";

    @Resource
    private NotifyMessageMapper notifyMessageMapper;

    /** 教师打开某个学生的留言。 */
    public int markTeacherConversationRead(Long teacherId, Long classId, Long courseId, Long studentId) {
        return markRead(teacherId, EduBusinessNotifyService.TEACHER_MESSAGE_CREATED, classId, courseId, studentId);
    }

    /** 学生打开某门课程的留言；学生通知的接收人就是学生本人，所以不比对 studentId。 */
    public int markStudentConversationRead(Long studentId, Long classId, Long courseId) {
        return markRead(studentId, EduBusinessNotifyService.STUDENT_MESSAGE_REPLY, classId, courseId, null);
    }

    private int markRead(Long userId, String templateCode, Long classId, Long courseId, Long studentId) {
        if (userId == null || classId == null || courseId == null) {
            return 0;
        }
        try {
            List<NotifyMessageDO> unread = notifyMessageMapper.selectList(new LambdaQueryWrapperX<NotifyMessageDO>()
                    .eq(NotifyMessageDO::getUserId, userId)
                    .eq(NotifyMessageDO::getUserType, UserTypeEnum.ADMIN.getValue())
                    .eq(NotifyMessageDO::getReadStatus, false)
                    .eq(NotifyMessageDO::getTemplateCode, templateCode)
                    .orderByDesc(NotifyMessageDO::getId)
                    .last("LIMIT " + MAX_SCAN));
            List<Long> ids = unread.stream()
                    .filter(item -> matchesConversation(item.getTemplateParams(), classId, courseId, studentId))
                    .map(NotifyMessageDO::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (ids.isEmpty()) {
                return 0;
            }
            return notifyMessageMapper.updateListRead(ids, userId, UserTypeEnum.ADMIN.getValue());
        } catch (Exception ex) {
            log.warn("[markRead][留言相关通知标记已读失败 userId={}, templateCode={}, classId={}, courseId={}]",
                    userId, templateCode, classId, courseId, ex);
            return 0;
        }
    }

    /**
     * 通知参数是否指向该会话。studentId 为 null 时不比对学生。
     * 优先用显式参数；缺少时从 targetUrl 的查询串里取（V1.0.1.6 之前发出的通知只有 targetUrl）。
     */
    static boolean matchesConversation(Map<String, Object> params, Long classId, Long courseId, Long studentId) {
        if (params == null || params.isEmpty() || classId == null || courseId == null) {
            return false;
        }
        Map<String, String> query = parseQuery(params.get(PARAM_TARGET_URL));
        Long paramClassId = firstLong(params.get(PARAM_CLASS_ID), query.get(PARAM_CLASS_ID));
        Long paramCourseId = firstLong(params.get(PARAM_COURSE_ID), query.get(PARAM_COURSE_ID));
        if (!classId.equals(paramClassId) || !courseId.equals(paramCourseId)) {
            return false;
        }
        if (studentId == null) {
            return true;
        }
        return studentId.equals(firstLong(params.get(PARAM_STUDENT_ID), query.get(PARAM_STUDENT_ID)));
    }

    private static Long firstLong(Object explicit, String fromUrl) {
        Long value = toLong(explicit);
        return value != null ? value : toLong(fromUrl);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? null : StrUtil.trimToNull(String.valueOf(value));
        if (text == null || !text.matches("\\d{1,18}")) {
            return null;
        }
        return Long.parseLong(text);
    }

    private static Map<String, String> parseQuery(Object targetUrl) {
        Map<String, String> result = new HashMap<>();
        String url = targetUrl == null ? null : StrUtil.trimToNull(String.valueOf(targetUrl));
        if (url == null) {
            return result;
        }
        String rawQuery;
        try {
            rawQuery = URI.create(url).getRawQuery();
        } catch (IllegalArgumentException ex) {
            int index = url.indexOf('?');
            rawQuery = index < 0 ? null : url.substring(index + 1);
        }
        if (StrUtil.isBlank(rawQuery)) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            try {
                String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
                result.putIfAbsent(key, value);
            } catch (IllegalArgumentException ignored) {
                // 编码损坏的参数不参与匹配
            }
        }
        return result;
    }

}
