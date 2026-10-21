package cn.nus.zknu.module.edu.service.student;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseBatchMapper;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseProgressRow;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_RESOURCE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_IDENTITY_REQUIRED;

/**
 * 学生课程 Service 实现类
 */
@Service
public class StudentCourseServiceImpl implements StudentCourseService {

    private static final int STUDENT_USER_TYPE = 2;

    @Resource
    private StudentCourseMapper studentCourseMapper;
    @Resource
    private StudentCourseBatchMapper studentCourseBatchMapper;
    @Resource
    private EduCourseResourceMapper courseResourceMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private StudentLevelUnlockService levelUnlockService;

    @Override
    public List<AppStudentCourseRespVO> getCourseList(Long studentId) {
        validateStudentIdentity(studentId);
        List<Long> classIds = studentCourseMapper.selectClassIdsByStudentId(studentId);
        if (classIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<AppStudentCourseRespVO> courses = studentCourseMapper.selectCoursesByClassIds(classIds);
        if (courses.isEmpty()) {
            return courses;
        }
        List<Long> courseIds = courses.stream().map(AppStudentCourseRespVO::getCourseId).distinct().toList();
        Map<Long, StudentCourseProgressRow> progressMap = studentCourseBatchMapper
                .selectCourseProgressBatch(studentId, courseIds).stream()
                .collect(Collectors.toMap(StudentCourseProgressRow::getCourseId, row -> row));
        Map<Long, List<AppStudentCourseDetailRespVO.Homework>> homeworkMap = new HashMap<>();
        studentCourseBatchMapper.selectCourseHomeworksBatch(studentId, courseIds).forEach(row ->
                homeworkMap.computeIfAbsent(row.getCourseId(), id -> new ArrayList<>()).add(row.getHomework()));
        Map<Long, List<AppStudentCourseDetailRespVO.Resource>> resourceMap = new HashMap<>();
        courseResourceMapper.selectList(new LambdaQueryWrapper<EduCourseResourceDO>()
                .select(EduCourseResourceDO::getId, EduCourseResourceDO::getCourseId,
                        EduCourseResourceDO::getResourceName, EduCourseResourceDO::getFileType,
                        EduCourseResourceDO::getFileSize)
                .in(EduCourseResourceDO::getCourseId, courseIds)).forEach(row ->
                resourceMap.computeIfAbsent(row.getCourseId(), id -> new ArrayList<>()).add(toResource(row)));
        for (AppStudentCourseRespVO course : courses) {
            fillCourseProgress(course, progressMap.get(course.getCourseId()));
            course.setResources(resourceMap.getOrDefault(course.getCourseId(), Collections.emptyList()));
            course.setHomeworkList(homeworkMap.getOrDefault(course.getCourseId(), Collections.emptyList()));
        }
        return courses;
    }

    @Override
    public AppStudentCourseDetailRespVO getCourseDetail(Long studentId, Long courseId) {
        // 当前课程照常显示；课程、班级或班课归档后，做过任务的学生仍可只读查看（往期课程）
        boolean history = !validateStudentCourseReadable(studentId, courseId);
        AppStudentCourseDetailRespVO detail = studentCourseMapper.selectCourseDetail(courseId, history);
        if (detail == null) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        detail.setArchived(history);
        // 填充进度（通过XML Mapper按课程统计）
        Map<String, Object> progress = studentCourseMapper.selectCourseProgress(studentId, courseId, history);
        if (progress != null) {
            int total = progress.get("totalHomework") != null ? ((Number) progress.get("totalHomework")).intValue() : 0;
            int completed = progress.get("completedHomework") != null ? ((Number) progress.get("completedHomework")).intValue() : 0;
            double avg = progress.get("avgScore") != null ? ((Number) progress.get("avgScore")).doubleValue() : 0.0;
            detail.setTotalHomework(total);
            detail.setCompletedHomework(completed);
            detail.setAvgScore(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        }
        // 填充资源列表
        detail.setResources(getCourseResources(courseId));
        detail.setHomeworkList(studentCourseMapper.selectCourseHomeworks(studentId, courseId, history));
        return detail;
    }

    private List<AppStudentCourseDetailRespVO.Resource> getCourseResources(Long courseId) {
        return courseResourceMapper.selectList(
                new LambdaQueryWrapper<EduCourseResourceDO>()
                        .eq(EduCourseResourceDO::getCourseId, courseId))
                .stream().map(this::toResource).collect(Collectors.toList());
    }

    private AppStudentCourseDetailRespVO.Resource toResource(EduCourseResourceDO row) {
        AppStudentCourseDetailRespVO.Resource resource = new AppStudentCourseDetailRespVO.Resource();
        resource.setId(row.getId());
        resource.setResourceName(row.getResourceName());
        resource.setFileType(row.getFileType());
        resource.setFileSize(row.getFileSize());
        return resource;
    }

    @Override
    public AppStudentUnlockStatusRespVO getUnlockStatus(Long studentId, Long courseId) {
        boolean history = !validateStudentCourseReadable(studentId, courseId);
        AppStudentUnlockStatusRespVO resp = new AppStudentUnlockStatusRespVO();
        resp.setLevels(levelUnlockService.getLevels(studentId, courseId, history));
        resp.setArchived(history);
        return resp;
    }

    public void validateLevelUnlocked(Long studentId, Long courseId, Integer level) {
        levelUnlockService.validateLevelUnlocked(studentId, courseId, level);
    }

    @Override
    public FileContentRespDTO getCourseResourceFile(Long studentId, Long resourceId) {
        validateStudentIdentity(studentId);
        EduCourseResourceDO resource = courseResourceMapper.selectById(resourceId);
        if (resource == null || (!studentCourseMapper.existsStudentCourse(studentId, resource.getCourseId())
                && !studentCourseMapper.existsStudentCourseHistory(studentId, resource.getCourseId()))) {
            throw exception(STUDENT_COURSE_RESOURCE_NOT_EXISTS);
        }
        // 只返回已登记平台文件的元数据，内容由控制器流式输出，避免大文件整体读入内存
        FileContentRespDTO file;
        try {
            file = fileApi.getFile(resource.getFileUrl());
        } catch (Exception ex) {
            throw exception(STUDENT_COURSE_RESOURCE_NOT_EXISTS);
        }
        if (StrUtil.isBlank(file.getName())) {
            file.setName(resource.getResourceName());
        }
        return file;
    }

    private void validateStudentIdentity(Long studentId) {
        AdminUserRespDTO user = adminUserApi.getUser(studentId);
        if (user == null || !Objects.equals(user.getUserType(), STUDENT_USER_TYPE)) {
            throw exception(STUDENT_IDENTITY_REQUIRED);
        }
    }

    /**
     * 校验学生能查看这门课。
     *
     * @return true 表示当前课程；false 表示往期课程（课程、班级或班课已归档，只能查看）
     */
    private boolean validateStudentCourseReadable(Long studentId, Long courseId) {
        validateStudentIdentity(studentId);
        if (studentCourseMapper.existsStudentCourse(studentId, courseId)) {
            return true;
        }
        if (studentCourseMapper.existsStudentCourseHistory(studentId, courseId)) {
            return false;
        }
        throw exception(STUDENT_COURSE_NOT_EXISTS);
    }

    private void fillCourseProgress(AppStudentCourseRespVO course, StudentCourseProgressRow progress) {
        course.setTotalHomework(progress == null ? 0 : progress.getTotalHomework());
        course.setCompletedHomework(progress == null || progress.getCompletedHomework() == null
                ? 0 : progress.getCompletedHomework());
        double avg = progress == null || progress.getAvgScore() == null ? 0.0 : progress.getAvgScore();
        course.setAvgScore(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
    }

}
