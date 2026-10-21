package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordHistoryRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordHistoryDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordHistoryMapper;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
public class EduHomeworkRecordHistoryServiceImpl implements EduHomeworkRecordHistoryService {
    @Resource
    private EduHomeworkRecordHistoryMapper historyMapper;
    @Resource
    private AdminUserService adminUserService;

    @Override
    public Long append(EduHomeworkRecordDO record, String eventType, Integer toStatus,
                       Integer score, String comment, LocalDateTime redoDeadline, Long aiReviewId) {
        EduHomeworkRecordHistoryDO history = new EduHomeworkRecordHistoryDO();
        history.setRecordId(record.getId());
        history.setHomeworkId(record.getHomeworkId());
        history.setStudentId(record.getStudentId());
        history.setEventType(eventType);
        history.setFromStatus("SUBMIT".equals(eventType) ? null : record.getStatus());
        history.setToStatus(toStatus);
        history.setScore(score);
        history.setComment(comment);
        history.setRedoDeadline(redoDeadline);
        history.setAiReviewId(aiReviewId);
        history.setOperatorId(getLoginUserId());
        historyMapper.insert(history);
        return history.getId();
    }

    @Override
    public List<EduHomeworkRecordHistoryRespVO> listByRecordId(Long recordId) {
        List<EduHomeworkRecordHistoryDO> rows = historyMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordHistoryDO>()
                        .eq(EduHomeworkRecordHistoryDO::getRecordId, recordId)
                        .orderByAsc(EduHomeworkRecordHistoryDO::getCreateTime)
                        .orderByAsc(EduHomeworkRecordHistoryDO::getId));
        java.util.Set<Long> operatorIds = rows.stream()
                .map(EduHomeworkRecordHistoryDO::getOperatorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AdminUserDO> operators = operatorIds.isEmpty() ? Map.of() : adminUserService.getUserMap(operatorIds);
        return rows.stream().map(row -> {
            EduHomeworkRecordHistoryRespVO vo = BeanUtils.toBean(row, EduHomeworkRecordHistoryRespVO.class);
            AdminUserDO user = operators.get(row.getOperatorId());
            vo.setOperatorName(user == null ? null : user.getNickname());
            return vo;
        }).toList();
    }
}
