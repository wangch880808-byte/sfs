package cn.nus.zknu.module.edu.service.message;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.controller.admin.message.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkArchiveAccessService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Service
public class EduMessageServiceImpl implements EduMessageService {
    @Resource private EduMessageMapper messageMapper;
    @Resource private EduClassCourseMapper classCourseMapper;
    @Resource private EduClassCourseAssistantService assistantService;
    @Resource private CapacityCourseApi capacityCourseApi;
    @Resource private EduClassStudentMapper classStudentMapper;
    @Resource private EduDataScopeService dataScopeService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private EduBusinessNotifyService businessNotifyService;
    @Resource private EduMessageRecipientResolver recipientResolver;
    @Resource private EduMessageNotifyReadService notifyReadService;
    @Resource private EduHomeworkArchiveAccessService archiveAccessService;

    @Override
    public List<MessageStudentListRespVO> getStudentList(Long classId, Long courseId, String name) {
        dataScopeService.validateClassCourseAccess(classId, courseId);
        List<MessageStudentListRespVO> students = messageMapper.selectTeacherStudentSummaries(classId, courseId);
        List<Long> studentIds = students.stream().map(MessageStudentListRespVO::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        var users = studentIds.isEmpty() ? Collections.<Long, AdminUserRespDTO>emptyMap()
                : adminUserApi.getUserMap(studentIds);
        for (MessageStudentListRespVO student : students) {
            var user = student.getStudentId() == null ? null : users.get(student.getStudentId());
            student.setStudentName(user == null ? "学生" : StrUtil.blankToDefault(user.getNickname(), "学生"));
        }
        return students.stream()
                .filter(vo -> StrUtil.isBlank(name) || vo.getStudentName().contains(name)).toList();
    }

    @Override
    @Transactional
    public MessageConversationRespVO getDetail(Long classId, Long courseId, Long studentId) {
        dataScopeService.validateClassCourseAccess(classId, courseId);
        if (studentId == null) throw exception(MESSAGE_NOT_EXISTS);
        markRead(classId, courseId, studentId, 0);
        notifyReadService.markTeacherConversationRead(getLoginUserId(), classId, courseId, studentId);
        return buildDetail(classId, courseId, studentId);
    }

    @Override
    public List<StudentConversationRespVO> getMyConversations() {
        Long studentId = getLoginUserId();
        if (studentId == null) throw exception(MESSAGE_STUDENT_COURSE_FORBIDDEN);
        List<StudentConversationRespVO> result = messageMapper.selectStudentConversations(studentId);
        for (StudentConversationRespVO vo : result) {
            List<EduMessageDO> messages = findMessages(vo.getClassId(), vo.getCourseId(), studentId);
            vo.setStatus(conversationStatus(messages));
            vo.setUnreadCount((int) messages.stream()
                    .filter(msg -> Objects.equals(msg.getType(), 1) && !Objects.equals(msg.getReadStatus(), 1))
                    .count());
            if (!messages.isEmpty()) {
                EduMessageDO latest = messages.get(messages.size() - 1);
                vo.setLastMessageContent(latest.getContent());
                vo.setLastMessageTime(latest.getCreateTime());
            }
        }
        return result;
    }

    @Override
    @Transactional
    public MessageConversationRespVO getMyDetail(Long classId, Long courseId) {
        Long studentId = requireStudentConversation(classId, courseId);
        markRead(classId, courseId, studentId, 1);
        notifyReadService.markStudentConversationRead(studentId, classId, courseId);
        return buildDetail(classId, courseId, studentId);
    }

    @Override
    public void markMyRead(Long classId, Long courseId) {
        Long studentId = requireStudentConversation(classId, courseId);
        markRead(classId, courseId, studentId, 1);
        notifyReadService.markStudentConversationRead(studentId, classId, courseId);
    }

    @Override
    public void reply(Long classId, Long courseId, Long studentId, String content) {
        validateTeacherWriteAccess(classId, courseId);
        // 班级、课程或班课归档后留言只读
        archiveAccessService.validateMessageScopeActive(classId, courseId);
        validateStudentInClass(classId, studentId);
        validateContent(content);
        ensureOpen(classId, courseId, studentId);
        insertMessage(classId, courseId, studentId, content, 1);
        var course = capacityCourseApi.getCourse(courseId);
        if (course != null) {
            Map<String, Object> params = conversationParams(classId, courseId, null);
            params.put("courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程"));
            params.put("content", content);
            businessNotifyService.sendToUser(studentId, EduBusinessNotifyService.STUDENT_MESSAGE_REPLY, params,
                    "/student/message?classId=" + classId + "&courseId=" + courseId);
        }
    }

