package cn.nus.zknu.module.edu.service.coursemanage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.framework.EduRequestCache;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.service.homework.HomeworkWriteAccess;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageClassDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageTeacherMessageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper.HomeworkStat;
import lombok.extern.slf4j.Slf4j;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_STUDENT_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_NOT_EXISTS;

@Service
@Slf4j
public class EduCourseManageServiceImpl implements EduCourseManageService {

    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseMapper eduClassCourseMapper;
    @Resource
    private EduClassMapper eduClassMapper;
    @Resource
    private EduClassStudentMapper eduClassStudentMapper;
    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduHomeworkAssigneeMapper eduHomeworkAssigneeMapper;
    @Resource
    private EduCourseStatMapper courseStatMapper;
    @Resource
    private EduCourseResourceMapper eduCourseResourceMapper;
    @Resource
    private EduMessageMapper eduMessageMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private EduClassCourseAssistantService assistantService;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduMetricPolicy metricPolicy;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public CourseManageDetailRespVO getCourseDetail(Long courseId) {
        CapacityCourseRespDTO course = validateCourseExists(courseId);
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        List<EduClassCourseDO> classCourseList = eduClassCourseMapper.selectList(
                new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getCourseId, courseId)
                        .orderByDesc(EduClassCourseDO::getId));
        boolean claimMode = dataScopeService.isPublicCourse(courseId);
        if (claimMode && loginUserId != null) {
            // 详情已经批量读取全部关联，复用同一请求内的在用认领，避免任务逐行重复查库。
            EduRequestCache.get("claimedRelations", List.of(courseId, loginUserId),
                    () -> classCourseList.stream().filter(item -> Objects.equals(item.getStatus(), 0)
                            && Objects.equals(item.getClaimTeacherId(), loginUserId)).toList());
        }
        boolean viewAllReadOnly = claimMode && dataScopeService.canViewAllPublic(courseId);
        List<EduClassCourseDO> visibleClassCourses = claimMode && !viewAllReadOnly
                ? classCourseList.stream().filter(item -> loginUserId != null
                        && Objects.equals(item.getClaimTeacherId(), loginUserId)).toList()
                : classCourseList;
        // 旧布置者放弃认领后仍能看到任务，保留原关联用于任务班级名和空学期回退。
        Set<Long> classIds = classCourseList.stream()
                .map(EduClassCourseDO::getClassId)
                .collect(Collectors.toSet());
        Map<Long, EduClassDO> classMap = getClassMap(classIds);
        Map<Long, Integer> classStudentCountMap = getClassStudentCountMap(classIds);

