package cn.nus.zknu.module.edu.service.classcourse;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkCopyService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.semester.EduTermFlowPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共课同步只通知学生课程已分配；认领时补发认领教师的进行中任务。
 * <p>
 * 任务按班级建模（edu_homework.class_id），指派人是发布时该班学生的快照。补发沿用同一模型：
 * 为新班级生成一份已发布的任务（来源记在 source_homework_id），按新班级学生生成指派快照并发通知。
 * 「全课程任务」指同名、同截止时间的源任务覆盖了该教师本学期其他已认领的在用班级，
 * 只布置给部分班级的任务不会补发给新班级。
 */
@Service
@Slf4j
public class EduPublicCourseBackfillService {

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private EduClassStudentMapper classStudentMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduBusinessNotifyService notifyService;
    @Resource
    private EduTermFlowPolicy termFlowPolicy;
    @Resource
    @Lazy
    private EduHomeworkCopyService copyService;

    /**
     * 处理同一门公共课本次同步新建的班级关联。
     *
     * @param courseId     公共课 ID
     * @param semesterName 当前学期
     * @param newClassIds  触发通知的班级；实际待通知批次在课程锁下从数据库读取
     * @return 补发结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BackfillResult backfill(Long courseId, String semesterName, Collection<Long> newClassIds) {
        if (courseId == null
                || StrUtil.isBlank(semesterName) || newClassIds == null || newClassIds.isEmpty()) {
            return BackfillResult.EMPTY;
        }
        long started = System.nanoTime();
        classCourseMapper.lockPublicCourse(courseId);
        String semester = StrUtil.trim(semesterName);
        List<EduClassCourseDO> pending = classCourseMapper.selectPendingBackfillLinks(courseId, semester);
        if (pending.isEmpty()) {
            return BackfillResult.EMPTY; // 另一个回调已经完成，不能重复通知或指派
        }
        if (!termFlowPolicy.publicCourseBackfillEnabled()) {
            classCourseMapper.completeBackfill(courseId, semester);
            return BackfillResult.EMPTY;
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (!isOnline(course)) {
            return BackfillResult.EMPTY;
        }
        Set<Long> newIds = pending.stream().map(EduClassCourseDO::getClassId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> activeIds = pending.stream().filter(link -> Objects.equals(link.getStatus(), 0))
                .map(EduClassCourseDO::getClassId).collect(Collectors.toSet());
        List<EduClassDO> newClasses = classMapper.selectByIds(newIds).stream()
                .filter(clazz -> activeIds.contains(clazz.getId()))
                .filter(clazz -> Objects.equals(clazz.getStatus(), 0))
                .sorted(Comparator.comparing(EduClassDO::getId))
                .toList();
        if (newClasses.isEmpty()) {
            classCourseMapper.completeBackfill(courseId, semester);
            return BackfillResult.EMPTY;
        }
        Map<Long, List<Long>> studentsByClass = studentsByClass(newClasses.stream().map(EduClassDO::getId).toList());
        int notifiedStudents = 0;
        for (EduClassDO clazz : newClasses) {
            notifiedStudents += notifyService.sendToUsers(studentsByClass.getOrDefault(clazz.getId(), List.of()),
                    EduBusinessNotifyService.COURSE_ASSIGNED,
                    Map.of("className", StrUtil.blankToDefault(clazz.getClassName(), "未命名班级"),
                            "courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程")),
                    "/student/course/detail/" + courseId);
        }
        classCourseMapper.completeBackfill(courseId, semester);
        BackfillResult result = new BackfillResult(newClasses.size(), notifiedStudents, 0, 0);
        log.info("[backfill][公共课分配通知完成 courseId={}, semester={}, result={}, durationMs={}]",
                courseId, semester, result, (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    /** 认领与补发共用事务和课程锁，避免放弃、再认领之间给错误的教师补发。 */
    @Transactional(rollbackFor = Exception.class)
    public BackfillResult backfillForClaim(Long courseId, String semesterName, Long teacherId, Long classId) {
        return backfillForClaim(courseId, semesterName, teacherId, classId,
                classId == null ? Set.of() : Set.of(classId));
    }

