package cn.nus.zknu.module.edu.controller.admin.classcourse;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.*;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - 班级课程关联")
@RestController
@RequestMapping("/edu/class-course")
@Validated
public class EduClassCourseController {

    @Resource
    private EduClassCourseService eduClassCourseService;
    @Resource
    private EduClassService eduClassService;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseAssistantService assistantService;

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private EduDataScopeService dataScopeService;

    /**
     * 将班课关联DO列表转换为带班级名称、课程名称、助教的RespVO列表；各一次批量查询。
     */
    private List<EduClassCourseRespVO> toRespVOList(List<EduClassCourseDO> list) {
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        Map<Long, String> classNames = new HashMap<>();
        for (EduClassDO clazz : eduClassService.getClassList(convertSet(list, EduClassCourseDO::getClassId))) {
            classNames.put(clazz.getId(), clazz.getClassName());
        }
        Map<Long, String> courseNames = new HashMap<>();
        Map<Long, CapacityCourseRespDTO> courses = new HashMap<>();
        Set<Long> courseIds = convertSet(list, EduClassCourseDO::getCourseId);
        if (!courseIds.isEmpty()) {
            for (CapacityCourseRespDTO course : capacityCourseApi.getCourseList(courseIds)) {
                courseNames.put(course.getId(), course.getCourseName());
                courses.put(course.getId(), course);
            }
        }
        // 助教取自 edu_class_course_assistant，不读旧的逗号串列
        Map<Long, List<Long>> assistants = assistantService.getTeacherIdsMap(convertSet(list, EduClassCourseDO::getId));
        Set<Long> claimTeacherIds = list.stream().map(EduClassCourseDO::getClaimTeacherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> teacherNames = new HashMap<>();
        if (!claimTeacherIds.isEmpty()) {
            adminUserApi.getUserList(claimTeacherIds).forEach(user -> teacherNames.put(user.getId(), user.getNickname()));
        }
        Map<Long, Boolean> manage = new HashMap<>();
        boolean admin = dataScopeService.isGlobalAdmin();
        List<EduClassCourseRespVO> result = new ArrayList<>(list.size());
        for (EduClassCourseDO item : list) {
            EduClassCourseRespVO vo = BeanUtils.toBean(item, EduClassCourseRespVO.class);
            vo.setAssistantTeacherIds(EduClassCourseAssistantService.toCsv(assistants.get(item.getId())));
            vo.setClassName(classNames.get(item.getClassId()));
            vo.setCourseName(courseNames.get(item.getCourseId()));
            vo.setClaimTeacherName(teacherNames.get(item.getClaimTeacherId()));
            CapacityCourseRespDTO course = courses.get(item.getCourseId());
            boolean publicCourse = course != null && Boolean.TRUE.equals(course.getPublicCourse());
            vo.setCanRelease(publicCourse && dataScopeService.canReleasePublicClaim(item.getClaimTeacherId()));
            vo.setCanOperate(publicCourse
                    ? admin || (getLoginUserId() != null && Objects.equals(item.getClaimTeacherId(), getLoginUserId()))
                    : manage.computeIfAbsent(item.getCourseId(), dataScopeService::canManageCourse));
            result.add(vo);
        }
        return result;
    }

    @GetMapping("/claim-options")
    @Operation(summary = "查询公共课认领选项")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    public CommonResult<List<EduClassCourseClaimOptionRespVO>> claimOptions(@RequestParam("courseId") Long courseId,
            @RequestParam(value = "semesterName", required = false) String semesterName) {
        return success(eduClassCourseService.claimOptions(courseId, semesterName));
    }

    @PostMapping("/claim")
    @Operation(summary = "认领公共课班级")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    public CommonResult<Boolean> claimClassCourse(@Valid @RequestBody EduClassCourseClaimReqVO request) {
        eduClassCourseService.claimClassCourse(request);
        return success(true);
    }

    @PutMapping("/release")
    @Operation(summary = "放弃或清除公共课认领")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    public CommonResult<Boolean> releaseClassCourse(@RequestParam("id") Long id) {
        eduClassCourseService.releaseClassCourse(id);
        return success(true);
    }

    /**
     * 班级绑定课程（支持批量，重复跳过）。
     */
    @PostMapping("/bind")
    @Operation(summary = "班级绑定课程")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班级绑定正在处理中，请勿重复操作")
    public CommonResult<Boolean> bindClassCourse(@Valid @RequestBody EduClassCourseBindReqVO bindReqVO) {
        eduClassCourseService.bindClassCourse(bindReqVO);
        return success(true);
    }

    /**
     * 班级解绑课程。
     */
    @DeleteMapping("/unbind")
    @Operation(summary = "封存班课关联")
    @Parameter(name = "id", description = "班课关联 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班课封存正在处理中，请勿重复操作")
    public CommonResult<Boolean> unbindClassCourse(@RequestParam("id") Long id) {
        eduClassCourseService.unbindClassCourse(id);
        return success(true);
    }

    @PutMapping("/restore")
    @Operation(summary = "恢复班课关联")
    @PreAuthorize("@ss.hasPermission('edu:class-course:bind')")
    public CommonResult<Boolean> restoreClassCourse(@RequestParam("id") Long id) {
        eduClassCourseService.restoreClassCourse(id);
        return success(true);
    }

    /**
     * 查询班级的课程列表。
     */
    @GetMapping("/list-by-class")
    @Operation(summary = "查询班级的课程列表")
    @Parameter(name = "classId", description = "班级 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:class-course:query')")
    public CommonResult<List<EduClassCourseRespVO>> listByClass(@RequestParam("classId") Long classId,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived) {
        List<EduClassCourseDO> list = eduClassCourseService.listByClass(classId, includeArchived);
        return success(toRespVOList(list));
    }

    /**
     * 查询课程的班级列表。
     */
    @GetMapping("/list-by-course")
    @Operation(summary = "查询课程的班级列表")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('edu:class-course:query', 'edu:homework:query')")
    public CommonResult<List<EduClassCourseRespVO>> listByCourse(@RequestParam("courseId") Long courseId,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived) {
        List<EduClassCourseDO> list = eduClassCourseService.listByCourse(courseId, includeArchived);
        return success(toRespVOList(list));
    }

}
