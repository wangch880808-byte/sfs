package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.module.edu.framework.EduRequestCache;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
public class EduDataScopeService {

    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseAssistantService assistantService;

    /** 管理员角色集合见参数配置 edu.role.admin-codes */
    public boolean isGlobalAdmin() {
        return eduRoleApi.isCurrentUserAdmin();
    }

    public void validateClassAccess(Long classId) {
        validateClassAccess(classId, false);
    }

    /** 花名册写入必须来自认领、助教或专属课团队，查看全部不等于管理班级。 */
    public void validateClassWriteAccess(Long classId) {
        validateClassAccess(classId, true);
    }

    private void validateClassAccess(Long classId, boolean write) {
        if (isGlobalAdmin()) {
            return;
        }
        Long userId = getLoginUserId();
        if (userId == null || classId == null) {
            throw exception(FORBIDDEN);
        }
        List<EduClassCourseDO> relations = classCourseMapper.selectList(
                new LambdaQueryWrapper<EduClassCourseDO>().eq(EduClassCourseDO::getClassId, classId));
        if (isAssistantOf(relations, userId)) {
            return;
        }
        Set<Long> courseIds = relations.stream().map(EduClassCourseDO::getCourseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (CollUtil.isNotEmpty(courseIds)) {
            List<CapacityCourseRespDTO> courses = EduRequestCache.get("courseList", Set.copyOf(courseIds),
                    () -> capacityCourseApi.getCourseList(courseIds));
            if (courses.stream().anyMatch(course -> Boolean.TRUE.equals(course.getPublicCourse())
                    ? relations.stream().anyMatch(relation -> Objects.equals(relation.getCourseId(), course.getId())
                            && Objects.equals(relation.getStatus(), 0)
                            && (write ? isClaimedByCurrentUser(course.getId(), classId)
                            : canViewAllPublic(course.getId()) || Objects.equals(relation.getClaimTeacherId(), userId)))
                    : Objects.equals(course.getTeacherId(), userId) || isCourseMember(course.getId(), userId))) {
                return;
            }
        }
        throw exception(FORBIDDEN);
    }

    public void validateCourseAccess(Long courseId) {
        if (isGlobalAdmin()) {
            return;
        }
        Long userId = getLoginUserId();
        CapacityCourseRespDTO course = courseId == null ? null : getCourse(courseId);
        if (userId != null && course != null
                && (Objects.equals(course.getTeacherId(), userId)
                || isCourseMember(course.getId(), userId)
                || (isTeacher() && isOnlinePublicCourse(course)))) {
            return;
        }
        throw exception(FORBIDDEN);
    }

    /**
     * 能否管理课程（绑定、解绑、恢复班课等写操作）：全局管理员、课程负责人或课程团队成员。
     * 与 {@link #validateCourseAccess} 不同，不放行在线公共课的其他教师。
     */
    public boolean canManageCourse(Long courseId) {
        if (isGlobalAdmin()) {
            return true;
        }
        Long userId = getLoginUserId();
        CapacityCourseRespDTO course = courseId == null ? null : getCourse(courseId);
        return userId != null && course != null
                && (Objects.equals(course.getTeacherId(), userId)
                || isCourseMember(course.getId(), userId));
    }

    public void validateCourseManage(Long courseId) {
        if (!canManageCourse(courseId)) {
            throw exception(FORBIDDEN);
        }
    }

    public boolean isOnlinePublicCourseForTeacher(Long courseId) {
        return getLoginUserId() != null && isTeacher()
                && isOnlinePublicCourse(courseId == null ? null : getCourse(courseId));
    }

    private CapacityCourseRespDTO getCourse(Long courseId) {
        return EduRequestCache.get("course", courseId, () -> capacityCourseApi.getCourse(courseId));
    }

    private boolean isCourseMember(Long courseId, Long userId) {
        return EduRequestCache.get("courseMember", List.of(courseId, userId),
                () -> capacityCourseApi.isCourseMember(courseId, userId));
    }

    private boolean isTeacher() {
        return eduRoleApi.isCurrentUserTeacher();
    }

    // ---------------- 公共课认领（V1.0.1.10）----------------
    // 公共课的班级按认领教师划分：教师只看、只管自己认领的班级；
    // 管理员、公共课审批人、课程负责人可以只读查看全部（团队成员不算）。专属课程不走这些规则。

    /** 公共课类型（不论审批与上线状态）。 */
    public boolean isPublicCourse(Long courseId) {
        CapacityCourseRespDTO course = courseId == null ? null : getCourse(courseId);
        return course != null && Boolean.TRUE.equals(course.getPublicCourse());
    }

    /** 公共课审批人（capacity:course:audit）。 */
    public boolean isCourseApprover() {
        Long userId = getLoginUserId();
        return userId != null && EduRequestCache.get("courseApprover", userId,
                () -> capacityCourseApi.isCourseAdmin(userId));
    }

    /** 能否只读查看公共课的全部班级与任务：管理员、审批人、课程负责人。 */
    public boolean canViewAllPublic(Long courseId) {
        if (isGlobalAdmin() || isCourseApprover()) {
            return true;
        }
        Long userId = getLoginUserId();
        CapacityCourseRespDTO course = courseId == null ? null : getCourse(courseId);
        return userId != null && course != null && Objects.equals(course.getTeacherId(), userId);
    }

    /** 按认领接口的功能权限、身份和上线条件决定是否显示入口。 */
    public boolean canClaimPublicCourse(CapacityCourseRespDTO course) {
        return course != null && course.isOnlinePublicCourse() && getLoginUserId() != null
                && securityFrameworkService.hasPermission("edu:class-course:bind")
                && (isGlobalAdmin() || isTeacher());
    }

    /** 放弃按当前行认领人授权，归档和历史学期不影响；功能权限与 release 入口一致。 */
    public boolean canReleasePublicClaim(Long claimTeacherId) {
        return claimTeacherId != null && getLoginUserId() != null
                && securityFrameworkService.hasPermission("edu:class-course:bind")
                && (isGlobalAdmin() || isCourseApprover() || Objects.equals(claimTeacherId, getLoginUserId()));
    }

    /** 某教师在公共课认领的在用班课关联（全部学期）。 */
    public List<EduClassCourseDO> listClaimedRelations(Long courseId, Long userId) {
        if (courseId == null || userId == null) {
            return List.of();
        }
        return EduRequestCache.get("claimedRelations", List.of(courseId, userId),
                () -> classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getCourseId, courseId)
                        .eq(EduClassCourseDO::getClaimTeacherId, userId)
                        .eq(EduClassCourseDO::getStatus, 0)));
    }

    /** 当前用户在公共课认领的班级 ID。 */
    public Set<Long> claimedClassIds(Long courseId) {
        return listClaimedRelations(courseId, getLoginUserId()).stream()
                .map(EduClassCourseDO::getClassId).collect(Collectors.toSet());
    }

    /** 当前用户是否认领了公共课的这个班级（在用关联）。 */
    public boolean isClaimedByCurrentUser(Long courseId, Long classId) {
        return classId != null && claimedClassIds(courseId).contains(classId);
    }

    /**
     * 公共课：能否给这个班级布置任务（新建、修改、复制、AI 新建、题库生成）。
     * 只有认领人和管理员可以；负责人、审批人只读。专属课程不要调用本方法。
     */
    public boolean canAssignToPublicClass(Long courseId, Long classId) {
        return isGlobalAdmin() || isClaimedByCurrentUser(courseId, classId);
    }

    /** 公共课：能否查看这个班级（班级详情、学生名单、留言、统计）：能看全部的人或认领人。 */
    public boolean canViewPublicClass(Long courseId, Long classId) {
        return canViewAllPublic(courseId) || isClaimedByCurrentUser(courseId, classId);
    }

    /** 课程 API 只返回未删除的课程。 */
    private boolean isOnlinePublicCourse(CapacityCourseRespDTO course) {
        return course != null && course.isOnlinePublicCourse();
    }

    /** 留言按班课授权：公共课认领人、可查看全部的人或助教；专属课保留课程团队。 */
    public void validateClassCourseAccess(Long classId, Long courseId) {
        if (classId == null || courseId == null) {
            throw exception(FORBIDDEN);
        }
        if (isGlobalAdmin()) {
            return;
        }
        List<EduClassCourseDO> relations = classCourseMapper.selectList(
                new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getClassId, classId)
                        .eq(EduClassCourseDO::getCourseId, courseId));
        if (relations.isEmpty()) {
            throw exception(FORBIDDEN);
        }
        Long userId = getLoginUserId();
        if (userId != null && isAssistantOf(relations, userId)) {
            return;
        }
        if (isPublicCourse(courseId)) {
            if (!canViewPublicClass(courseId, classId)) {
                throw exception(FORBIDDEN);
            }
            return;
        }
        validateCourseAccess(courseId);
    }

    /** 是否为任一班课关联的助教（edu_class_course_assistant）。 */
    private boolean isAssistantOf(List<EduClassCourseDO> relations, Long userId) {
        return !relations.isEmpty() && assistantService.isAssistant(
                relations.stream().map(EduClassCourseDO::getId).toList(), userId);
    }

}
