package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** 实训任务 Service 实现类。 */
@Service
public class EduHomeworkServiceImpl implements EduHomeworkService {

    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseMapper eduClassCourseMapper;
    @Resource
    private EduClassMapper eduClassMapper;
    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduHomeworkRecordMapper eduHomeworkRecordMapper;
    @Resource
    private EduAiBusinessAccessService aiBusinessAccessService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduHomeworkCreationSupport creationSupport;
    @Resource
    private EduHomeworkPublishService publishService;
    @Resource
    private EduHomeworkStatisticsService statisticsService;
    @Resource
    private EduHomeworkAiCreateService aiCreateService;
    @Resource
    private EduHomeworkChangeLogService homeworkChangeLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createHomework(EduHomeworkSaveReqVO createReqVO) {
        validateCreateStatus(createReqVO.getStatus());
        creationSupport.validateScoreConfig(createReqVO.getPassScore(), createReqVO.getScore());
        EduClassCourseDO relation = validateCourseClassRelation(createReqVO.getCourseId(),
                createReqVO.getClassId(), createReqVO.getSemesterName(), false, createReqVO.getClassCourseId());
        if (createReqVO.getSourceHomeworkId() != null) {
            aiBusinessAccessService.requireHomeworkViewAccess(createReqVO.getSourceHomeworkId());
        }
        // 重名处理：同班级课程下重名任务自动加后缀
        String finalName = creationSupport.generateUniqueName(createReqVO.getClassId(),
                createReqVO.getCourseId(), createReqVO.getHomeworkName());
        EduHomeworkDO homework = BeanUtils.toBean(createReqVO, EduHomeworkDO.class);
        if (StrUtil.isBlank(homework.getSemesterName())) {
            homework.setSemesterName(relation.getSemesterName());
        }
        homework.setHomeworkName(finalName);
        // 自动填充当前登录教师ID
        homework.setTeacherId(getLoginUserId());
        creationSupport.fillHomeworkDefaults(homework);
        eduHomeworkMapper.insert(homework);
        publishService.snapshotAssigneesIfPublished(homework);
        publishService.notifyPublishedHomework(homework, EduBusinessNotifyService.HOMEWORK_CREATED);
        return homework.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHomework(EduHomeworkSaveReqVO updateReqVO) {
        EduHomeworkDO current = validateHomeworkExistsAndAccess(updateReqVO.getId());
        if (Objects.equals(current.getStatus(), EduHomeworkStatusEnum.ARCHIVED)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        boolean wasDraft = Objects.equals(current.getStatus(), EduHomeworkStatusEnum.DRAFT);
        if (!wasDraft && Objects.equals(updateReqVO.getStatus(), EduHomeworkStatusEnum.DRAFT)) {
            throw exception(HOMEWORK_STATUS_DRAFT_FORBIDDEN);
        }
        if (wasDraft) {
            if (updateReqVO.getDeadline() == null) {
                throw exception(HOMEWORK_DEADLINE_REQUIRED);
            }
            // 旧草稿还没有学生快照，保存时统一进入原发布流程。
            updateReqVO.setStatus(EduHomeworkStatusEnum.PUBLISHED);
        }
        validateUpdateStatus(updateReqVO.getStatus(), current.getStatus());
        creationSupport.validateScoreConfig(updateReqVO.getPassScore(), updateReqVO.getScore());
        boolean sameOwnedPublicScope = !wasDraft && Objects.equals(current.getTeacherId(), getLoginUserId())
                && Objects.equals(current.getCourseId(), updateReqVO.getCourseId())
                && Objects.equals(current.getClassId(), updateReqVO.getClassId())
                && current.getCourseId() != null && dataScopeService.isPublicCourse(current.getCourseId());
        validateCourseClassRelation(updateReqVO.getCourseId(), updateReqVO.getClassId(),
                StrUtil.blankToDefault(updateReqVO.getSemesterName(), current.getSemesterName()), sameOwnedPublicScope, updateReqVO.getClassCourseId());
        validatePublishedScopeUnchanged(current, updateReqVO);
        long submittedCount = validateLevelUnchangedAfterSubmission(current, updateReqVO);
        EduHomeworkDO updateObj = BeanUtils.toBean(updateReqVO, EduHomeworkDO.class);
        // 来源只在创建时确定，编辑不能跳过来源查看授权改写关联。
        updateObj.setSourceHomeworkId(null);
        updateObj.setAiReviewMode(HomeworkGradingMode.normalizeAiReviewMode(
                updateObj.getAutoGrade() != null ? updateObj.getAutoGrade() : current.getAutoGrade(),
                updateObj.getAiReviewMode()));
        eduHomeworkMapper.updateById(updateObj);
        recordPassScoreChange(current, updateReqVO, submittedCount);
        Integer effectiveStatus = updateReqVO.getStatus() == null ? current.getStatus() : updateReqVO.getStatus();
        if (Objects.equals(current.getStatus(), EduHomeworkStatusEnum.DRAFT)
                && Objects.equals(effectiveStatus, EduHomeworkStatusEnum.PUBLISHED)) {
            updateObj.setStatus(effectiveStatus);
            publishService.snapshotAssigneesIfPublished(updateObj);
        }
        EduHomeworkDO updated = eduHomeworkMapper.selectById(updateReqVO.getId());
        publishService.notifyUpdatedHomework(current, updated);
    }

    @Override
    public void deleteHomework(Long id) {
        EduHomeworkDO homework = validateHomeworkExistsAndAccess(id);
        if (Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED)) {
            return;
        }
        EduHomeworkDO update = new EduHomeworkDO();
        update.setId(id);
        update.setStatusBeforeArchive(homework.getStatus());
        update.setStatus(EduHomeworkStatusEnum.ARCHIVED);
        eduHomeworkMapper.updateById(update);
    }

    @Override
    public void restoreHomework(Long id) {
        EduHomeworkDO homework = validateHomeworkExistsAndAccess(id);
        if (!Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED)) {
            return;
        }
        EduHomeworkDO update = new EduHomeworkDO();
        update.setId(id);
        update.setStatus(homework.getStatusBeforeArchive() == null ? EduHomeworkStatusEnum.PUBLISHED : homework.getStatusBeforeArchive());
        eduHomeworkMapper.updateById(update);
    }

