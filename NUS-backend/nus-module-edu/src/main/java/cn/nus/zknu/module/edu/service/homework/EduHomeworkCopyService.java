package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.copy.EduHomeworkCopyTargetRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/** 复制表单可选择的班课，沿用原排序与布置权限范围。 */
@Service
public class EduHomeworkCopyService {

    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduSemesterOptionService semesterOptionService;

    /** 当前教师在某个学期可以布置任务的班级课程。 */
    public List<EduHomeworkCopyTargetRespVO> listTargets(String semesterName) {
        EduSemesterOptionRespVO semester = semesterOptionService.resolve(semesterName);
        List<EduClassCourseDO> links = classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getStatus, 0)
                .apply("TRIM(semester_name) = {0}", semester.getSemesterName())
                .orderByAsc(EduClassCourseDO::getCourseId)
                .orderByAsc(EduClassCourseDO::getClassId));
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, CapacityCourseRespDTO> courses = activeCourses(links.stream()
                .map(EduClassCourseDO::getCourseId).collect(Collectors.toSet()));
        Map<Long, EduClassDO> classes = activeClasses(links.stream()
                .map(EduClassCourseDO::getClassId).collect(Collectors.toSet()));
        Set<Long> accessibleCourseIds = new HashSet<>();
        for (Long courseId : courses.keySet()) {
            try {
                // 课程负责人、课程团队、在线公共课的教师可以给该课程的班级布置任务
                dataScopeService.validateCourseAccess(courseId);
                accessibleCourseIds.add(courseId);
            } catch (ServiceException ignored) {
                // 无权限的课程不出现在可选列表里
            }
        }
        List<EduHomeworkCopyTargetRespVO> result = new ArrayList<>();
        for (EduClassCourseDO link : links) {
            CapacityCourseRespDTO course = courses.get(link.getCourseId());
            EduClassDO clazz = classes.get(link.getClassId());
            if (course == null || clazz == null || !accessibleCourseIds.contains(course.getId())) {
                continue;
            }
            if (dataScopeService.isPublicCourse(course.getId())
                    && !dataScopeService.canAssignToPublicClass(course.getId(), link.getClassId())) {
                continue;
            }
            EduHomeworkCopyTargetRespVO target = new EduHomeworkCopyTargetRespVO();
            target.setClassCourseId(link.getId());
            target.setClassId(clazz.getId());
            target.setClassName(clazz.getClassName());
            target.setCourseId(course.getId());
            target.setCourseName(course.getCourseName());
            target.setSemesterName(StrUtil.trim(link.getSemesterName()));
            target.setPublicCourse(Objects.equals(course.getIsPublicCourse(), 0)
                    && Objects.equals(course.getPublicAuditStatus(), 2));
            result.add(target);
        }
        return result;
    }

    /** 已批量读取任务名时直接复用命名规则，不再逐班级查询。 */
    public String uniqueName(String name, Collection<String> existing) {
        String base = StrUtil.blankToDefault(name, "未命名任务");
        if (!existing.contains(base)) {
            return base;
        }
        int maxSuffix = 0;
        for (String candidate : existing) {
            if (candidate.startsWith(base + "(") && candidate.endsWith(")")) {
                try {
                    maxSuffix = Math.max(maxSuffix,
                            Integer.parseInt(candidate.substring(base.length() + 1, candidate.length() - 1)));
                } catch (NumberFormatException ignored) {
                    // 名称本身带括号，不是序号
                }
            }
        }
        return base + "(" + (maxSuffix + 1) + ")";
    }

    private Map<Long, CapacityCourseRespDTO> activeCourses(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return capacityCourseApi.getCourseList(ids).stream()
                .filter(course -> Objects.equals(course.getStatus(), 0))
                .collect(Collectors.toMap(CapacityCourseRespDTO::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, EduClassDO> activeClasses(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectByIds(ids).stream()
                .filter(clazz -> Objects.equals(clazz.getStatus(), 0))
                .collect(Collectors.toMap(EduClassDO::getId, Function.identity(), (left, right) -> left));
    }

}