    @Override
    public void studentAdd(Long classId, Long courseId, String content) {
        Long studentId = requireStudentConversation(classId, courseId);
        validateContent(content);
        ensureOpen(classId, courseId, studentId);
        insertMessage(classId, courseId, studentId, content, 0);
        var course = capacityCourseApi.getCourse(courseId);
        if (course == null) {
            return;
        }
        // 按课程类型和班课授权接收通知，同一人只发一次。
        Set<Long> recipients = new LinkedHashSet<>(recipientResolver.resolveTeacherRecipients(classId, courseId, course));
        recipients.remove(studentId);
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> params = conversationParams(classId, courseId, studentId);
        params.put("studentName", userName(studentId, "学生"));
        params.put("courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程"));
        params.put("content", content);
        businessNotifyService.sendToUsers(recipients, EduBusinessNotifyService.TEACHER_MESSAGE_CREATED, params,
                "/edu/message?classId=" + classId + "&courseId=" + courseId + "&studentId=" + studentId);
    }

    /** 通知里带上会话定位参数，打开留言时据此把相关通知标为已读。 */
    private Map<String, Object> conversationParams(Long classId, Long courseId, Long studentId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("classId", classId);
        params.put("courseId", courseId);
        if (studentId != null) {
            params.put("studentId", studentId);
        }
        return params;
    }

    @Override
    public void closeConversation(Long classId, Long courseId, Long studentId) {
        updateConversationStatus(classId, courseId, studentId, 1);
    }

    @Override
    public void reopenConversation(Long classId, Long courseId, Long studentId) {
        updateConversationStatus(classId, courseId, studentId, 0);
    }

    private void updateConversationStatus(Long classId, Long courseId, Long studentId, int status) {
        validateTeacherWriteAccess(classId, courseId);
        if (studentId == null) throw exception(MESSAGE_NOT_EXISTS);
        if (findMessages(classId, courseId, studentId).isEmpty()) throw exception(MESSAGE_NOT_EXISTS);
        messageMapper.update(null, new LambdaUpdateWrapper<EduMessageDO>()
                .eq(EduMessageDO::getClassId, classId).eq(EduMessageDO::getCourseId, courseId)
                .eq(EduMessageDO::getStudentId, studentId).set(EduMessageDO::getStatus, status));
    }

    private void validateTeacherWriteAccess(Long classId, Long courseId) {
        dataScopeService.validateClassCourseAccess(classId, courseId);
        if (!dataScopeService.isPublicCourse(courseId) || dataScopeService.isGlobalAdmin()
                || dataScopeService.isClaimedByCurrentUser(courseId, classId)) {
            return;
        }
        // 查看全部是只读权限；助教仍按班课关联处理留言。
        var relations = classCourseMapper.selectList(
                new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getClassId, classId)
                        .eq(EduClassCourseDO::getCourseId, courseId));
        if (getLoginUserId() == null || !assistantService.isAssistant(
                relations.stream().map(EduClassCourseDO::getId).toList(),
                getLoginUserId())) {
            throw exception(FORBIDDEN);
        }
    }

    private Long requireStudentConversation(Long classId, Long courseId) {
        Long studentId = getLoginUserId();
        if (studentId == null || classId == null || courseId == null
                || !messageMapper.existsStudentConversation(studentId, classId, courseId)) {
            throw exception(MESSAGE_STUDENT_COURSE_FORBIDDEN);
        }
        return studentId;
    }

    private void validateStudentInClass(Long classId, Long studentId) {
        if (studentId == null || classStudentMapper.selectListByClassIdAndStudentIds(
                classId, List.of(studentId)).isEmpty()) throw exception(MESSAGE_STUDENT_COURSE_FORBIDDEN);
    }

    private void validateContent(String content) {
        if (StrUtil.isBlank(content) || content.trim().length() > 1000) throw exception(MESSAGE_CONTENT_INVALID);
    }

    private void ensureOpen(Long classId, Long courseId, Long studentId) {
        if (conversationStatus(findMessages(classId, courseId, studentId)) == 1) {
            throw exception(MESSAGE_CONVERSATION_CLOSED);
        }
    }

    private void insertMessage(Long classId, Long courseId, Long studentId, String content, int type) {
        EduMessageDO msg = new EduMessageDO();
        msg.setClassId(classId);
        msg.setCourseId(courseId);
        msg.setStudentId(studentId);
        msg.setContent(content.trim());
        msg.setType(type);
        msg.setStatus(0);
        msg.setReadStatus(0);
        messageMapper.insert(msg);
    }

    private List<EduMessageDO> findMessages(Long classId, Long courseId, Long studentId) {
        return messageMapper.selectList(conversationQuery(classId, courseId, studentId)
                .orderByAsc(EduMessageDO::getId));
    }

    private LambdaQueryWrapper<EduMessageDO> conversationQuery(Long classId, Long courseId, Long studentId) {
        return new LambdaQueryWrapper<EduMessageDO>().eq(EduMessageDO::getClassId, classId)
                .eq(EduMessageDO::getCourseId, courseId)
                .eq(studentId != null, EduMessageDO::getStudentId, studentId);
    }

    private int conversationStatus(List<EduMessageDO> messages) {
        return messages.isEmpty() ? 0 : Objects.equals(messages.get(messages.size() - 1).getStatus(), 1) ? 1 : 0;
    }

    private void markRead(Long classId, Long courseId, Long studentId, int senderType) {
        messageMapper.update(null, new LambdaUpdateWrapper<EduMessageDO>()
                .eq(EduMessageDO::getClassId, classId).eq(EduMessageDO::getCourseId, courseId)
                .eq(EduMessageDO::getStudentId, studentId).eq(EduMessageDO::getType, senderType)
                .eq(EduMessageDO::getReadStatus, 0)
                .set(EduMessageDO::getReadStatus, 1).set(EduMessageDO::getReadTime, LocalDateTime.now()));
    }

    private MessageConversationRespVO buildDetail(Long classId, Long courseId, Long studentId) {
        List<EduMessageDO> messages = findMessages(classId, courseId, studentId);
        MessageConversationRespVO result = new MessageConversationRespVO();
        result.setStatus(conversationStatus(messages));
        result.setStudentName(userName(studentId, "学生"));
        result.setMessages(messages.stream().map(msg -> {
            MessageDetailRespVO vo = new MessageDetailRespVO();
            vo.setId(msg.getId());
            vo.setType(msg.getType());
            vo.setContent(msg.getContent());
            vo.setCreateTime(msg.getCreateTime());
            vo.setReadStatus(msg.getReadStatus());
            vo.setSenderName(Objects.equals(msg.getType(), 0) ? result.getStudentName()
                    : userName(parseUserId(msg.getCreator()), "教师"));
            return vo;
        }).toList());
        return result;
    }

    private Long parseUserId(String creator) {
        try { return Long.valueOf(creator); } catch (NumberFormatException e) { return null; }
    }

    private String userName(Long userId, String fallback) {
        var user = userId == null ? null : adminUserApi.getUser(userId);
        return user == null || StrUtil.isBlank(user.getNickname()) ? fallback : user.getNickname();
    }
}