    /** 整批新增认领都排除，才能保持本次认领之前的覆盖范围。 */
    @Transactional(rollbackFor = Exception.class)
    public BackfillResult backfillForClaim(Long courseId, String semesterName, Long teacherId, Long classId,
                                          Set<Long> newClassIds) {
        if (courseId == null || teacherId == null || classId == null || StrUtil.isBlank(semesterName)) {
            return BackfillResult.EMPTY;
        }
        classCourseMapper.lockPublicCourse(courseId);
        String semester = StrUtil.trim(semesterName);
        EduClassCourseDO target = classCourseMapper.selectClaimRelation(courseId, classId, semester);
        if (target == null || !Objects.equals(target.getClaimTeacherId(), teacherId)
                || !Objects.equals(target.getStatus(), 0) || !termFlowPolicy.publicCourseBackfillEnabled()) {
            return BackfillResult.EMPTY;
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        EduClassDO targetClass = classMapper.selectById(classId);
        if (!isOnline(course) || targetClass == null || !Objects.equals(targetClass.getStatus(), 0)) {
            return BackfillResult.EMPTY;
        }
        List<EduClassDO> newClasses = List.of(targetClass);
        List<List<EduHomeworkDO>> groups = courseWideGroups(courseId, semester, newClassIds, teacherId);
        if (groups.isEmpty()) {
            return BackfillResult.EMPTY;
        }
        List<Long> affectedClassIds = newClasses.stream().map(EduClassDO::getId).toList();
        Map<Long, List<Long>> studentsByClass = studentsByClass(affectedClassIds);
        // 同一次查询覆盖去重与重名判断；包括往期任务，保持原有 source_homework_id 去重口径。
        Map<Long, List<EduHomeworkDO>> existingByClass = homeworkMapper.selectList(new LambdaQueryWrapper<EduHomeworkDO>()
                        .select(EduHomeworkDO::getClassId, EduHomeworkDO::getTeacherId,
                                EduHomeworkDO::getHomeworkName, EduHomeworkDO::getSourceHomeworkId,
                                EduHomeworkDO::getSemesterName)
                        .eq(EduHomeworkDO::getCourseId, courseId)
                        .in(EduHomeworkDO::getClassId, affectedClassIds).last("FOR UPDATE")).stream()
                        .collect(Collectors.groupingBy(EduHomeworkDO::getClassId));
        List<EduHomeworkAssigneeDO> assignees = new ArrayList<>();
        List<TaskNotice> taskNotices = new ArrayList<>();
        int createdTasks = 0;
        for (EduClassDO clazz : newClasses) {
            List<Long> studentIds = studentsByClass.getOrDefault(clazz.getId(), List.of());
            List<EduHomeworkDO> existing = existingByClass.computeIfAbsent(clazz.getId(), ignored -> new ArrayList<>());
            for (List<EduHomeworkDO> group : groups) {
                EduHomeworkDO created = replicate(group, clazz.getId(), semester, existing);
                if (created != null) {
                    createdTasks++;
                    existing.add(created);
                    addAssignees(created, studentIds, assignees);
                    taskNotices.add(new TaskNotice(created, studentIds));
                }
            }
        }
        if (!assignees.isEmpty()) {
            assigneeMapper.insertBatch(assignees);
        }
        for (TaskNotice notice : taskNotices) {
            sendTaskNotice(notice.task(), notice.studentIds(), course);
        }
        return new BackfillResult(1, 0, createdTasks, assignees.size());
    }

    private Map<Long, List<Long>> studentsByClass(List<Long> classIds) {
        Map<Long, List<Long>> studentsByClass = new LinkedHashMap<>();
        classStudentMapper.selectListByClassIds(classIds).stream()
                .filter(student -> student.getStudentId() != null)
                .collect(Collectors.groupingBy(EduClassStudentDO::getClassId, LinkedHashMap::new,
                        Collectors.mapping(EduClassStudentDO::getStudentId,
                                Collectors.toCollection(LinkedHashSet::new))))
                .forEach((classId, ids) -> studentsByClass.put(classId, new ArrayList<>(ids)));
        return studentsByClass;
    }

    /**
     * 本学期覆盖了该教师其他已认领在用班级的进行中任务分组：同一教师、同名、同截止时间。
     * 既有班级为空（例如学期刚切换、所有关联都是新建的）时没有可补发的任务。
     */
    List<List<EduHomeworkDO>> courseWideGroups(Long courseId, String semester, Set<Long> newClassIds, Long teacherId) {
        Set<Long> coverage = classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                        .select(EduClassCourseDO::getId, EduClassCourseDO::getClassId)
                        .eq(EduClassCourseDO::getCourseId, courseId)
                        .eq(EduClassCourseDO::getStatus, 0)
                        .eq(EduClassCourseDO::getClaimTeacherId, teacherId)
                        .apply("TRIM(semester_name) = {0}", semester).last("FOR UPDATE"))
                .stream().map(EduClassCourseDO::getClassId)
                .filter(classId -> !newClassIds.contains(classId))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!coverage.isEmpty()) {
            Set<Long> active = classMapper.selectByIds(coverage).stream()
                    .filter(clazz -> Objects.equals(clazz.getStatus(), 0))
                    .map(EduClassDO::getId).collect(Collectors.toSet());
            coverage.retainAll(active);
        }
        if (coverage.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<EduHomeworkDO> openTasks = homeworkMapper.selectList(new LambdaQueryWrapper<EduHomeworkDO>()
                .eq(EduHomeworkDO::getCourseId, courseId)
                .eq(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED)
                .eq(EduHomeworkDO::getTeacherId, teacherId)
                .isNull(EduHomeworkDO::getSourceHomeworkId)
                .in(EduHomeworkDO::getClassId, coverage)
                .apply("TRIM(semester_name) = {0}", semester)
                .and(deadline -> deadline.isNull(EduHomeworkDO::getDeadline)
                        .or().gt(EduHomeworkDO::getDeadline, now))
                .orderByAsc(EduHomeworkDO::getId).last("FOR UPDATE"));
        Map<GroupKey, List<EduHomeworkDO>> grouped = new LinkedHashMap<>();
        for (EduHomeworkDO task : openTasks) {
            if (!Objects.equals(task.getTeacherId(), teacherId) || task.getSourceHomeworkId() != null) {
                continue;
            }
            grouped.computeIfAbsent(new GroupKey(task.getTeacherId(), StrUtil.trim(task.getHomeworkName()),
                    task.getDeadline()), ignored -> new ArrayList<>()).add(task);
        }
        return grouped.values().stream()
                .filter(group -> group.stream().map(EduHomeworkDO::getClassId).collect(Collectors.toSet())
                        .containsAll(coverage))
                .toList();
    }

