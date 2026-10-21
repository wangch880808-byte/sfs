package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemoveImpactRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.*;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.aireview.*;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** 删除区别于封存：从属记录也必须退出学生待办、提醒和成绩统计。 */
@Service
public class EduHomeworkRemoveService {

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;
    @Resource
    private EduHomeworkRecordHistoryMapper historyMapper;
    @Resource
    private EduHomeworkGradingActionMapper gradingActionMapper;
    @Resource
    private EduHomeworkExtensionMapper extensionMapper;
    @Resource
    private EduHomeworkRemindLogMapper remindLogMapper;
    @Resource
    private EduHomeworkNotifyStateMapper notifyStateMapper;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private AiEduReviewTaskMapper aiReviewTaskMapper;
    @Resource
    private AiEduFeedbackMapper feedbackMapper;
    @Resource
    private EduDataScopeService dataScopeService;

    public EduHomeworkRemoveImpactRespVO getRemoveImpact(Collection<Long> requestedIds) {
        List<Long> ids = requireManageAccess(requestedIds);
        long submittedCount = recordMapper.selectCount(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                .in(EduHomeworkRecordDO::getHomeworkId, ids)
                .in(EduHomeworkRecordDO::getStatus, List.of(EduHomeworkRecordStatusEnum.SUBMITTED,
                        EduHomeworkRecordStatusEnum.CORRECTED, EduHomeworkRecordStatusEnum.REDO,
                        EduHomeworkRecordStatusEnum.RESUBMITTED)));
        long studentCount = assigneeMapper.selectCount(new QueryWrapper<EduHomeworkAssigneeDO>()
                .select("DISTINCT student_id").in("homework_id", ids));
        return new EduHomeworkRemoveImpactRespVO(ids.size(), submittedCount, studentCount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeHomeworks(Collection<Long> requestedIds) {
        // 必须先检查整批，避免混入无权限任务时已经删除了前面的任务。
        List<Long> ids = requireManageAccess(requestedIds);
        // 反馈只有提交记录 ID，须在提交记录被逻辑删除前通过子查询定位；ID 已校验且类型为 Long。
        String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        feedbackMapper.delete(new LambdaQueryWrapper<AiEduFeedbackDO>()
                .inSql(AiEduFeedbackDO::getHomeworkRecordId,
                        "SELECT id FROM edu_homework_record WHERE deleted = 0 AND homework_id IN (" + idList + ")"));
        aiReviewTaskMapper.delete(new LambdaQueryWrapper<AiEduReviewTaskDO>().in(AiEduReviewTaskDO::getHomeworkId, ids));
        aiReviewMapper.delete(new LambdaQueryWrapper<EduHomeworkAiReviewDO>().in(EduHomeworkAiReviewDO::getHomeworkId, ids));
        gradingActionMapper.delete(new LambdaQueryWrapper<EduHomeworkGradingActionDO>().in(EduHomeworkGradingActionDO::getHomeworkId, ids));
        historyMapper.delete(new LambdaQueryWrapper<EduHomeworkRecordHistoryDO>().in(EduHomeworkRecordHistoryDO::getHomeworkId, ids));
        extensionMapper.delete(new LambdaQueryWrapper<EduHomeworkExtensionDO>().in(EduHomeworkExtensionDO::getHomeworkId, ids));
        remindLogMapper.delete(new LambdaQueryWrapper<EduHomeworkRemindLogDO>().in(EduHomeworkRemindLogDO::getHomeworkId, ids));
        notifyStateMapper.delete(new LambdaQueryWrapper<EduHomeworkNotifyStateDO>().in(EduHomeworkNotifyStateDO::getHomeworkId, ids));
        assigneeMapper.delete(new LambdaQueryWrapper<EduHomeworkAssigneeDO>().in(EduHomeworkAssigneeDO::getHomeworkId, ids));
        recordMapper.delete(new LambdaQueryWrapper<EduHomeworkRecordDO>().in(EduHomeworkRecordDO::getHomeworkId, ids));
        homeworkMapper.delete(new LambdaQueryWrapper<EduHomeworkDO>().in(EduHomeworkDO::getId, ids));
    }

    private List<Long> requireManageAccess(Collection<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.size() > 100
                || requestedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw exception(HOMEWORK_REMOVE_IDS_INVALID);
        }
        List<Long> ids = requestedIds.stream().distinct().toList();
        Long userId = getLoginUserId();
        if (userId == null) {
            throw exception(FORBIDDEN);
        }
        List<EduHomeworkDO> homeworks = homeworkMapper.selectByIds(ids);
        Set<Long> found = homeworks.stream().map(EduHomeworkDO::getId).collect(Collectors.toSet());
        if (!found.containsAll(ids)) {
            throw exception(FORBIDDEN);
        }
        boolean admin = dataScopeService.isGlobalAdmin();
        if (!admin && homeworks.stream().anyMatch(homework -> !Objects.equals(homework.getTeacherId(), userId))) {
            throw exception(FORBIDDEN);
        }
        return ids;
    }
}
