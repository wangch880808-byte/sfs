package cn.nus.zknu.module.capacity.api.course;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import cn.nus.zknu.module.capacity.enums.CapacityCoursePublicAuditStatusEnum;
import cn.nus.zknu.module.capacity.enums.CapacityCourseTypeConstants;
import cn.nus.zknu.module.capacity.enums.CapacityPermissionConstants;
import cn.nus.zknu.module.capacity.service.course.CapacityCourseMemberService;
import cn.nus.zknu.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 实训课程 API 实现类
 */
@Service
public class CapacityCourseApiImpl implements CapacityCourseApi {

    @Resource
    private CapacityCourseMapper courseMapper;
    @Resource
    private CapacityCourseMemberService memberService;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public CapacityCourseRespDTO getCourse(Long id) {
        if (id == null) {
            return null;
        }
        CapacityCourseDO course = courseMapper.selectById(id);
        return course == null ? null : BeanUtils.toBean(course, CapacityCourseRespDTO.class);
    }

    @Override
    public List<CapacityCourseRespDTO> getCourseList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return BeanUtils.toBean(courseMapper.selectByIds(ids), CapacityCourseRespDTO.class);
    }

    @Override
    public List<CapacityCourseRespDTO> getOnlinePublicCourseList() {
        return BeanUtils.toBean(courseMapper.selectList(new LambdaQueryWrapperX<CapacityCourseDO>()
                .eq(CapacityCourseDO::getIsPublicCourse, CapacityCourseTypeConstants.PUBLIC_COURSE)
                .eq(CapacityCourseDO::getPublicAuditStatus, CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus())
                .eq(CapacityCourseDO::getStatus, 0)), CapacityCourseRespDTO.class);
    }

    @Override
    public List<Long> getCourseMemberUserIds(Long courseId) {
        return memberService.getMemberUserIds(courseId);
    }

    @Override
    public Map<Long, List<Long>> getCourseMemberUserIdsMap(Collection<Long> courseIds) {
        return memberService.getMemberUserIdsMap(courseIds);
    }

    @Override
    public boolean isCourseMember(Long courseId, Long userId) {
        return memberService.isMember(courseId, userId);
    }

    @Override
    public boolean isCourseAdmin(Long userId) {
        return userId != null && permissionApi.hasAnyPermissions(userId, CapacityPermissionConstants.COURSE_AUDIT);
    }

}