    /** 新班级还没有这份任务时，按最新的一份生成已发布任务、指派快照并通知学生。 */
    EduHomeworkDO replicate(List<EduHomeworkDO> group, Long classId, String semester,
                            List<EduHomeworkDO> existing) {
        EduHomeworkDO source = group.get(group.size() - 1);
        Set<Long> groupIds = group.stream().map(EduHomeworkDO::getId).collect(Collectors.toSet());
        boolean duplicate = existing.stream().anyMatch(task -> groupIds.contains(task.getSourceHomeworkId())
                || (Objects.equals(task.getTeacherId(), source.getTeacherId())
                    && Objects.equals(task.getHomeworkName(), source.getHomeworkName())
                    && Objects.equals(StrUtil.trim(task.getSemesterName()), semester)));
        if (duplicate) {
            return null;
        }
        EduHomeworkDO task = new EduHomeworkDO();
        task.setHomeworkName(copyService.uniqueName(source.getHomeworkName(), existing.stream()
                .map(EduHomeworkDO::getHomeworkName).filter(Objects::nonNull).toList()));
        task.setHomeworkType(source.getHomeworkType());
        task.setHomeworkLevel(source.getHomeworkLevel());
        task.setCourseId(source.getCourseId());
        task.setClassId(classId);
        task.setSemesterName(semester);
        task.setIndicatorFirstId(source.getIndicatorFirstId());
        task.setIndicatorSecondId(source.getIndicatorSecondId());
        task.setRequirement(source.getRequirement());
        task.setDeadline(source.getDeadline());
        task.setAutoGrade(source.getAutoGrade());
        task.setSubmissionMode(source.getSubmissionMode());
        task.setAiReviewMode(source.getAiReviewMode());
        task.setRequiredAttachmentTypesJson(source.getRequiredAttachmentTypesJson());
        task.setAiRubricJson(source.getAiRubricJson());
        task.setAiReferenceJson(source.getAiReferenceJson());
        task.setAiConfidenceThreshold(source.getAiConfidenceThreshold());
        task.setPassScore(source.getPassScore());
        task.setScore(source.getScore());
        task.setTeacherId(source.getTeacherId());
        task.setStatus(EduHomeworkStatusEnum.PUBLISHED);
        task.setSourceHomeworkId(source.getId());
        task.setCreator(EduClassCourseMapper.SYNC_OPERATOR);
        task.setUpdater(EduClassCourseMapper.SYNC_OPERATOR);
        homeworkMapper.insert(task);

        return task;
    }

    private void addAssignees(EduHomeworkDO task, List<Long> studentIds,
                              List<EduHomeworkAssigneeDO> assignees) {
        LocalDateTime assignedTime = LocalDateTime.now();
        for (Long studentId : studentIds) {
            EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
            assignee.setHomeworkId(task.getId());
            assignee.setStudentId(studentId);
            assignee.setClassId(task.getClassId());
            assignee.setAssignedTime(assignedTime);
            assignee.setCreator(EduClassCourseMapper.SYNC_OPERATOR);
            assignee.setUpdater(EduClassCourseMapper.SYNC_OPERATOR);
            assignees.add(assignee);
        }
    }

    private void sendTaskNotice(EduHomeworkDO task, List<Long> studentIds, CapacityCourseRespDTO course) {
        if (studentIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("courseName", StrUtil.blankToDefault(course.getCourseName(), "未命名课程"));
        params.put("homeworkName", task.getHomeworkName());
        params.put("deadline", task.getDeadline() == null ? "未设置" : task.getDeadline().format(DEADLINE_FORMAT));
        notifyService.sendToUsers(studentIds, EduBusinessNotifyService.HOMEWORK_CREATED, params,
                "/student/homework/submit/" + task.getId());
    }

    private record TaskNotice(EduHomeworkDO task, List<Long> studentIds) {
    }

    /** 课程 API 只返回未删除的课程。 */
    private boolean isOnline(CapacityCourseRespDTO course) {
        return course != null && course.isOnlinePublicCourse();
    }

    private record GroupKey(Long teacherId, String homeworkName, LocalDateTime deadline) {
    }

    /** 补发结果：处理的班级数、收到课程分配通知的学生数、补发的任务数、新建的指派数。 */
    public record BackfillResult(int classCount, int notifiedStudents, int createdTasks, int createdAssignees) {
        public static final BackfillResult EMPTY = new BackfillResult(0, 0, 0, 0);
    }
}
