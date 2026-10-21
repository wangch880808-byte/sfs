package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionSetReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionStudentRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkExtensionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkExtensionMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class EduHomeworkExtensionServiceImpl implements EduHomeworkExtensionService {

    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkExtensionMapper extensionMapper;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduAiBusinessAccessService businessAccessService;

    @Override
    public List<EduHomeworkExtensionRespVO> listExtensions(Long homeworkId) {
        businessAccessService.requireHomeworkViewAccess(homeworkId);
        List<EduHomeworkExtensionDO> extensions = extensionMapper.selectListByHomeworkId(homeworkId);
        if (extensions.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> studentIds = extensions.stream().map(EduHomeworkExtensionDO::getStudentId)
                .collect(Collectors.toSet());
        Map<Long, Integer> statusMap = selectRecordStatusMap(homeworkId, studentIds);
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(studentIds);
        return extensions.stream().map(extension -> {
            EduHomeworkExtensionRespVO respVO = new EduHomeworkExtensionRespVO();
            respVO.setId(extension.getId());
            respVO.setHomeworkId(extension.getHomeworkId());
            respVO.setStudentId(extension.getStudentId());
            respVO.setDeadline(extension.getDeadline());
            respVO.setReason(extension.getReason());
            respVO.setUpdateTime(extension.getUpdateTime());
            respVO.setRecordStatus(statusMap.getOrDefault(extension.getStudentId(),
                    EduHomeworkRecordStatusEnum.NOT_SUBMITTED));
            AdminUserRespDTO user = userMap.get(extension.getStudentId());
            if (user != null) {
                respVO.setStudentName(user.getNickname());
                respVO.setStudentNo(user.getUsername());
            }
            return respVO;
        }).toList();
    }

    @Override
    public List<EduHomeworkExtensionStudentRespVO> listStudents(Long homeworkId) {
        validateOwnedHomework(homeworkId);
        List<Long> studentIds = assigneeMapper.selectListByHomeworkId(homeworkId).stream()
                .map(EduHomeworkAssigneeDO::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Integer> statusMap = selectRecordStatusMap(homeworkId, studentIds);
        Map<Long, LocalDateTime> extensionMap = extensionMapper.selectListByHomeworkId(homeworkId).stream()
                .collect(Collectors.toMap(EduHomeworkExtensionDO::getStudentId, EduHomeworkExtensionDO::getDeadline,
                        (left, right) -> left));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(studentIds);
        return studentIds.stream().map(studentId -> {
            EduHomeworkExtensionStudentRespVO respVO = new EduHomeworkExtensionStudentRespVO();
            respVO.setStudentId(studentId);
            respVO.setRecordStatus(statusMap.getOrDefault(studentId, EduHomeworkRecordStatusEnum.NOT_SUBMITTED));
            respVO.setPersonalDeadline(extensionMap.get(studentId));
            AdminUserRespDTO user = userMap.get(studentId);
            if (user != null) {
                respVO.setStudentName(user.getNickname());
                respVO.setStudentNo(user.getUsername());
            }
            return respVO;
        }).sorted(Comparator.comparing(EduHomeworkExtensionStudentRespVO::getStudentNo,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setExtensions(EduHomeworkExtensionSetReqVO reqVO) {
        EduHomeworkDO homework = validateOwnedHomework(reqVO.getHomeworkId());
        if (!Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)) {
            throw exception(HOMEWORK_EXTENSION_STATUS_INVALID);
        }
        if (homework.getDeadline() == null) {
            throw exception(HOMEWORK_EXTENSION_NO_DEADLINE);
        }
        LocalDateTime deadline = reqVO.getDeadline().withSecond(0).withNano(0);
        if (!deadline.isAfter(homework.getDeadline()) || !deadline.isAfter(LocalDateTime.now())) {
            throw exception(HOMEWORK_EXTENSION_DEADLINE_INVALID, homework.getDeadline().format(TIME_FORMAT));
        }
        Set<Long> studentIds = reqVO.getStudentIds() == null ? Collections.emptySet() : reqVO.getStudentIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (studentIds.isEmpty()) {
            throw exception(HOMEWORK_EXTENSION_STUDENT_NOT_ASSIGNED, 0);
        }
        Set<Long> assigned = assigneeMapper.selectList(new LambdaQueryWrapperX<EduHomeworkAssigneeDO>()
                        .eq(EduHomeworkAssigneeDO::getHomeworkId, homework.getId())
                        .in(EduHomeworkAssigneeDO::getStudentId, studentIds)).stream()
                .map(EduHomeworkAssigneeDO::getStudentId)
                .collect(Collectors.toSet());
        long notAssigned = studentIds.stream().filter(id -> !assigned.contains(id)).count();
        if (notAssigned > 0) {
            throw exception(HOMEWORK_EXTENSION_STUDENT_NOT_ASSIGNED, notAssigned);
        }
        Map<Long, Integer> statusMap = selectRecordStatusMap(homework.getId(), studentIds);
        if (!statusMap.isEmpty()) {
            throw exception(HOMEWORK_EXTENSION_STUDENT_SUBMITTED, statusMap.size());
        }
        String reason = StrUtil.trimToEmpty(reqVO.getReason());
        Long operatorId = getLoginUserId();
        Map<Long, EduHomeworkExtensionDO> existing = extensionMapper.selectList(
                        new LambdaQueryWrapperX<EduHomeworkExtensionDO>()
                                .eq(EduHomeworkExtensionDO::getHomeworkId, homework.getId())
                                .in(EduHomeworkExtensionDO::getStudentId, studentIds)).stream()
                .collect(Collectors.toMap(EduHomeworkExtensionDO::getStudentId, extension -> extension,
                        (left, right) -> left));
        List<Long> changedStudentIds = new ArrayList<>();
        for (Long studentId : studentIds) {
            EduHomeworkExtensionDO current = existing.get(studentId);
            if (current != null && Objects.equals(current.getDeadline(), deadline)
                    && Objects.equals(current.getReason(), reason)) {
                continue;
            }
            upsert(homework.getId(), studentId, current, deadline, reason, operatorId);
            changedStudentIds.add(studentId);
        }
        notifyExtended(homework, changedStudentIds, deadline, reason);
        return studentIds.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExtension(Long id) {
        EduHomeworkExtensionDO extension = id == null ? null : extensionMapper.selectById(id);
        if (extension == null) {
            throw exception(HOMEWORK_EXTENSION_NOT_EXISTS);
        }
        validateOwnedHomework(extension.getHomeworkId());
        extensionMapper.deleteById(id);
    }

    @Override
    public LocalDateTime getPersonalDeadline(Long homeworkId, Long studentId) {
        if (homeworkId == null || studentId == null) {
            return null;
        }
        EduHomeworkExtensionDO extension = extensionMapper.selectByHomeworkIdAndStudentId(homeworkId, studentId);
        return extension != null ? extension.getDeadline() : null;
    }

    @Override
    public Map<Long, LocalDateTime> getPersonalDeadlineMap(Long studentId, Collection<Long> homeworkIds) {
        if (studentId == null || CollUtil.isEmpty(homeworkIds)) {
            return Collections.emptyMap();
        }
        return extensionMapper.selectListByStudentIdAndHomeworkIds(studentId, homeworkIds).stream()
                .collect(Collectors.toMap(EduHomeworkExtensionDO::getHomeworkId, EduHomeworkExtensionDO::getDeadline,
                        (left, right) -> left));
    }

    private void upsert(Long homeworkId, Long studentId, EduHomeworkExtensionDO current,
                        LocalDateTime deadline, String reason, Long operatorId) {
        if (current == null) {
            EduHomeworkExtensionDO extension = new EduHomeworkExtensionDO();
            extension.setHomeworkId(homeworkId);
            extension.setStudentId(studentId);
            extension.setDeadline(deadline);
            extension.setReason(reason);
            extension.setOperatorId(operatorId);
            try {
                extensionMapper.insert(extension);
                return;
            } catch (DuplicateKeyException ex) {
                // 另一位教师或另一次请求刚刚为同一学生设置了延期：改为更新那一条。
                current = extensionMapper.selectByHomeworkIdAndStudentId(homeworkId, studentId);
                if (current == null) {
                    throw ex;
                }
            }
        }
        EduHomeworkExtensionDO update = new EduHomeworkExtensionDO();
        update.setId(current.getId());
        update.setDeadline(deadline);
        update.setReason(reason);
        update.setOperatorId(operatorId);
        extensionMapper.updateById(update);
    }

    private void notifyExtended(EduHomeworkDO homework, List<Long> studentIds, LocalDateTime deadline, String reason) {
        if (studentIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeworkName", Objects.toString(homework.getHomeworkName(), "未命名任务"));
        params.put("deadline", deadline.format(TIME_FORMAT));
        params.put("reason", StrUtil.blankToDefault(reason, "无"));
        businessNotifyService.sendToUsers(studentIds, EduBusinessNotifyService.HOMEWORK_EXTENDED, params,
                "/student/homework/submit/" + homework.getId());
    }

    private Map<Long, Integer> selectRecordStatusMap(Long homeworkId, Collection<Long> studentIds) {
        if (CollUtil.isEmpty(studentIds)) {
            return Collections.emptyMap();
        }
        return recordMapper.selectList(new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                        .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                        .in(EduHomeworkRecordDO::getStudentId, studentIds)).stream()
                .collect(Collectors.toMap(EduHomeworkRecordDO::getStudentId,
                        record -> Objects.requireNonNullElse(record.getStatus(),
                                EduHomeworkRecordStatusEnum.SUBMITTED),
                        (left, right) -> left));
    }

    /** 只有任务的发布教师可以查看和设置个别延期，与任务修改的权限一致。 */
    private EduHomeworkDO validateOwnedHomework(Long homeworkId) {
        EduHomeworkDO homework = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
        Long loginUserId = getLoginUserId();
        if (homework == null || loginUserId == null || !Objects.equals(homework.getTeacherId(), loginUserId)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        return homework;
    }

}
