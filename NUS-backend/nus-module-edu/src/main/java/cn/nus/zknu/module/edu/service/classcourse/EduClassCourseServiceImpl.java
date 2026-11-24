package cn.nus.zknu.module.edu.service.classcourse;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.*;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.dept.DeptApi;
import org.springframework.dao.DuplicateKeyException;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import cn.nus.zknu.module.edu.util.EduIdCsvUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 班级课程关联 Service 实现类
 */
@Service
@Slf4j
public class EduClassCourseServiceImpl implements EduClassCourseService {

    @Resource
    private EduClassCourseMapper eduClassCourseMapper;
    @Resource
    private EduClassStudentMapper eduClassStudentMapper;
    @Resource
    private EduClassMapper eduClassMapper;
    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduHomeworkRecordMapper eduHomeworkRecordMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseAssistantService assistantService;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduSemesterOptionService semesterOptionService;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private EduPublicCourseBackfillService backfillService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindClassCourse(EduClassCourseBindReqVO bindReqVO) {
        boolean courseDimension = bindReqVO.getCourseId() != null && CollUtil.isNotEmpty(bindReqVO.getClassIds());
        boolean classDimension = bindReqVO.getClassId() != null && CollUtil.isNotEmpty(bindReqVO.getCourseIds());
        if (!courseDimension && !classDimension) {
            throw exception(CLASS_COURSE_BIND_PARAM_INVALID);
        }
        if (courseDimension) {
            validateBindCourse(bindReqVO.getCourseId());
        } else {
            bindReqVO.getCourseIds().forEach(this::validateBindCourse);
        }
        // 学期只能从已配置的学期里选：为空取当前学期，日期以学期配置为准，避免自由文本产生新学期名
        EduSemesterOptionRespVO semester = semesterOptionService.resolve(bindReqVO.getSemesterName());
        java.time.LocalDate startDate = semester.getSemesterStartDate() != null
                ? semester.getSemesterStartDate() : bindReqVO.getSemesterStartDate();
        java.time.LocalDate endDate = semester.getSemesterEndDate() != null
                ? semester.getSemesterEndDate() : bindReqVO.getSemesterEndDate();
        // 新接口：课程维度绑定（courseId + classIds）
        if (courseDimension) {
            if (dataScopeService.isPublicCourse(bindReqVO.getCourseId())) {
                claimResolved(bindReqVO.getCourseId(), bindReqVO.getClassIds(), semester);
                return;
            }
            for (Long classId : bindReqVO.getClassIds()) {
                bindOne(classId, bindReqVO.getCourseId(), semester.getSemesterName(), startDate, endDate,
                        bindReqVO.getAssistantTeacherIds());
            }
            return;
        }
        // 多课程认领先按 ID 加锁，专属课仍按请求顺序绑定，保留原有列表顺序。
        bindReqVO.getCourseIds().stream().filter(dataScopeService::isPublicCourse).distinct().sorted()
                .forEach(eduClassCourseMapper::lockPublicCourse);
        // 旧接口兼容：班级维度绑定（classId + courseIds）
        for (Long courseId : bindReqVO.getCourseIds()) {
            if (dataScopeService.isPublicCourse(courseId)) {
                claimResolved(courseId, List.of(bindReqVO.getClassId()), semester);
                continue;
            }
            bindOne(bindReqVO.getClassId(), courseId, semester.getSemesterName(), startDate, endDate,
                    bindReqVO.getAssistantTeacherIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindClassCourse(Long id) {
        EduClassCourseDO entity = eduClassCourseMapper.selectById(id);
        if (entity == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        if (dataScopeService.isPublicCourse(entity.getCourseId())) {
            eduClassCourseMapper.lockPublicCourse(entity.getCourseId());
            entity = eduClassCourseMapper.selectClaimById(id);
            if (entity == null) {
                throw exception(CLASS_COURSE_NOT_EXISTS);
            }
        }
        validateRelationManage(entity);
        EduClassCourseDO update = new EduClassCourseDO();
        update.setId(id);
        update.setStatus(1);
        eduClassCourseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreClassCourse(Long id) {
        EduClassCourseDO entity = eduClassCourseMapper.selectById(id);
        if (entity == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        if (dataScopeService.isPublicCourse(entity.getCourseId())) {
            eduClassCourseMapper.lockPublicCourse(entity.getCourseId());
            entity = eduClassCourseMapper.selectClaimById(id);
            if (entity == null) {
                throw exception(CLASS_COURSE_NOT_EXISTS);
            }
        }
        validateRelationManage(entity);
        restoreRelation(id);
    }

    private void restoreRelation(Long id) {
        EduClassCourseDO update = new EduClassCourseDO();
        update.setId(id);
        update.setStatus(0);
        eduClassCourseMapper.updateById(update);
    }

    @Override
    public List<EduClassCourseDO> listByClass(Long classId, boolean includeArchived) {
        dataScopeService.validateClassAccess(classId);
        EduClassDO clazz = eduClassMapper.selectById(classId);
        if (!includeArchived && clazz != null && Integer.valueOf(1).equals(clazz.getStatus())) {
            return List.of();
        }
        List<EduClassCourseDO> rows = eduClassCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getClassId, classId)
                .eq(!includeArchived, EduClassCourseDO::getStatus, 0)
                .inSql(!includeArchived, EduClassCourseDO::getCourseId,
                        "SELECT id FROM capacity_course WHERE deleted = 0 AND status = 0")
                .orderByDesc(EduClassCourseDO::getId));
        Map<Long, Boolean> all = new HashMap<>();
        return rows.stream().filter(row -> all.computeIfAbsent(row.getCourseId(), courseId ->
                        !dataScopeService.isPublicCourse(courseId) || dataScopeService.canViewAllPublic(courseId))
                || (getLoginUserId() != null && Objects.equals(row.getClaimTeacherId(), getLoginUserId())))
                .toList();
    }

    public List<EduClassCourseDO> listByClass(Long classId) {
        return listByClass(classId, false);
    }

    @Override
    public List<EduClassCourseDO> listByCourse(Long courseId, boolean includeArchived) {
        // 任务页只读场景同样按课程数据范围校验，避免仅凭功能权限读取他人课程的班级
        dataScopeService.validateCourseAccess(courseId);
        var course = capacityCourseApi.getCourse(courseId);
        if (!includeArchived && course != null && Integer.valueOf(1).equals(course.getStatus())) {
            return List.of();
        }
        boolean ownOnly = dataScopeService.isPublicCourse(courseId)
                && !dataScopeService.canViewAllPublic(courseId);
        if (ownOnly && getLoginUserId() == null) {
            return List.of();
        }
        return eduClassCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getCourseId, courseId)
                .eq(ownOnly, EduClassCourseDO::getClaimTeacherId, getLoginUserId())
                .eq(!includeArchived, EduClassCourseDO::getStatus, 0)
                .inSql(!includeArchived, EduClassCourseDO::getClassId,
                        "SELECT id FROM edu_class WHERE deleted = 0 AND status = 0")
                .orderByDesc(EduClassCourseDO::getId));
    }

    public List<EduClassCourseDO> listByCourse(Long courseId) {
        return listByCourse(courseId, false);
    }

    private void validateBindCourse(Long courseId) {
        if (dataScopeService.isPublicCourse(courseId)) {
            validateClaimCourse(courseId);
        } else {
            dataScopeService.validateCourseManage(courseId);
        }
    }

    private CapacityCourseRespDTO validateOnlineCourse(Long courseId) {
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (course == null || !course.isOnlinePublicCourse()) {
            throw exception(CLASS_COURSE_CLAIM_OFFLINE);
        }
        return course;
    }

    private void validateClaimCourse(Long courseId) {
        validateOnlineCourse(courseId);
        if (getLoginUserId() == null || (!dataScopeService.isGlobalAdmin() && !eduRoleApi.isCurrentUserTeacher())) {
            throw exception(CLASS_COURSE_CLAIM_TEACHER_REQUIRED);
        }
    }

    private void validateRelationManage(EduClassCourseDO row) {
        if (!dataScopeService.isPublicCourse(row.getCourseId())) {
            dataScopeService.validateCourseManage(row.getCourseId());
            return;
        }
        // 归档行和旧学期也按该行认领人授权，不能被其他学期同班的认领绕过。
        if (!dataScopeService.isGlobalAdmin() && !dataScopeService.isCourseApprover()
                && (getLoginUserId() == null || !Objects.equals(row.getClaimTeacherId(), getLoginUserId()))) {
            throw exception(FORBIDDEN);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimClassCourse(EduClassCourseClaimReqVO request) {
        validateClaimCourse(request.getCourseId());
        claimResolved(request.getCourseId(), request.getClassIds(), semesterOptionService.resolve(request.getSemesterName()));
    }

    private void claimResolved(Long courseId, List<Long> classIds, EduSemesterOptionRespVO option) {
        eduClassCourseMapper.lockPublicCourse(courseId);
        validateClaimCourse(courseId);
        String semester = StrUtil.trim(option.getSemesterName());
        Long teacherId = getLoginUserId();
        List<Long> newlyClaimed = new ArrayList<>();
        for (Long classId : new LinkedHashSet<>(classIds)) {
            EduClassDO clazz = eduClassMapper.selectById(classId);
            if (clazz == null) {
                throw exception(CLASS_NOT_EXISTS);
            }
            if (Integer.valueOf(1).equals(clazz.getStatus())) {
                throw exception(CLASS_ARCHIVED);
            }
            EduClassCourseDO row = eduClassCourseMapper.selectClaimRelation(courseId, classId, semester);
            if (row == null) {
                row = new EduClassCourseDO();
                row.setClassId(classId);
                row.setCourseId(courseId);
                row.setSemesterName(semester);
                row.setSemesterStartDate(option.getSemesterStartDate());
                row.setSemesterEndDate(option.getSemesterEndDate());
                row.setStatus(0);
                row.setBackfillPending(false);
                row.setCreator(EduClassCourseMapper.SYNC_OPERATOR);
                row.setUpdater(EduClassCourseMapper.SYNC_OPERATOR);
                row.setClaimTeacherId(teacherId);
                row.setClaimTime(LocalDateTime.now());
                try {
                    eduClassCourseMapper.insert(row);
                    newlyClaimed.add(classId);
                    continue;
                } catch (DuplicateKeyException ignored) {
                    // 兼容尚未使用课程锁的旧写入方；唯一键冲突后必须重新当前读。
                    row = eduClassCourseMapper.selectClaimRelation(courseId, classId, semester);
                }
            }
            if (row != null && Objects.equals(row.getClaimTeacherId(), teacherId)) {
                continue;
            }
            if (row != null && row.getClaimTeacherId() == null
                    && (Integer.valueOf(1).equals(row.getStatus())
                        ? eduClassCourseMapper.claimArchived(row.getId(), teacherId)
                        : eduClassCourseMapper.claim(row.getId(), teacherId)) == 1) {
                newlyClaimed.add(classId);
                continue;
            }
            row = eduClassCourseMapper.selectClaimRelation(courseId, classId, semester);
            Long owner = row == null ? null : row.getClaimTeacherId();
            var teacher = owner == null ? null : adminUserApi.getUser(owner);
            throw exception(CLASS_COURSE_CLAIM_TAKEN, StrUtil.blankToDefault(clazz.getClassName(), "未命名班级"),
                    teacher == null ? "其他" : StrUtil.blankToDefault(teacher.getNickname(), "其他"));
        }
        // 与认领在同一事务、同一课程锁下补发，失败时认领和任务一并回滚。
        Set<Long> newClassIds = Set.copyOf(newlyClaimed);
        for (Long classId : newlyClaimed) {
            backfillService.backfillForClaim(courseId, semester, teacherId, classId, newClassIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseClassCourse(Long id) {
        EduClassCourseDO row = eduClassCourseMapper.selectById(id);
        if (row == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        eduClassCourseMapper.lockPublicCourse(row.getCourseId());
        row = eduClassCourseMapper.selectClaimById(id);
        if (row == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        if (!dataScopeService.isPublicCourse(row.getCourseId())) {
            throw exception(CLASS_COURSE_CLAIM_OFFLINE);
        }
        validateRelationManage(row);
        if (row.getClaimTeacherId() != null) {
            eduClassCourseMapper.release(id, row.getClaimTeacherId());
        }
    }

    @Override
    public List<EduClassCourseClaimOptionRespVO> claimOptions(Long courseId, String semesterName) {
        validateOnlineCourse(courseId);
        String semester = StrUtil.trim(semesterOptionService.resolve(semesterName).getSemesterName());
        List<EduClassDO> classes = eduClassMapper.selectList(new LambdaQueryWrapper<EduClassDO>()
                .eq(EduClassDO::getStatus, 0));
        Map<Long, EduClassCourseDO> links = new HashMap<>();
        eduClassCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getCourseId, courseId).apply("TRIM(semester_name) = {0}", semester)
                .orderByAsc(EduClassCourseDO::getStatus).orderByDesc(EduClassCourseDO::getId))
                .forEach(row -> links.putIfAbsent(row.getClassId(), row));
        Set<Long> teacherIds = links.values().stream().map(EduClassCourseDO::getClaimTeacherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            adminUserApi.getUserList(teacherIds).forEach(user -> names.put(user.getId(), user.getNickname()));
        }
        Set<Long> deptIds = classes.stream().map(EduClassDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptApi.getDeptList(deptIds).forEach(dept -> depts.put(dept.getId(), dept.getName()));
        }
        List<EduClassCourseClaimOptionRespVO> result = new ArrayList<>();
        for (EduClassDO clazz : classes) {
            EduClassCourseDO row = links.get(clazz.getId());
            EduClassCourseClaimOptionRespVO item = new EduClassCourseClaimOptionRespVO();
            item.setClassId(clazz.getId());
            item.setClassName(clazz.getClassName());
            item.setGradeYear(clazz.getGradeYear());
            item.setDeptName(depts.get(clazz.getDeptId()));
            item.setClassCourseId(row == null ? null : row.getId());
            item.setClaimTeacherId(row == null ? null : row.getClaimTeacherId());
            item.setClaimTeacherName(names.get(item.getClaimTeacherId()));
            item.setState(item.getClaimTeacherId() == null ? "AVAILABLE"
                    : Objects.equals(item.getClaimTeacherId(), getLoginUserId()) ? "MINE" : "TAKEN");
            result.add(item);
        }
        Map<String, Integer> order = Map.of("AVAILABLE", 0, "MINE", 1, "TAKEN", 2);
        result.sort(Comparator.comparingInt((EduClassCourseClaimOptionRespVO item) -> order.get(item.getState()))
                .thenComparing(EduClassCourseClaimOptionRespVO::getClassName, Comparator.nullsFirst(String::compareTo)));
        return result;
    }

    private void bindOne(Long classId, Long courseId,
                         String semesterName,
                         java.time.LocalDate semesterStartDate,
                         java.time.LocalDate semesterEndDate,
                         String assistantTeacherIds) {
        EduClassDO clazz = eduClassMapper.selectById(classId);
        if (clazz == null) {
            throw exception(CLASS_NOT_EXISTS);
        }
        if (Integer.valueOf(1).equals(clazz.getStatus())) {
            throw exception(CLASS_ARCHIVED);
        }
        var course = capacityCourseApi.getCourse(courseId);
        if (course == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
        EduClassCourseDO existing = findExisting(classId, courseId, semesterName);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getStatus())) {
                restoreRelation(existing.getId());
            }
            return;
        }
        EduClassCourseDO entity = new EduClassCourseDO();
        entity.setClassId(classId);
        entity.setCourseId(courseId);
        entity.setSemesterName(StrUtil.trimToNull(semesterName));
        entity.setSemesterStartDate(semesterStartDate);
        entity.setSemesterEndDate(semesterEndDate);
        // 助教以 edu_class_course_assistant 为准，旧逗号串列保留但不再写入。
        List<Long> assistantIds = new ArrayList<>(EduIdCsvUtils.parseIds(assistantTeacherIds));
        entity.setStatus(0);
        eduClassCourseMapper.insert(entity);
        assistantService.replaceAssistants(entity.getId(), assistantIds);
        List<Long> studentIds = eduClassStudentMapper.selectListByClassId(classId).stream()
                .map(EduClassStudentDO::getStudentId)
                .toList();
        notifyAfterCommit(studentIds,
                Map.of("className", StrUtil.blankToDefault(clazz.getClassName(), "未命名班级"),
                        "courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程")), courseId);
    }

    /** 关联与助教全部提交后再通知，回调中的站内信写入使用新事务。 */
    private void notifyAfterCommit(List<Long> studentIds, Map<String, Object> params, Long courseId) {
        Runnable send = () -> {
            try {
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                template.executeWithoutResult(status -> businessNotifyService.sendToUsers(studentIds,
                        EduBusinessNotifyService.COURSE_ASSIGNED, params, "/student/course/detail/" + courseId));
            } catch (Exception ex) {
                log.error("[notifyAfterCommit][班课绑定通知发送失败 courseId={}]", courseId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private EduClassCourseDO findExisting(Long classId, Long courseId, String semesterName) {
        LambdaQueryWrapper<EduClassCourseDO> queryWrapper = new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getClassId, classId)
                .eq(EduClassCourseDO::getCourseId, courseId);
        if (StrUtil.isBlank(semesterName)) {
            queryWrapper.and(wrapper -> wrapper.isNull(EduClassCourseDO::getSemesterName)
                    .or().eq(EduClassCourseDO::getSemesterName, ""));
        } else {
            // 旧数据的学期名可能带首尾空格，按去空格后的名称匹配
            queryWrapper.apply("TRIM(semester_name) = {0}", StrUtil.trim(semesterName));
        }
        queryWrapper.orderByAsc(EduClassCourseDO::getStatus).orderByDesc(EduClassCourseDO::getId);
        return eduClassCourseMapper.selectList(queryWrapper).stream().findFirst().orElse(null);
    }

    // ========== P2-4 教学班运行摘要 ==========

    @Override
    public cn.nus.zknu.module.edu.controller.admin.classcourse.vo.ClassCourseProcessSummaryRespVO getProcessSummary(Long classCourseId) {
        EduClassCourseDO binding = eduClassCourseMapper.selectById(classCourseId);
        if (binding == null) {
            throw new IllegalArgumentException("班课关联不存在");
        }
        var resp = new cn.nus.zknu.module.edu.controller.admin.classcourse.vo.ClassCourseProcessSummaryRespVO();
        // 在班学生数
        resp.setStudentCount(eduClassStudentMapper.selectCount(
                new LambdaQueryWrapperX<EduClassStudentDO>()
                        .eq(EduClassStudentDO::getClassId, binding.getClassId())));
        // 任课教师数（含助教）
        resp.setTeacherCount(1L);
        resp.setBindingStatus(binding.getStatus());
        // 作业统计
        List<EduHomeworkDO> homeworks = eduHomeworkMapper.selectList(
                new LambdaQueryWrapperX<EduHomeworkDO>()
                        .eq(EduHomeworkDO::getCourseId, binding.getCourseId())
                        .eq(EduHomeworkDO::getClassId, binding.getClassId())
                        .in(EduHomeworkDO::getStatus, 1, 2)
                        .orderByDesc(EduHomeworkDO::getId));
        resp.setHomeworkCount((long) homeworks.size());
        List<cn.nus.zknu.module.edu.controller.admin.classcourse.vo.ClassCourseProcessSummaryRespVO.AssignmentStat> assignmentStats = new java.util.ArrayList<>();
        long totalSubmitted = 0, totalPending = 0;
        double scoreSum = 0;
        int scoreCount = 0;
        for (EduHomeworkDO hw : homeworks) {
            var stat = new cn.nus.zknu.module.edu.controller.admin.classcourse.vo.ClassCourseProcessSummaryRespVO.AssignmentStat();
            stat.setHomeworkId(hw.getId());
            stat.setHomeworkName(hw.getHomeworkName());
            stat.setDeadline(hw.getDeadline() != null ? hw.getDeadline().toString() : null);
            stat.setStatus(hw.getStatus());
            long expected = resp.getStudentCount() != null ? resp.getStudentCount() : 0;
            long submitted = eduHomeworkRecordMapper.selectCount(
                    new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                            .eq(EduHomeworkRecordDO::getHomeworkId, hw.getId())
                            .in(EduHomeworkRecordDO::getStatus, 1, 2, 4));
            long graded = eduHomeworkRecordMapper.selectCount(
                    new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                            .eq(EduHomeworkRecordDO::getHomeworkId, hw.getId())
                            .isNotNull(EduHomeworkRecordDO::getScore));
            // 平均分
            List<EduHomeworkRecordDO> scoredRecords = eduHomeworkRecordMapper.selectList(
                    new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                            .eq(EduHomeworkRecordDO::getHomeworkId, hw.getId())
                            .isNotNull(EduHomeworkRecordDO::getScore));
            double avgScore = 0;
            if (!scoredRecords.isEmpty()) {
                avgScore = scoredRecords.stream().mapToInt(EduHomeworkRecordDO::getScore).average().orElse(0);
            }
            stat.setExpectedCount(expected);
            stat.setSubmittedCount(submitted);
            stat.setGradedCount(graded);
            stat.setAverageScore(avgScore);
            stat.setCompletionRate(expected > 0 ? Math.round(submitted * 10000.0 / expected) / 100.0 : 0);
            assignmentStats.add(stat);
            totalSubmitted += submitted;
            totalPending += (submitted - graded);
            if (!scoredRecords.isEmpty()) {
                scoreSum += avgScore * scoredRecords.size();
                scoreCount += scoredRecords.size();
            }
        }
        resp.setAssignments(assignmentStats);
        resp.setSubmittedCount(totalSubmitted);
        resp.setPendingGradingCount(Math.max(0, totalPending));
        resp.setAverageScore(scoreCount > 0 ? Math.round(scoreSum / scoreCount * 100) / 100.0 : null);
        return resp;
    }

}
