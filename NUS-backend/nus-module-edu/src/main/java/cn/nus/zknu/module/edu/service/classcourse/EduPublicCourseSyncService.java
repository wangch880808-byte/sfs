package cn.nus.zknu.module.edu.service.classcourse;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import cn.nus.zknu.module.edu.service.semester.EduTermFlowPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线公共课与正常班级的当前学期关联：只补缺失记录，新建的关联在提交后通知学生；
 * 旧学期和已下线课程中由同步创建、且已空闲的关联会被归档（保留记录，可在班级课程里查看和恢复）。
 */
@Service
@Slf4j
public class EduPublicCourseSyncService {

    @Resource
    @Lazy
    private EduSemesterService semesterService;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    @Lazy
    private EduPublicCourseBackfillService backfillService;
    @Resource
    private EduTermFlowPolicy termFlowPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncCourse(Long courseId) {
        classCourseMapper.lockPublicCourse(courseId);
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (!isOnline(course)) {
            // 课程下线：只归档未认领、同步创建且从未人工改动、已经没有进行中任务和待批改提交的关联
            int archived = classCourseMapper.archiveIdleAutoLinksOfOfflineCourses(courseId);
            if (archived > 0) {
                log.info("[syncCourse][公共课已下线，封存同步关联 courseId={}, count={}]", courseId, archived);
            }
            return;
        }
        EduSemesterRespVO semester = currentSemester();
        if (semester == null) {
            return;
        }
        String semesterName = StrUtil.trim(semester.getSemesterName());
        classCourseMapper.restoreSyncArchivedLinks(courseId, semesterName);
        Set<ClassCourseKey> existing = existingKeys(semester.getSemesterName(), null, courseId);
        Map<Long, Set<Long>> inserted = new LinkedHashMap<>();
        if (!retryPending(courseId, semesterName, inserted)) {
            for (EduClassDO clazz : normalClasses()) {
                bindMissing(clazz.getId(), courseId, semester, existing, inserted);
            }
        }
        backfillAfterCommit(semesterName, inserted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncClass(Long classId) {
        EduSemesterRespVO semester = currentSemester();
        if (semester == null) {
            return;
        }
        EduClassDO clazz = classMapper.selectById(classId);
        if (clazz == null || !Objects.equals(clazz.getStatus(), 0)) {
            return;
        }
        List<CapacityCourseRespDTO> courses = lockOnlineCourses();
        Set<ClassCourseKey> existing = existingKeys(semester.getSemesterName(), classId, null);
        Map<Long, Set<Long>> inserted = new LinkedHashMap<>();
        for (CapacityCourseRespDTO course : courses) {
            if (!retryPending(course.getId(), StrUtil.trim(semester.getSemesterName()), inserted)) {
                bindMissing(classId, course.getId(), semester, existing, inserted);
            }
        }
        backfillAfterCommit(StrUtil.trim(semester.getSemesterName()), inserted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncAll() {
        EduSemesterRespVO semester = currentSemester();
        if (semester == null) {
            return;
        }
        String semesterName = StrUtil.trim(semester.getSemesterName());
        List<CapacityCourseRespDTO> courses = lockOnlineCourses();
        // 先处理已有关联：下线课程归档、重新上线课程恢复、旧学期空闲关联归档，再补齐当前学期
        archiveStaleLinks(semesterName);
        classCourseMapper.restoreSyncArchivedLinks(null, semesterName);
        List<EduClassDO> classes = normalClasses();
        Set<ClassCourseKey> existing = existingKeys(semester.getSemesterName(), null, null);
        Map<Long, Set<Long>> inserted = new LinkedHashMap<>();
        for (CapacityCourseRespDTO course : courses) {
            if (retryPending(course.getId(), semesterName, inserted)) {
                continue;
            }
            for (EduClassDO clazz : classes) {
                bindMissing(clazz.getId(), course.getId(), semester, existing, inserted);
            }
        }
        backfillAfterCommit(semesterName, inserted);
        retryBacklogAfterCommit(semesterName, inserted.keySet());
    }

    /** 每日同步也重试历史学期的失败批次；当前轮已安排的课程不重复安排。 */
    private void retryBacklogAfterCommit(String currentSemester, Set<Long> scheduledCourses) {
        Map<BackfillKey, Set<Long>> backlog = new LinkedHashMap<>();
        for (EduClassCourseDO link : classCourseMapper.selectBackfillBacklog()) {
            String semester = StrUtil.trim(link.getSemesterName());
            if (Objects.equals(semester, currentSemester) && scheduledCourses.contains(link.getCourseId())) {
                continue;
            }
            backlog.computeIfAbsent(new BackfillKey(link.getCourseId(), semester), ignored -> new LinkedHashSet<>())
                    .add(link.getClassId());
        }
        if (!backlog.isEmpty()) {
            afterCommit(() -> backlog.forEach((key, classIds) -> runBackfill(key.courseId(), key.semester(), classIds)));
        }
    }

    /**
     * 归档不再需要的同步关联：只动同步创建、更新人仍是同步程序（即没有被人工归档或恢复过）、
     * 关联下没有进行中任务和待批改提交的记录；仍有进行中任务的会在之后的每日同步里再处理。
     */
    private void archiveStaleLinks(String currentSemester) {
        int offline = classCourseMapper.archiveIdleAutoLinksOfOfflineCourses(null);
        int previous = termFlowPolicy.archivePreviousSemesterPublicLinks()
                ? classCourseMapper.archiveIdleAutoLinksOutsideSemester(currentSemester) : 0;
        if (offline > 0 || previous > 0) {
            log.info("[syncAll][封存同步关联 offlineCourse={}, previousSemester={}]", offline, previous);
        }
    }

    /**
     * 上一批尚未补发时先重试，不扩大既有覆盖范围；否则两个已提交的新批次会互相阻止补发。
     * 课程锁下的当前读与补发共用同一把锁，新关联在后续同步中补齐。
     */
    private boolean retryPending(Long courseId, String semesterName, Map<Long, Set<Long>> inserted) {
        List<EduClassCourseDO> pending = classCourseMapper.selectPendingBackfillLinks(courseId, semesterName);
        if (pending.isEmpty()) {
            return false;
        }
        inserted.put(courseId, pending.stream().map(EduClassCourseDO::getClassId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return true;
    }

    /** 新建或待通知关联在同步事务提交后通知学生；失败保留标记供下一次同步重试。 */
    private void backfillAfterCommit(String semesterName, Map<Long, Set<Long>> inserted) {
        log.info("[backfillAfterCommit][公共课关联处理统计 semester={}, pendingCourseCount={}, pendingLinkCount={}]",
                semesterName, inserted.size(), inserted.values().stream().mapToInt(Set::size).sum());
        if (inserted.isEmpty()) {
            return;
        }
        afterCommit(() -> inserted.forEach((courseId, classIds) -> runBackfill(courseId, semesterName, classIds)));
    }

    private void runBackfill(Long courseId, String semesterName, Set<Long> classIds) {
        try {
            backfillService.backfill(courseId, semesterName, classIds);
        } catch (Exception ex) {
            log.error("[backfillAfterCommit][公共课分配通知失败 courseId={}]", courseId, ex);
        }
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private EduSemesterRespVO currentSemester() {
        EduSemesterRespVO semester = semesterService.getCurrentSemester();
        if (semester == null || StrUtil.isBlank(semester.getSemesterName())
                || "未设置".equals(StrUtil.trim(semester.getSemesterName()))) {
            log.warn("[syncPublicCourse][当前学期未设置，跳过同步]");
            return null;
        }
        return semester;
    }

    private List<EduClassDO> normalClasses() {
        return classMapper.selectList(new LambdaQueryWrapper<EduClassDO>()
                .eq(EduClassDO::getStatus, 0));
    }

    /** 多课程同步按 ID 顺序加锁，避免两个同步请求以不同顺序持有课程锁。 */
    private List<CapacityCourseRespDTO> lockOnlineCourses() {
        List<CapacityCourseRespDTO> courses = capacityCourseApi.getOnlinePublicCourseList().stream()
                .sorted(Comparator.comparing(CapacityCourseRespDTO::getId)).toList();
        courses.forEach(course -> classCourseMapper.lockPublicCourse(course.getId()));
        return courses;
    }

    /** 课程 API 只返回未删除的课程。 */
    private boolean isOnline(CapacityCourseRespDTO course) {
        return course != null && course.isOnlinePublicCourse();
    }

    private Set<ClassCourseKey> existingKeys(String semesterName, Long classId, Long courseId) {
        return classCourseMapper.selectList(new QueryWrapper<EduClassCourseDO>()
                        .select("class_id", "course_id")
                        .apply("TRIM(semester_name) = {0}", StrUtil.trim(semesterName))
                        .eq(classId != null, "class_id", classId)
                        .eq(courseId != null, "course_id", courseId)
                        .last("FOR UPDATE"))
                .stream().map(item -> new ClassCourseKey(item.getClassId(), item.getCourseId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void bindMissing(Long classId, Long courseId, EduSemesterRespVO semester,
                             Set<ClassCourseKey> existing, Map<Long, Set<Long>> inserted) {
        String name = StrUtil.trim(semester.getSemesterName());
        ClassCourseKey key = new ClassCourseKey(classId, courseId);
        if (existing.contains(key)) {
            return; // 人工归档的关联也保持原样
        }
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setClassId(classId);
        relation.setCourseId(courseId);
        relation.setSemesterName(name);
        relation.setSemesterStartDate(semester.getSemesterStartDate());
        relation.setSemesterEndDate(semester.getSemesterEndDate());
        relation.setStatus(0);
        relation.setBackfillPending(true);
        relation.setCreator(EduClassCourseMapper.SYNC_OPERATOR);
        relation.setUpdater(EduClassCourseMapper.SYNC_OPERATOR);
        try {
            classCourseMapper.insert(relation);
            inserted.computeIfAbsent(courseId, ignored -> new LinkedHashSet<>()).add(classId);
        } catch (DuplicateKeyException ignored) {
            // 人工绑定或其他同步已补齐相同关联；待补发标记由后续同步重试。
        }
        existing.add(key);
    }

    private record BackfillKey(Long courseId, String semester) {
    }

    private record ClassCourseKey(Long classId, Long courseId) {
    }
}