    @Override
    public EduHomeworkDO getHomework(Long id) {
        return aiBusinessAccessService.requireHomeworkViewAccess(id);
    }

    @Override
    public PageResult<EduHomeworkDO> getHomeworkPage(EduHomeworkPageReqVO reqVO) {
        return eduHomeworkMapper.selectPage(reqVO, new LambdaQueryWrapperX<EduHomeworkDO>()
                .likeIfPresent(EduHomeworkDO::getHomeworkName, reqVO.getHomeworkName())
                .eqIfPresent(EduHomeworkDO::getHomeworkType, reqVO.getHomeworkType())
                .eqIfPresent(EduHomeworkDO::getCourseId, reqVO.getCourseId())
                .eqIfPresent(EduHomeworkDO::getClassId, reqVO.getClassId())
                .eqIfPresent(EduHomeworkDO::getSemesterName, reqVO.getSemesterName())
                .eqIfPresent(EduHomeworkDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(EduHomeworkDO::getCreateTime, reqVO.getCreateTime())
                .ne(!Boolean.TRUE.equals(reqVO.getIncludeArchived()), EduHomeworkDO::getStatus, EduHomeworkStatusEnum.ARCHIVED)
                .inSql(!Boolean.TRUE.equals(reqVO.getIncludeArchived()), EduHomeworkDO::getCourseId,
                        "SELECT id FROM capacity_course WHERE deleted = 0 AND status = 0")
                .inSql(!Boolean.TRUE.equals(reqVO.getIncludeArchived()), EduHomeworkDO::getClassId,
                        "SELECT id FROM edu_class WHERE deleted = 0 AND status = 0")
                .apply(!Boolean.TRUE.equals(reqVO.getIncludeArchived()),
                        "EXISTS (SELECT 1 FROM edu_class_course ecc WHERE ecc.class_id = edu_homework.class_id "
                                + "AND ecc.course_id = edu_homework.course_id AND ecc.deleted = 0 AND ecc.status = 0 "
                                + "AND (TRIM(COALESCE(edu_homework.semester_name, '')) = '' "
                                + "OR TRIM(COALESCE(ecc.semester_name, '')) = '' "
                                + "OR TRIM(ecc.semester_name) = TRIM(edu_homework.semester_name)))")
                .eq(EduHomeworkDO::getTeacherId, getLoginUserId())
                .orderByDesc(EduHomeworkDO::getId));
    }

    /**
     * 校验实训任务存在。
     */
    @VisibleForTesting
    public void validateHomeworkExists(Long id) {
        if (id == null) {
            return;
        }
        EduHomeworkDO homework = eduHomeworkMapper.selectById(id);
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
    }

    private EduHomeworkDO validateHomeworkExistsAndAccess(Long id) {
        if (id == null) {
            return null;
        }
        EduHomeworkDO homework = eduHomeworkMapper.selectById(id);
        validateHomeworkAccess(homework);
        return homework;
    }

    private void validateHomeworkAccess(EduHomeworkDO homework) {
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        Long loginUserId = getLoginUserId();
        if (loginUserId == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        if (Objects.equals(homework.getTeacherId(), loginUserId)) {
            return;
        }
        throw exception(HOMEWORK_NOT_EXISTS);
    }

    /**
     * 仅允许给当前课程已绑定的班级创建/修改任务。
     */
    private EduClassCourseDO validateCourseClassRelation(Long courseId, Long classId, String semesterName,
                                                        boolean sameOwnedPublicScope, Long classCourseId) {
        List<EduClassCourseDO> relations = classCourseId == null ? null : findCourseClassRelations(courseId, classId);
        EduClassCourseDO selected = null;
        if (classCourseId != null) {
            selected = relations.stream().filter(relation -> Objects.equals(relation.getId(), classCourseId)
                    && Objects.equals(relation.getStatus(), 0)
                    && (StrUtil.isBlank(semesterName)
                    || StrUtil.equals(StrUtil.trim(relation.getSemesterName()), StrUtil.trim(semesterName))))
                    .findFirst().orElseThrow(() -> exception(HOMEWORK_CLASS_COURSE_MISMATCH));
        }
        // 原范围的本人任务不因放弃认领失去修改权，归档和学期校验仍须保留。
        if (!sameOwnedPublicScope) {
            aiBusinessAccessService.validateCourseClassAccess(courseId, classId);
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (course != null && Objects.equals(course.getStatus(), 1)) {
            throw exception(COURSE_ARCHIVED);
        }
        EduClassDO clazz = eduClassMapper.selectById(classId);
        if (clazz != null && Objects.equals(clazz.getStatus(), 1)) {
            throw exception(CLASS_ARCHIVED);
        }
        if (selected != null) {
            return selected;
        }
        relations = findCourseClassRelations(courseId, classId);
        return relations.stream().filter(relation -> Objects.equals(relation.getStatus(), 0)
                && (StrUtil.isBlank(semesterName)
                || StrUtil.isBlank(relation.getSemesterName())
                || StrUtil.equals(StrUtil.trim(relation.getSemesterName()), StrUtil.trim(semesterName))))
                .findFirst().orElseThrow(() -> exception(CLASS_COURSE_ARCHIVED));
    }

    private List<EduClassCourseDO> findCourseClassRelations(Long courseId, Long classId) {
        return eduClassCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getCourseId, courseId)
                .eq(EduClassCourseDO::getClassId, classId)
                .orderByDesc(EduClassCourseDO::getId));
    }

    private void validateCreateStatus(Integer status) {
        if (Objects.equals(status, EduHomeworkStatusEnum.DRAFT)) {
            throw exception(HOMEWORK_DRAFT_FORBIDDEN);
        }
        if (status != null && !Objects.equals(status, EduHomeworkStatusEnum.PUBLISHED)) {
            throw exception(HOMEWORK_STATUS_INVALID);
        }
    }

    private void validateUpdateStatus(Integer status, Integer currentStatus) {
        if (status != null && !Objects.equals(status, EduHomeworkStatusEnum.PUBLISHED)
                && !Objects.equals(status, currentStatus)) {
            throw exception(HOMEWORK_STATUS_INVALID);
        }
    }

    private void validatePublishedScopeUnchanged(EduHomeworkDO current, EduHomeworkSaveReqVO reqVO) {
        if (Objects.equals(current.getStatus(), EduHomeworkStatusEnum.DRAFT)) {
            return;
        }
        if (!Objects.equals(current.getClassId(), reqVO.getClassId())
                || !Objects.equals(current.getCourseId(), reqVO.getCourseId())) {
            throw exception(HOMEWORK_ASSIGNEE_SCOPE_LOCKED);
        }
    }

    /**
     * 已有学生提交后不能再改任务等级：等级闯关按等级统计达标比例，改等级会让已解锁或已达标的等级前后对不上。
     *
     * @return 该任务的提交记录数
     */
    private long validateLevelUnchangedAfterSubmission(EduHomeworkDO current, EduHomeworkSaveReqVO reqVO) {
        long submittedCount = eduHomeworkRecordMapper.selectCount(EduHomeworkRecordDO::getHomeworkId, current.getId());
        if (submittedCount > 0 && reqVO.getHomeworkLevel() != null
                && !Objects.equals(reqVO.getHomeworkLevel(), current.getHomeworkLevel())) {
            throw exception(HOMEWORK_LEVEL_LOCKED_AFTER_SUBMISSION);
        }
        return submittedCount;
    }

    /**
     * 达标分数线允许修改，但已发布的任务改分数线会影响达标人数、能力分析和等级闯关，记入操作日志。
     */
    private void recordPassScoreChange(EduHomeworkDO current, EduHomeworkSaveReqVO reqVO, long submittedCount) {
        if (Objects.equals(current.getStatus(), EduHomeworkStatusEnum.DRAFT) || reqVO.getPassScore() == null
                || Objects.equals(reqVO.getPassScore(), current.getPassScore())) {
            return;
        }
        homeworkChangeLogService.recordPassScoreChange(current, current.getPassScore(), reqVO.getPassScore(),
                submittedCount);
    }

    @Override
    public Long startAiCreateHomeworkPreviewTask(EduHomeworkAiCreatePreviewReqVO reqVO) {
        return aiCreateService.startAiCreateHomeworkPreviewTask(reqVO);
    }

    @Override
    public EduHomeworkAiCreateTaskStatusRespVO getAiCreateHomeworkPreviewTaskStatus(Long taskId) {
        return aiCreateService.getAiCreateHomeworkPreviewTaskStatus(taskId);
    }

    @Override
    public void recoverAiCreateHomeworkPreviewTasks() {
        aiCreateService.recoverAiCreateHomeworkPreviewTasks();
    }

    /**
     * 单题重新生成改为后台执行：先把题目标记为“重新生成中”并立即返回，前端轮询任务状态接口取结果。
     * 结果按生成令牌写回，预览已确认或题目又被重新发起时丢弃旧结果；失败时保留原来的题目。
     */
    @Override
    public EduHomeworkAiCreatePreviewRespVO.Item regenerateAiCreateHomeworkQuestion(EduHomeworkAiCreateRegenerateReqVO reqVO) {
        return aiCreateService.regenerateAiCreateHomeworkQuestion(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduHomeworkAiCreateConfirmRespVO confirmAiCreateHomework(EduHomeworkAiCreateConfirmReqVO reqVO) {
        return aiCreateService.confirmAiCreateHomework(reqVO);
    }

    @Override
    public EduHomeworkStatisticsRespVO getHomeworkStatistics(Long homeworkId) {
        EduHomeworkDO homework = aiBusinessAccessService.requireHomeworkViewAccess(homeworkId);
        // 草稿没有分配学生；已归档的任务，以及班级、课程或班课归档后的任务只读统计
        if (homework == null || Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.DRAFT)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        return statisticsService.getHomeworkStatistics(homeworkId, homework);
    }

}
