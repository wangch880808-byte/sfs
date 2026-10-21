package cn.nus.zknu.module.edu.service.questionbank;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.QUESTION_BANK_NOT_EXISTS;

@Service
@Slf4j
public class EduQuestionBankServiceImpl implements EduQuestionBankService {

    @Resource
    private EduQuestionBankMapper questionBankMapper;
    @Resource
    private CapacityIndicatorApi capacityIndicatorApi;

    @Override
    public PageResult<QuestionBankRespVO> getPage(QuestionBankPageReqVO reqVO) {
        Long teacherId = getLoginUserId();
        PageResult<EduQuestionBankDO> page = questionBankMapper.selectPage(reqVO,
                new LambdaQueryWrapperX<EduQuestionBankDO>()
                        .eq(EduQuestionBankDO::getTeacherId, teacherId)
                        .eqIfPresent(EduQuestionBankDO::getIndicatorFirstId, reqVO.getIndicatorFirstId())
                        .eqIfPresent(EduQuestionBankDO::getIndicatorSecondId, reqVO.getIndicatorSecondId())
                        .eqIfPresent(EduQuestionBankDO::getHomeworkLevel, reqVO.getHomeworkLevel())
                        .likeIfPresent(EduQuestionBankDO::getTitle, reqVO.getTitle())
                        .orderByDesc(EduQuestionBankDO::getId));
        Set<Long> indicatorIds = page.getList().stream()
                .flatMap(item -> Arrays.stream(new Long[]{item.getIndicatorFirstId(), item.getIndicatorSecondId()}))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> indicatorNameMap = indicatorIds.isEmpty()
                ? Collections.emptyMap()
                : capacityIndicatorApi.getIndicatorList(indicatorIds).stream()
                .collect(Collectors.toMap(CapacityIndicatorRespDTO::getId, CapacityIndicatorRespDTO::getIndicatorName));
        List<QuestionBankRespVO> list = page.getList().stream()
                .map(item -> toRespVO(item, indicatorNameMap))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public void clearByTeacher() {
        Long teacherId = getLoginUserId();
        questionBankMapper.delete(new LambdaQueryWrapper<EduQuestionBankDO>()
                .eq(EduQuestionBankDO::getTeacherId, teacherId));
    }

    @Override
    public void delete(Long id) {
        // 只能删除本人的题目；他人题目按不存在处理，不暴露题目归属
        EduQuestionBankDO question = questionBankMapper.selectById(id);
        Long teacherId = getLoginUserId();
        if (question == null || teacherId == null || !Objects.equals(question.getTeacherId(), teacherId)) {
            throw exception(QUESTION_BANK_NOT_EXISTS);
        }
        questionBankMapper.deleteById(id);
    }

    private QuestionBankRespVO toRespVO(EduQuestionBankDO bankDO, Map<Long, String> indicatorNameMap) {
        QuestionBankRespVO vo = new QuestionBankRespVO();
        vo.setId(bankDO.getId());
        vo.setTitle(bankDO.getTitle());
        vo.setRequirement(bankDO.getRequirement());
        vo.setIndicatorFirstId(bankDO.getIndicatorFirstId());
        vo.setIndicatorFirstName(indicatorNameMap.get(bankDO.getIndicatorFirstId()));
        vo.setIndicatorSecondId(bankDO.getIndicatorSecondId());
        vo.setIndicatorSecondName(indicatorNameMap.get(bankDO.getIndicatorSecondId()));
        vo.setHomeworkLevel(bankDO.getHomeworkLevel());
        vo.setTeacherId(bankDO.getTeacherId());
        return vo;
    }

}
