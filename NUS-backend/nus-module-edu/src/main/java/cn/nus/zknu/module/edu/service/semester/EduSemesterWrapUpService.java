package cn.nus.zknu.module.edu.service.semester;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveReqVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpPreviewRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/**
 * 学期收尾：先预览某学期还在用的班级课程、进行中的任务、待批改的提交和公共课关联，
 * 再批量归档选中的班级课程，以及只在该学期有课程安排的班级。归档沿用现有的班课、班级归档，可随时恢复。
 */
@Service
public class EduSemesterWrapUpService {

    @Resource
    private EduSemesterOptionService semesterOptionService;
    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;
    @Resource
    private EduClassCourseService classCourseService;
    @Resource
    private EduClassService classService;
    @Resource
    private EduSemesterCacheInvalidator cacheInvalidator;

    public EduSemesterWrapUpPreviewRespVO preview(String semesterName) {
        EduSemesterOptionRespVO semester = semesterOptionService.resolve(semesterName);
        String name = semester.getSemesterName();
        List<EduClassCourseDO> links = linksOf(name);
        List<EduClassCourseDO> activeLinks = links.stream()
                .filter(link -> Objects.equals(link.getStatus(), 0)).toList();

        EduSemesterWrapUpPreviewRespVO resp = new EduSemesterWrapUpPreviewRespVO();
        resp.setSemesterName(name);
        resp.setCurrent(Boolean.TRUE.equals(semester.getCurrent()));
        resp.setActiveClassCourseCount(activeLinks.size());
        resp.setPublicCourseLinkCount((int) links.stream().filter(EduSemesterWrapUpService::isAutoCreated).count());
        resp.setActivePublicCourseLinkCount((int) activeLinks.stream()
                .filter(EduSemesterWrapUpService::isAutoCreated).count());
        if (activeLinks.isEmpty()) {
            return resp;
        }

        Map<Long, EduClassDO> classes = classMapper.selectByIds(activeLinks.stream()
                        .map(EduClassCourseDO::getClassId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EduClassDO::getId, Function.identity(), (left, right) -> left));
        Map<Long, CapacityCourseRespDTO> courses = capacityCourseApi.getCourseList(activeLinks.stream()
                        .map(EduClassCourseDO::getCourseId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CapacityCourseRespDTO::getId, Function.identity(), (left, right) -> left));
        TaskStats stats = taskStats(name, activeLinks);

        for (EduClassCourseDO link : activeLinks) {
            LinkKey key = new LinkKey(link.getClassId(), link.getCourseId());
            EduSemesterWrapUpPreviewRespVO.ClassCourse item = new EduSemesterWrapUpPreviewRespVO.ClassCourse();
            item.setClassCourseId(link.getId());
            item.setClassId(link.getClassId());
            item.setClassName(Optional.ofNullable(classes.get(link.getClassId()))
                    .map(EduClassDO::getClassName).orElse("班级已删除"));
            item.setCourseId(link.getCourseId());
            item.setCourseName(Optional.ofNullable(courses.get(link.getCourseId()))
                    .map(CapacityCourseRespDTO::getCourseName).orElse("课程已删除"));
            item.setAutoCreated(isAutoCreated(link));
            item.setOpenTaskCount(stats.openTasks().getOrDefault(key, 0));
            item.setPendingReviewCount(stats.pendingReviews().getOrDefault(key, 0));
            resp.getClassCourses().add(item);
        }
        resp.setOpenTaskCount(stats.openTasks().values().stream().mapToInt(Integer::intValue).sum());
        resp.setPendingReviewCount(stats.pendingReviews().values().stream().mapToInt(Integer::intValue).sum());

        Set<Long> candidateClassIds = candidateClassIds(name, activeLinks);
        for (Long classId : candidateClassIds) {
            EduClassDO clazz = classes.get(classId);
            EduSemesterWrapUpPreviewRespVO.ClassItem item = new EduSemesterWrapUpPreviewRespVO.ClassItem();
            item.setClassId(classId);
            item.setClassName(clazz == null ? "班级已删除" : clazz.getClassName());
            item.setActiveClassCourseCount((int) activeLinks.stream()
                    .filter(link -> Objects.equals(link.getClassId(), classId)).count());
            resp.getClasses().add(item);
        }
        resp.getClasses().sort(Comparator.comparing(EduSemesterWrapUpPreviewRespVO.ClassItem::getClassName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public EduSemesterWrapUpArchiveRespVO archive(EduSemesterWrapUpArchiveReqVO reqVO) {
        EduSemesterOptionRespVO semester = semesterOptionService.resolve(reqVO.getSemesterName());
        String name = semester.getSemesterName();
        List<Long> classCourseIds = distinct(reqVO.getClassCourseIds());
        List<Long> classIds = distinct(reqVO.getClassIds());
        if (classCourseIds.isEmpty() && classIds.isEmpty()) {
            throw exception(SEMESTER_WRAP_UP_EMPTY);
        }
        List<EduClassCourseDO> activeLinks = linksOf(name).stream()
                .filter(link -> Objects.equals(link.getStatus(), 0)).toList();
        Set<Long> activeLinkIds = activeLinks.stream().map(EduClassCourseDO::getId).collect(Collectors.toSet());
        if (!activeLinkIds.containsAll(classCourseIds)) {
            throw exception(SEMESTER_WRAP_UP_CLASS_COURSE_INVALID, name);
        }
        // 在归档班课之前判定候选班级，判定口径与预览一致
        Set<Long> candidateClassIds = candidateClassIds(name, activeLinks);
        if (!candidateClassIds.containsAll(classIds)) {
            throw exception(SEMESTER_WRAP_UP_CLASS_INVALID);
        }
        classCourseIds.forEach(classCourseService::unbindClassCourse);
        if (!classIds.isEmpty()) {
            classService.deleteClassList(classIds);
        }
        EduSemesterWrapUpArchiveRespVO resp = new EduSemesterWrapUpArchiveRespVO();
        resp.setSemesterName(name);
        resp.setArchivedClassCourseCount(classCourseIds.size());
        resp.setArchivedClassCount(classIds.size());
        // 提交后再失效，避免并发弹窗在事务提交前重新缓存旧统计。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheInvalidator.evictOptions();
                }
            });
        } else {
            cacheInvalidator.evictOptions();
        }
        return resp;
    }

    /**
     * 只在该学期有课程安排的正常班级：在该学期有人工绑定的在用班课，其他学期没有人工绑定的在用班课。
     * 公共课同步创建的关联覆盖所有班级，不能说明班级还在上课，因此不参与判定。
     */
    Set<Long> candidateClassIds(String semesterName, List<EduClassCourseDO> activeLinks) {
        Set<Long> classIds = activeLinks.stream().filter(link -> !isAutoCreated(link))
                .map(EduClassCourseDO::getClassId).collect(Collectors.toCollection(TreeSet::new));
        if (classIds.isEmpty()) {
            return classIds;
        }
        Set<Long> normalClassIds = classMapper.selectByIds(classIds).stream()
                .filter(clazz -> Objects.equals(clazz.getStatus(), 0))
                .map(EduClassDO::getId).collect(Collectors.toSet());
        classIds.retainAll(normalClassIds);
        if (classIds.isEmpty()) {
            return classIds;
        }
        Set<Long> busyElsewhere = classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                        .select(EduClassCourseDO::getId, EduClassCourseDO::getClassId)
                        .in(EduClassCourseDO::getClassId, classIds)
                        .eq(EduClassCourseDO::getStatus, 0)
                        .and(creator -> creator.isNull(EduClassCourseDO::getCreator)
                                .or().ne(EduClassCourseDO::getCreator, EduClassCourseMapper.SYNC_OPERATOR)
                                .or().isNotNull(EduClassCourseDO::getClaimTeacherId))
                        .apply("TRIM(COALESCE(semester_name, '')) <> {0}", semesterName))
                .stream().map(EduClassCourseDO::getClassId).collect(Collectors.toSet());
        classIds.removeAll(busyElsewhere);
        return classIds;
    }

    private TaskStats taskStats(String semesterName, List<EduClassCourseDO> activeLinks) {
        Set<LinkKey> linkKeys = activeLinks.stream()
                .map(link -> new LinkKey(link.getClassId(), link.getCourseId())).collect(Collectors.toSet());
        Set<Long> courseIds = activeLinks.stream().map(EduClassCourseDO::getCourseId).collect(Collectors.toSet());
        List<EduHomeworkDO> homeworks = homeworkMapper.selectList(new LambdaQueryWrapper<EduHomeworkDO>()
                        .select(EduHomeworkDO::getId, EduHomeworkDO::getClassId, EduHomeworkDO::getCourseId,
                                EduHomeworkDO::getStatus, EduHomeworkDO::getDeadline)
                        .in(EduHomeworkDO::getCourseId, courseIds)
                        .in(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED, EduHomeworkStatusEnum.CLOSED)
                        .apply("(TRIM(COALESCE(semester_name, '')) = '' OR TRIM(semester_name) = {0})", semesterName))
                .stream().filter(homework -> linkKeys.contains(new LinkKey(homework.getClassId(), homework.getCourseId())))
                .toList();
        Map<LinkKey, Integer> openTasks = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, LinkKey> keyByHomework = new HashMap<>();
        for (EduHomeworkDO homework : homeworks) {
            LinkKey key = new LinkKey(homework.getClassId(), homework.getCourseId());
            keyByHomework.put(homework.getId(), key);
            if (Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)
                    && (homework.getDeadline() == null || homework.getDeadline().isAfter(now))) {
                openTasks.merge(key, 1, Integer::sum);
            }
        }
        Map<LinkKey, Integer> pendingReviews = new HashMap<>();
        if (!keyByHomework.isEmpty()) {
            recordMapper.selectList(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                            .select(EduHomeworkRecordDO::getId, EduHomeworkRecordDO::getHomeworkId)
                            .in(EduHomeworkRecordDO::getHomeworkId, keyByHomework.keySet())
                            .in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.PENDING_REVIEW))
                    .forEach(record -> {
                        LinkKey key = keyByHomework.get(record.getHomeworkId());
                        if (key != null) {
                            pendingReviews.merge(key, 1, Integer::sum);
                        }
                    });
        }
        return new TaskStats(openTasks, pendingReviews);
    }

    private List<EduClassCourseDO> linksOf(String semesterName) {
        return classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .apply("TRIM(semester_name) = {0}", semesterName)
                .orderByAsc(EduClassCourseDO::getClassId)
                .orderByAsc(EduClassCourseDO::getCourseId));
    }

    private static boolean isAutoCreated(EduClassCourseDO link) {
        return EduClassCourseMapper.SYNC_OPERATOR.equals(StrUtil.trim(link.getCreator()))
                && link.getClaimTeacherId() == null;
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record LinkKey(Long classId, Long courseId) {
    }

    private record TaskStats(Map<LinkKey, Integer> openTasks, Map<LinkKey, Integer> pendingReviews) {
    }
}