        // 课程团队取自 capacity_course_member，班课助教取自 edu_class_course_assistant
        Map<Long, List<Long>> assistantsByClassCourse = assistantService.getTeacherIdsMap(
                classCourseList.stream().map(EduClassCourseDO::getId).toList());
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(course.getTeacherId());
        Set<Long> courseMemberIds = new HashSet<>(capacityCourseApi.getCourseMemberUserIds(course.getId()));
        userIds.addAll(courseMemberIds);
        assistantsByClassCourse.values().forEach(userIds::addAll);
        if (claimMode) {
            visibleClassCourses.stream().map(EduClassCourseDO::getClaimTeacherId)
                    .filter(Objects::nonNull).forEach(userIds::add);
        }
        List<EduHomeworkDO> homeworkList = eduHomeworkMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkDO>()
                        .eq(EduHomeworkDO::getCourseId, courseId)
                        .orderByDesc(EduHomeworkDO::getId));
        homeworkList = filterVisibleHomeworks(homeworkList, course, loginUserId);
        // 任务布置者可能已离开课程团队，仍需与班级负责人共用一次姓名查询。
        homeworkList.stream().map(EduHomeworkDO::getTeacherId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, AdminUserDO> userMap = getUserMap(userIds);
        // 旧待发布任务可能遗留分配或提交数据，不能据此展示发布后的统计。
        List<Long> homeworkIds = homeworkList.stream()
                .filter(homework -> !Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.DRAFT))
                .map(EduHomeworkDO::getId).toList();
        Map<Long, Long> assigneeCounts = homeworkIds.isEmpty() ? Map.of() : courseStatMapper
                .selectAssigneeCounts(homeworkIds).stream().collect(Collectors.toMap(
                        EduCourseStatMapper.HomeworkCount::getHomeworkId,
                        EduCourseStatMapper.HomeworkCount::getStudentCount));
        Map<Long, HomeworkStat> recordStats = homeworkIds.isEmpty() ? Map.of() : courseStatMapper
                .selectRecordStats(homeworkIds).stream().collect(Collectors.toMap(
                        HomeworkStat::getHomeworkId, Function.identity()));
        Map<Long, EduClassCourseDO> firstClassCourseByClass = classCourseList.stream().collect(
                Collectors.toMap(EduClassCourseDO::getClassId, Function.identity(), (a, b) -> a));

        List<EduCourseResourceDO> resourceList = eduCourseResourceMapper.selectList(
                new LambdaQueryWrapper<EduCourseResourceDO>()
                        .eq(EduCourseResourceDO::getCourseId, courseId)
                        .orderByDesc(EduCourseResourceDO::getId));

        CourseManageDetailRespVO respVO = new CourseManageDetailRespVO();
        respVO.setCourseId(course.getId());
        respVO.setCanManage(dataScopeService.canManageCourse(courseId));
        respVO.setClaimMode(claimMode);
        respVO.setCanClaim(dataScopeService.canClaimPublicCourse(course));
        respVO.setViewAllReadOnly(viewAllReadOnly);
        respVO.setCourseName(course.getCourseName());
        respVO.setCourseIntroduction(course.getCourseIntroduction());
        respVO.setCourseImageUrl(course.getCourseImageUrl());
        respVO.setIndicatorId(course.getIndicatorId());
        respVO.setIndicatorName(course.getIndicatorName());
        respVO.setTeacherId(course.getTeacherId());
        respVO.setTeacherName(resolveUserName(userMap, course.getTeacherId(), "-"));
        respVO.setTrainingCategory(resolveTrainingCategory(course.getIsPublicCourse()));

        boolean globalAdmin = dataScopeService.isGlobalAdmin();
        respVO.setClasses(visibleClassCourses.stream().map(item -> {
            CourseManageDetailRespVO.CourseClassRespVO classVO = new CourseManageDetailRespVO.CourseClassRespVO();
            classVO.setClassCourseId(item.getId());
            Long ownerId = resolveClassOwnerId(item, course, courseMemberIds, claimMode);
            classVO.setOwnerId(ownerId);
            classVO.setOwnerName(claimMode && ownerId == null ? "未认领"
                    : resolveUserName(userMap, ownerId, "-"));
            classVO.setCanRelease(claimMode && dataScopeService.canReleasePublicClaim(item.getClaimTeacherId()));
            classVO.setClaimTeacherId(item.getClaimTeacherId());
            classVO.setClaimTeacherName(item.getClaimTeacherId() == null ? null
                    : resolveUserName(userMap, item.getClaimTeacherId(), "-"));
            // 已封存的自己的认领行也可恢复，不能使用只查询在用关联的判断。
            classVO.setCanOperate(claimMode ? globalAdmin || (loginUserId != null
                    && Objects.equals(item.getClaimTeacherId(), loginUserId)) : respVO.getCanManage());
            classVO.setClassId(item.getClassId());
            classVO.setClassName(resolveClassName(classMap, item.getClassId()));
            classVO.setSemesterName(item.getSemesterName());
            classVO.setSemesterStartDate(item.getSemesterStartDate());
            classVO.setSemesterEndDate(item.getSemesterEndDate());
            classVO.setStatus(resolveSemesterStatus(item.getSemesterStartDate(), item.getSemesterEndDate()));
            EduClassDO clazz = classMap.get(item.getClassId());
            classVO.setArchived(Objects.equals(item.getStatus(), 1)
                    || (clazz != null && Objects.equals(clazz.getStatus(), 1)));
            classVO.setStudentCount(classStudentCountMap.getOrDefault(item.getClassId(), 0));
            List<Long> assistantIds = assistantsByClassCourse.getOrDefault(item.getId(), List.of());
            classVO.setAssistantTeacherIds(EduClassCourseAssistantService.toCsv(assistantIds));
            classVO.setAssistantTeacherNames(resolveAssistantNames(assistantIds, userMap));
            classVO.setCreateTime(item.getCreateTime());
            return classVO;
        }).toList());

        // 同一响应复用功能权限结果，任务数增加时不能逐行加载权限或任务。
        boolean hasOwnedTask = homeworkList.stream().anyMatch(homework -> HomeworkWriteAccess.isOwner(homework, loginUserId));
        boolean canUpdate = hasOwnedTask && securityFrameworkService.hasPermission("edu:homework:update");
        boolean canDeleteOrRestore = hasOwnedTask && securityFrameworkService.hasPermission("edu:homework:delete");
        boolean canCorrect = hasOwnedTask && securityFrameworkService.hasPermission("edu:homework-record:correct");
        Map<Long, List<EduClassCourseDO>> relationsByClass = classCourseList.stream()
                .collect(Collectors.groupingBy(EduClassCourseDO::getClassId));
        respVO.setTasks(homeworkList.stream().map(homework -> {
            CourseManageDetailRespVO.CourseTaskRespVO taskVO = new CourseManageDetailRespVO.CourseTaskRespVO();
            taskVO.setHomeworkId(homework.getId());
            taskVO.setTeacherId(homework.getTeacherId());
            taskVO.setTeacherName(resolveUserName(userMap, homework.getTeacherId(), "-"));
            taskVO.setStatus(homework.getStatus());
            boolean owner = HomeworkWriteAccess.isOwner(homework, loginUserId);
            boolean canEdit = canUpdate && owner && !Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED)
                    && canUpdateTaskScope(homework, course, classMap, relationsByClass);
            taskVO.setCanManage(owner && (canDeleteOrRestore || canEdit));
            taskVO.setCanGrade(canCorrect && HomeworkWriteAccess.canGrade(homework, loginUserId));
            taskVO.setHomeworkName(homework.getHomeworkName());
            taskVO.setClassId(homework.getClassId());
            taskVO.setClassName(resolveClassName(classMap, homework.getClassId()));
            String semesterName = homework.getSemesterName();
            if (StrUtil.isBlank(semesterName)) {
                EduClassCourseDO relation = firstClassCourseByClass.get(homework.getClassId());
                semesterName = relation == null ? null : relation.getSemesterName();
            }
            taskVO.setSemesterName(semesterName);
            taskVO.setDeadline(homework.getDeadline());
            taskVO.setStudentCount(assigneeCounts.getOrDefault(homework.getId(), 0L).intValue());
            HomeworkStat stat = recordStats.getOrDefault(homework.getId(), new HomeworkStat());
            taskVO.setSubmittedCount(stat.getSubmittedCount());
            taskVO.setCorrectedCount(stat.getCorrectedCount());
            taskVO.setAvgScore(stat.averageScore() == null ? 0D : roundOneDecimal(stat.averageScore()));
            return taskVO;
        }).toList());

        respVO.setResources(resourceList.stream().map(resource -> {
            CourseManageDetailRespVO.CourseResourceRespVO resourceVO = new CourseManageDetailRespVO.CourseResourceRespVO();
            resourceVO.setId(resource.getId());
            resourceVO.setResourceName(resource.getResourceName());
            resourceVO.setFileUrl(resource.getFileUrl());
            resourceVO.setFileType(resource.getFileType());
            resourceVO.setFileSize(resource.getFileSize());
            resourceVO.setCreateTime(resource.getCreateTime());
            return resourceVO;
        }).toList());
        return respVO;
    }

    /** 修改要求上级范围在用；封存/恢复仅校验布置者，不能把修改限制套给它们。 */
    private boolean canUpdateTaskScope(EduHomeworkDO homework, CapacityCourseRespDTO course,
                                       Map<Long, EduClassDO> classes,
                                       Map<Long, List<EduClassCourseDO>> relationsByClass) {
        EduClassDO clazz = classes.get(homework.getClassId());
        if (Objects.equals(course.getStatus(), 1) || (clazz != null && Objects.equals(clazz.getStatus(), 1))) {
            return false;
        }
        if (dataScopeService.isPublicCourse(course.getId())
                && !dataScopeService.canAssignToPublicClass(course.getId(), homework.getClassId())) {
            return false;
        }
        return relationsByClass.getOrDefault(homework.getClassId(), List.of()).stream().anyMatch(relation ->
                Objects.equals(relation.getStatus(), 0) && (StrUtil.isBlank(homework.getSemesterName())
                        || StrUtil.isBlank(relation.getSemesterName())
                        || StrUtil.equals(StrUtil.trim(relation.getSemesterName()), StrUtil.trim(homework.getSemesterName()))));
    }

    @Override
    public CourseManageClassDetailRespVO getClassDetail(Long classCourseId) {
        EduClassCourseDO classCourse = validateClassCourseExists(classCourseId);
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(classCourse.getCourseId());
        if (course == null) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        EduRequestCache.get("course", course.getId(), () -> course);
        boolean claimMode = dataScopeService.isPublicCourse(course.getId());
        if (claimMode) {
            // 本人封存认领行仍展示历史班级详情，其他用户继续走原班课查看范围。
            if (SecurityFrameworkUtils.getLoginUserId() == null
                    || !Objects.equals(classCourse.getClaimTeacherId(), SecurityFrameworkUtils.getLoginUserId())) {
                dataScopeService.validateClassCourseAccess(classCourse.getClassId(), course.getId());
            }
        } else {
            validateCourseAccess(course);
        }
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();

        List<EduClassStudentDO> classStudents = eduClassStudentMapper.selectListByClassId(classCourse.getClassId());
        Set<Long> studentIds = classStudents.stream().map(EduClassStudentDO::getStudentId).collect(Collectors.toSet());
        Set<Long> userIds = new LinkedHashSet<>(studentIds);
        List<Long> classAssistantIds = assistantService.getTeacherIds(classCourse.getId());
        userIds.add(course.getTeacherId());
        userIds.addAll(capacityCourseApi.getCourseMemberUserIds(course.getId()));
        userIds.addAll(classAssistantIds);
        if (claimMode) {
            userIds.add(classCourse.getClaimTeacherId());
        }
        Map<Long, AdminUserDO> userMap = getUserMap(userIds);

        List<EduHomeworkDO> homeworkList = buildClassHomeworkList(classCourse, course, loginUserId);
        List<Long> homeworkIds = homeworkList.stream().map(EduHomeworkDO::getId).toList();
        Map<Long, Set<Long>> assignedHomeworkByStudent = new HashMap<>();
        eduHomeworkAssigneeMapper.selectListByHomeworkIds(homeworkIds).forEach(assignee ->
                assignedHomeworkByStudent.computeIfAbsent(assignee.getStudentId(), ignored -> new HashSet<>())
                        .add(assignee.getHomeworkId()));
        Map<Long, List<EduHomeworkRecordDO>> recordByHomeworkId = homeworkIds.isEmpty() ? Map.of()
                : courseStatMapper.selectAssignedRecords(homeworkIds).stream()
                        .collect(Collectors.groupingBy(EduHomeworkRecordDO::getHomeworkId));
        Map<Long, List<EduHomeworkRecordDO>> recordByStudentId = getRecordByStudentId(recordByHomeworkId.values().stream()
                .flatMap(Collection::stream).toList());
        EduClassDO clazz = eduClassMapper.selectById(classCourse.getClassId());

        CourseManageClassDetailRespVO respVO = new CourseManageClassDetailRespVO();
        respVO.setClassCourseId(classCourse.getId());
        respVO.setCourseId(classCourse.getCourseId());
        respVO.setCourseName(course.getCourseName());
        respVO.setIndicatorName(course.getIndicatorName());
        Long teacherId = claimMode && classCourse.getClaimTeacherId() != null
                ? classCourse.getClaimTeacherId() : course.getTeacherId();
        respVO.setTeacherName(resolveUserName(userMap, teacherId, "-"));
        respVO.setCanOperate(claimMode
                ? Objects.equals(classCourse.getStatus(), 0)
                    && dataScopeService.canAssignToPublicClass(course.getId(), classCourse.getClassId())
                : true);
        respVO.setClassId(classCourse.getClassId());
        respVO.setClassName(clazz == null ? "-" : clazz.getClassName());
        respVO.setSemesterName(classCourse.getSemesterName());
        respVO.setSemesterStartDate(classCourse.getSemesterStartDate());
        respVO.setSemesterEndDate(classCourse.getSemesterEndDate());
        respVO.setClassStatus(resolveSemesterStatus(classCourse.getSemesterStartDate(), classCourse.getSemesterEndDate()));
        respVO.setStudentCount(classStudents.size());
        respVO.setAssistantTeacherNames(resolveAssistantNames(classAssistantIds, userMap));

        respVO.setTasks(homeworkList.stream().map(homework -> {
            List<EduHomeworkRecordDO> records = recordByHomeworkId.getOrDefault(homework.getId(), Collections.emptyList());
            CourseManageClassDetailRespVO.ClassTaskRespVO taskVO = new CourseManageClassDetailRespVO.ClassTaskRespVO();
            taskVO.setHomeworkId(homework.getId());
            taskVO.setHomeworkName(homework.getHomeworkName());
            taskVO.setPublishTime(homework.getCreateTime());
            taskVO.setDeadline(homework.getDeadline());
            taskVO.setSubmittedCount((int) records.stream().filter(item -> item.getStatus() != null && item.getStatus() >= EduHomeworkRecordStatusEnum.SUBMITTED).count());
            taskVO.setCorrectedCount((int) records.stream().filter(item -> Objects.equals(item.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED)).count());
            taskVO.setAvgScore(calcAvg(records.stream().map(EduHomeworkRecordDO::getScore).filter(Objects::nonNull).map(Integer::doubleValue).toList()));
            return taskVO;
        }).toList());

        respVO.setMembers(classStudents.stream().map(item -> {
            AdminUserDO user = userMap.get(item.getStudentId());
            List<EduHomeworkRecordDO> studentRecords = recordByStudentId.getOrDefault(item.getStudentId(), Collections.emptyList());
            Set<Long> submittedHomeworkIds = studentRecords.stream()
                    .filter(record -> record.getStatus() != null && record.getStatus() >= EduHomeworkRecordStatusEnum.SUBMITTED)
                    .map(EduHomeworkRecordDO::getHomeworkId)
                    .collect(Collectors.toSet());
            List<Double> scores = studentRecords.stream()
                    .map(EduHomeworkRecordDO::getScore)
                    .filter(Objects::nonNull)
                    .map(Integer::doubleValue)
                    .toList();
            CourseManageClassDetailRespVO.ClassMemberRespVO memberVO = new CourseManageClassDetailRespVO.ClassMemberRespVO();
            memberVO.setStudentId(item.getStudentId());
            memberVO.setStudentNo(user == null ? "-" : user.getUsername());
            memberVO.setStudentName(user == null ? ("学生" + item.getStudentId()) : user.getNickname());
            memberVO.setAvatar(user == null ? null : user.getAvatar());
            memberVO.setFinishedTaskCount(submittedHomeworkIds.size());
            memberVO.setTotalTaskCount(assignedHomeworkByStudent.getOrDefault(item.getStudentId(), Set.of()).size());
            memberVO.setAvgScore(calcAvg(scores));
            return memberVO;
        }).toList());

        respVO.setScoreAnalysis(buildScoreAnalysis(respVO.getMembers(), homeworkList, recordByHomeworkId));
        respVO.setMessages(buildClassMessages(classCourse, course, userMap));
        return respVO;
    }

    @Override
    public void sendTeacherMessage(CourseManageTeacherMessageReqVO reqVO) {
        CapacityCourseRespDTO course = validateTeacherMessageTarget(reqVO);
        EduMessageDO messageDO = new EduMessageDO();
        messageDO.setClassId(reqVO.getClassId());
        messageDO.setCourseId(reqVO.getCourseId());
        messageDO.setStudentId(reqVO.getStudentId());
        messageDO.setContent(StrUtil.trim(reqVO.getContent()));
        messageDO.setType(1);
        messageDO.setStatus(0);
        eduMessageMapper.insert(messageDO);
        businessNotifyService.sendToUser(reqVO.getStudentId(), EduBusinessNotifyService.STUDENT_MESSAGE_REPLY,
                Map.of("courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程"),
                        "content", StrUtil.blankToDefault(StrUtil.trim(reqVO.getContent()), "无内容")),
                "/student/course/detail/" + reqVO.getCourseId());
    }

    /**
     * 只允许管理员、课程教师或助教，给已绑定该课程的班级中的学生留言。
     */
    private CapacityCourseRespDTO validateTeacherMessageTarget(CourseManageTeacherMessageReqVO reqVO) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(reqVO.getCourseId());
        if (loginUserId == null || course == null) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        List<EduClassCourseDO> classCourses = eduClassCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getCourseId, reqVO.getCourseId())
                .eq(EduClassCourseDO::getClassId, reqVO.getClassId()));
        if (CollUtil.isEmpty(classCourses)) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        boolean assistant = assistantService.isAssistant(classCourses.stream().map(EduClassCourseDO::getId).toList(),
                loginUserId);
        boolean manager = dataScopeService.isPublicCourse(course.getId())
                ? dataScopeService.isGlobalAdmin()
                    || dataScopeService.isClaimedByCurrentUser(course.getId(), reqVO.getClassId()) || assistant
                : canApprovePublicCourse(loginUserId)
                || Objects.equals(course.getTeacherId(), loginUserId)
                || capacityCourseApi.isCourseMember(course.getId(), loginUserId)
                || assistant;
        if (!manager) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        if (CollUtil.isEmpty(eduClassStudentMapper.selectListByClassIdAndStudentIds(
                reqVO.getClassId(), List.of(reqVO.getStudentId())))) {
            throw exception(CLASS_STUDENT_NOT_EXISTS);
        }
        return course;
    }

    private CapacityCourseRespDTO validateCourseExists(Long courseId) {
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (course == null) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        EduRequestCache.get("course", courseId, () -> course);
        validateCourseAccess(course);
        return course;
    }

    private EduClassCourseDO validateClassCourseExists(Long classCourseId) {
        EduClassCourseDO classCourse = eduClassCourseMapper.selectById(classCourseId);
        if (classCourse == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        return classCourse;
    }

    /**
     * 聚合详情页与课程列表共享同一套可见性规则，避免知道 courseId 后直接绕过前端分组。
     */
    private void validateCourseAccess(CapacityCourseRespDTO course) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (loginUserId == null) {
            throw exception(STUDENT_COURSE_NOT_EXISTS);
        }
        if (canViewAllHomeworks(course, loginUserId)) {
            return;
        }
        if (dataScopeService.isOnlinePublicCourseForTeacher(course.getId())) {
            return;
        }
        throw exception(STUDENT_COURSE_NOT_EXISTS);
    }

    /** 与 capacity 课程管理一致：持有公共课审批权限的人视为课程管理员，可查看全部课程 */
    private boolean canApprovePublicCourse(Long userId) {
        return capacityCourseApi.isCourseAdmin(userId);
    }

    private Map<Long, EduClassDO> getClassMap(Set<Long> classIds) {
        if (CollUtil.isEmpty(classIds)) {
            return Collections.emptyMap();
        }
        return eduClassMapper.selectByIds(classIds).stream()
                .collect(Collectors.toMap(EduClassDO::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Integer> getClassStudentCountMap(Set<Long> classIds) {
        if (CollUtil.isEmpty(classIds)) {
            return Collections.emptyMap();
        }
        return courseStatMapper.selectClassStudentCounts(classIds).stream().collect(Collectors.toMap(
                EduCourseStatMapper.ClassCount::getClassId, EduCourseStatMapper.ClassCount::getStudentCount));
    }

    private Map<Long, AdminUserDO> getUserMap(Set<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        Set<Long> filteredIds = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (CollUtil.isEmpty(filteredIds)) {
            return Collections.emptyMap();
        }
        return adminUserMapper.selectByIds(filteredIds).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, Function.identity(), (a, b) -> a));
    }

    private boolean canViewAllHomeworks(CapacityCourseRespDTO course, Long loginUserId) {
        if (dataScopeService.isPublicCourse(course.getId())) {
            return loginUserId != null && dataScopeService.canViewAllPublic(course.getId());
        }
        return loginUserId != null && (dataScopeService.isGlobalAdmin()
                || canApprovePublicCourse(loginUserId)
                || Objects.equals(course.getTeacherId(), loginUserId)
                || capacityCourseApi.isCourseMember(course.getId(), loginUserId));
    }

    private List<EduHomeworkDO> buildClassHomeworkList(EduClassCourseDO classCourse,
                                                      CapacityCourseRespDTO course, Long loginUserId) {
        LambdaQueryWrapper<EduHomeworkDO> wrapper = new LambdaQueryWrapper<EduHomeworkDO>()
                .eq(EduHomeworkDO::getCourseId, classCourse.getCourseId())
                .eq(EduHomeworkDO::getClassId, classCourse.getClassId())
                .ne(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.DRAFT)
                .orderByDesc(EduHomeworkDO::getId);
        if (StrUtil.isNotBlank(classCourse.getSemesterName())) {
            wrapper.and(item -> item.eq(EduHomeworkDO::getSemesterName, classCourse.getSemesterName())
                    .or().isNull(EduHomeworkDO::getSemesterName)
                    .or().eq(EduHomeworkDO::getSemesterName, ""));
        }
        return filterVisibleHomeworks(eduHomeworkMapper.selectList(wrapper), course, loginUserId);
    }

    private List<EduHomeworkDO> filterVisibleHomeworks(List<EduHomeworkDO> homeworks,
                                                     CapacityCourseRespDTO course, Long loginUserId) {
        if (CollUtil.isEmpty(homeworks) || canViewAllHomeworks(course, loginUserId)) {
            return homeworks;
        }
        return homeworks.stream()
                .filter(homework -> Objects.equals(homework.getTeacherId(), loginUserId))
                .toList();
    }

    private Map<Long, List<EduHomeworkRecordDO>> getRecordByStudentId(List<EduHomeworkRecordDO> recordList) {
        if (CollUtil.isEmpty(recordList)) {
            return Collections.emptyMap();
        }
        return recordList.stream().collect(Collectors.groupingBy(EduHomeworkRecordDO::getStudentId));
    }

    private CourseManageClassDetailRespVO.ScoreAnalysisRespVO buildScoreAnalysis(
            List<CourseManageClassDetailRespVO.ClassMemberRespVO> members,
            List<EduHomeworkDO> homeworks, Map<Long, List<EduHomeworkRecordDO>> recordsByHomework) {
        List<Double> validScores = members.stream()
                .map(CourseManageClassDetailRespVO.ClassMemberRespVO::getAvgScore)
                .filter(Objects::nonNull)
                .filter(score -> score > 0)
                .toList();
        CourseManageClassDetailRespVO.ScoreAnalysisRespVO analysis = new CourseManageClassDetailRespVO.ScoreAnalysisRespVO();
        if (CollUtil.isEmpty(validScores)) {
            analysis.setAvgScore(0D);
            analysis.setHighestScore(0D);
            analysis.setLowestScore(0D);
            analysis.setPassRate(0D);
            analysis.setScoreRanges(buildScoreRange(homeworks, recordsByHomework,
                    getPassScores(homeworks, recordsByHomework), null));
            return analysis;
        }
        analysis.setAvgScore(calcAvg(validScores));
        analysis.setHighestScore(roundOneDecimal(validScores.stream().max(Double::compareTo).orElse(0D)));
        analysis.setLowestScore(roundOneDecimal(validScores.stream().min(Double::compareTo).orElse(0D)));
        Map<Long, Integer> passScores = getPassScores(homeworks, recordsByHomework);
        EduMetricPolicy.ScoreRatios ratios = passScores.isEmpty() ? null : metricPolicy.scoreRatios();
        long evaluated = 0;
        long passCount = 0;
        for (EduHomeworkDO homework : homeworks) {
            for (EduHomeworkRecordDO record : recordsByHomework.getOrDefault(homework.getId(), List.of())) {
                if (!Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED) || record.getScore() == null) continue;
                evaluated++;
                if (record.getScore() >= passScores.get(homework.getId())) passCount++;
            }
        }
        analysis.setPassRate(evaluated == 0 ? 0D : roundOneDecimal(passCount * 100D / evaluated));
        analysis.setScoreRanges(buildScoreRange(homeworks, recordsByHomework, passScores, ratios));
        return analysis;
    }

    /** 同一响应使用同一份百分制配置，避免随成绩条数反复查询参数表。 */
    private Map<Long, Integer> getPassScores(List<EduHomeworkDO> homeworks,
                                          Map<Long, List<EduHomeworkRecordDO>> recordsByHomework) {
        List<EduHomeworkDO> evaluated = homeworks.stream().filter(homework ->
                recordsByHomework.getOrDefault(homework.getId(), List.of()).stream().anyMatch(record ->
                        Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED)
                                && record.getScore() != null)).toList();
        return metricPolicy.passScores(evaluated);
    }

    private List<CourseManageClassDetailRespVO.ScoreRangeRespVO> buildScoreRange(
            List<EduHomeworkDO> homeworks, Map<Long, List<EduHomeworkRecordDO>> recordsByHomework,
            Map<Long, Integer> passScores, EduMetricPolicy.ScoreRatios ratios) {
        Map<EduMetricPolicy.ScoreBand, Integer> counts = new EnumMap<>(EduMetricPolicy.ScoreBand.class);
        int total = 0;
        if (!passScores.isEmpty() && ratios == null) ratios = metricPolicy.scoreRatios();
        for (EduHomeworkDO homework : homeworks) {
            for (EduHomeworkRecordDO record : recordsByHomework.getOrDefault(homework.getId(), List.of())) {
                if (!Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED) || record.getScore() == null) continue;
                int passScore = passScores.get(homework.getId());
                EduMetricPolicy.ScoreBand band = metricPolicy.scoreBand(record.getScore(), passScore,
                        homework.getScore(), ratios);
                counts.merge(band, 1, Integer::sum);
                total++;
            }
        }
        final int denominator = total;
        return List.of(EduMetricPolicy.ScoreBand.EXCELLENT, EduMetricPolicy.ScoreBand.GOOD,
                EduMetricPolicy.ScoreBand.QUALIFIED, EduMetricPolicy.ScoreBand.UNQUALIFIED).stream().map(band -> {
            int count = counts.getOrDefault(band, 0);
            CourseManageClassDetailRespVO.ScoreRangeRespVO vo = new CourseManageClassDetailRespVO.ScoreRangeRespVO();
            vo.setRange(switch (band) {
                case EXCELLENT -> "优秀";
                case GOOD -> "良好";
                case QUALIFIED -> "合格";
                case UNQUALIFIED -> "不合格";
            });
            vo.setCount(count);
            vo.setPercent(denominator == 0 ? 0D : roundOneDecimal(count * 100D / denominator));
            return vo;
        }).toList();
    }

    private List<CourseManageClassDetailRespVO.ClassMessageRespVO> buildClassMessages(
            EduClassCourseDO classCourse,
            CapacityCourseRespDTO course,
            Map<Long, AdminUserDO> userMap) {
        List<EduMessageDO> messages = eduMessageMapper.selectList(
                new LambdaQueryWrapper<EduMessageDO>()
                        .eq(EduMessageDO::getClassId, classCourse.getClassId())
                        .eq(EduMessageDO::getCourseId, classCourse.getCourseId())
                        .orderByAsc(EduMessageDO::getCreateTime));
        return messages.stream().map(item -> {
            CourseManageClassDetailRespVO.ClassMessageRespVO vo = new CourseManageClassDetailRespVO.ClassMessageRespVO();
            vo.setId(item.getId());
            vo.setStudentId(item.getStudentId());
            vo.setType(item.getType());
            vo.setContent(item.getContent());
            vo.setCreateTime(item.getCreateTime());
            Long senderId;
            if (Objects.equals(item.getType(), 0)) {
                senderId = item.getStudentId();
            } else {
                senderId = parseLong(item.getCreator(), course.getTeacherId());
            }
            vo.setSenderId(senderId);
            vo.setSenderName(resolveUserName(userMap, senderId, Objects.equals(item.getType(), 1) ? "教师" : "学生"));
            vo.setSenderAvatar(resolveUserAvatar(userMap, senderId));
            return vo;
        }).toList();
    }

    private Long resolveClassOwnerId(EduClassCourseDO classCourse, CapacityCourseRespDTO course,
                                     Set<Long> courseMemberIds, boolean claimMode) {
        if (claimMode) {
            return classCourse.getClaimTeacherId();
        }
        // 系统或非课程团队用户代建班课时，不能把代建者当作负责教师。
        Long creatorId = parseLong(classCourse.getCreator(), null);
        return creatorId != null && (Objects.equals(creatorId, course.getTeacherId()) || courseMemberIds.contains(creatorId))
                ? creatorId : course.getTeacherId();
    }

    private String resolveClassName(Map<Long, EduClassDO> classMap, Long classId) {
        EduClassDO clazz = classMap.get(classId);
        return clazz == null ? "-" : clazz.getClassName();
    }

    private String resolveTrainingCategory(Integer isPublicCourse) {
        return Objects.equals(isPublicCourse, 0) ? "公共实训" : "专属实训";
    }

    private String resolveSemesterStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate now = LocalDate.now();
        if (endDate != null && now.isAfter(endDate)) {
            return "已结束";
        }
        if (startDate != null && now.isBefore(startDate)) {
            return "未开始";
        }
        return "进行中";
    }

    private String resolveAssistantNames(List<Long> assistantIds, Map<Long, AdminUserDO> userMap) {
        if (CollUtil.isEmpty(assistantIds)) {
            return "-";
        }
        String names = assistantIds.stream()
                .map(id -> resolveUserName(userMap, id, null))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("、"));
        return StrUtil.isBlank(names) ? "-" : names;
    }

    private String resolveUserName(Map<Long, AdminUserDO> userMap, Long userId, String defaultValue) {
        if (userId == null) {
            return defaultValue;
        }
        AdminUserDO user = userMap.get(userId);
        if (user == null || StrUtil.isBlank(user.getNickname())) {
            return defaultValue;
        }
        return user.getNickname();
    }

    private String resolveUserAvatar(Map<Long, AdminUserDO> userMap, Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserDO user = userMap.get(userId);
        return user == null ? null : user.getAvatar();
    }

    private Long parseLong(String value, Long defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Double calcAvg(List<Double> scores) {
        if (CollUtil.isEmpty(scores)) {
            return 0D;
        }
        return roundOneDecimal(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0D));
    }

    private Double roundOneDecimal(Double value) {
        return Math.round((value == null ? 0D : value) * 10D) / 10D;
    }

}
