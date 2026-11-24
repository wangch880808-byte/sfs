package cn.nus.zknu.module.edu.service.classrequest;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestRespVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classrequest.EduClassRequestDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classrequest.EduClassRequestMapper;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_REQUEST_ALREADY_REVIEWED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_REQUEST_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.COURSE_NOT_EXISTS;

/**
 * 建班申请 Service 实现类
 */
@Service
public class EduClassRequestServiceImpl implements EduClassRequestService {

    @Resource
    private EduClassRequestMapper eduClassRequestMapper;
    @Resource
    private EduClassCourseMapper eduClassCourseMapper;
    @Resource
    private EduClassService eduClassService;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long submitClassRequest(EduClassRequestSaveReqVO reqVO) {
        EduClassRequestDO request = BeanUtils.toBean(reqVO, EduClassRequestDO.class);
        request.setApplicantId(getLoginUserId());
        request.setStatus(0);
        eduClassRequestMapper.insert(request);
        return request.getId();
    }

    @Override
    public PageResult<EduClassRequestRespVO> getClassRequestPage(EduClassRequestPageReqVO reqVO) {
        PageResult<EduClassRequestDO> pageResult = eduClassRequestMapper.selectPage(reqVO,
                new LambdaQueryWrapperX<EduClassRequestDO>()
                        .eqIfPresent(EduClassRequestDO::getStatus, reqVO.getStatus())
                        .eqIfPresent(EduClassRequestDO::getCourseId, reqVO.getCourseId())
                        .eqIfPresent(EduClassRequestDO::getApplicantId, reqVO.getApplicantId())
                        .orderByDesc(EduClassRequestDO::getCreateTime));
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public EduClassRequestRespVO getClassRequest(Long id) {
        EduClassRequestDO request = eduClassRequestMapper.selectById(id);
        if (request == null) {
            throw exception(CLASS_REQUEST_NOT_EXISTS);
        }
        return toRespVOList(Collections.singletonList(request)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveClassRequest(Long id) {
        EduClassRequestDO request = eduClassRequestMapper.selectById(id);
        if (request == null) {
            throw exception(CLASS_REQUEST_NOT_EXISTS);
        }
        validateClassAndCourse(request);
        // 条件更新防重复审核：仅待审核状态可进入审核结果
        EduClassRequestDO update = new EduClassRequestDO();
        update.setStatus(1);
        update.setReviewerId(getLoginUserId());
        update.setReviewedAt(LocalDateTime.now());
        int rows = eduClassRequestMapper.update(update, new LambdaUpdateWrapper<EduClassRequestDO>()
                .eq(EduClassRequestDO::getId, id)
                .eq(EduClassRequestDO::getStatus, 0));
        if (rows == 0) {
            throw exception(CLASS_REQUEST_ALREADY_REVIEWED);
        }
        // 审核通过后创建班课关联
        EduClassCourseDO link = new EduClassCourseDO();
        link.setClassId(request.getClassId());
        link.setCourseId(request.getCourseId());
        link.setSemesterName(request.getTerm());
        link.setStatus(0);
        eduClassCourseMapper.insert(link);
        EduClassRequestDO linkUpdate = new EduClassRequestDO();
        linkUpdate.setId(request.getId());
        linkUpdate.setClassCourseId(link.getId());
        eduClassRequestMapper.updateById(linkUpdate);
    }

    @Override
    public void rejectClassRequest(Long id, String reason) {
        EduClassRequestDO request = eduClassRequestMapper.selectById(id);
        if (request == null) {
            throw exception(CLASS_REQUEST_NOT_EXISTS);
        }
        EduClassRequestDO update = new EduClassRequestDO();
        update.setStatus(2);
        update.setReason(reason);
        update.setReviewerId(getLoginUserId());
        update.setReviewedAt(LocalDateTime.now());
        int rows = eduClassRequestMapper.update(update, new LambdaUpdateWrapper<EduClassRequestDO>()
                .eq(EduClassRequestDO::getId, id)
                .eq(EduClassRequestDO::getStatus, 0));
        if (rows == 0) {
            throw exception(CLASS_REQUEST_ALREADY_REVIEWED);
        }
    }

    private void validateClassAndCourse(EduClassRequestDO request) {
        if (eduClassService.getClassList(Collections.singletonList(request.getClassId())).isEmpty()) {
            throw exception(CLASS_NOT_EXISTS);
        }
        if (capacityCourseApi.getCourse(request.getCourseId()) == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
    }

    /**
     * DO 列表转 RespVO，并补齐课程、班级、申请人、审核人名称
     */
    private List<EduClassRequestRespVO> toRespVOList(List<EduClassRequestDO> list) {
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        Map<Long, String> classNames = new HashMap<>();
        for (EduClassDO clazz : eduClassService.getClassList(convertSet(list, EduClassRequestDO::getClassId))) {
            classNames.put(clazz.getId(), clazz.getClassName());
        }
        Map<Long, String> courseNames = new HashMap<>();
        Set<Long> courseIds = convertSet(list, EduClassRequestDO::getCourseId);
        if (!courseIds.isEmpty()) {
            for (CapacityCourseRespDTO course : capacityCourseApi.getCourseList(courseIds)) {
                courseNames.put(course.getId(), course.getCourseName());
            }
        }
        Set<Long> userIds = new HashSet<>();
        for (EduClassRequestDO item : list) {
            if (item.getApplicantId() != null) {
                userIds.add(item.getApplicantId());
            }
            if (item.getReviewerId() != null) {
                userIds.add(item.getReviewerId());
            }
        }
        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            adminUserApi.getUserList(userIds).forEach(user -> userNames.put(user.getId(), user.getNickname()));
        }
        List<EduClassRequestRespVO> result = new ArrayList<>(list.size());
        for (EduClassRequestDO item : list) {
            EduClassRequestRespVO vo = BeanUtils.toBean(item, EduClassRequestRespVO.class);
            vo.setCourseName(courseNames.get(item.getCourseId()));
            vo.setClassName(classNames.get(item.getClassId()));
            vo.setApplicantName(userNames.get(item.getApplicantId()));
            vo.setReviewerName(userNames.get(item.getReviewerId()));
            result.add(vo);
        }
        return result;
    }

}
