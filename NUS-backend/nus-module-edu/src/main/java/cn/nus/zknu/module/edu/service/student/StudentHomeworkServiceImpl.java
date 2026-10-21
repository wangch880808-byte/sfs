package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.*;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentHomeworkMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkArchiveAccessService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkExtensionService;
import cn.nus.zknu.module.edu.service.homework.HomeworkAttachmentLimits;
import cn.nus.zknu.module.edu.service.homework.HomeworkAttachmentService;
import cn.nus.zknu.module.edu.service.homework.HomeworkDeadlineUtils;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.infra.api.file.FileApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.io.InputStream;
import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_NOT_ASSIGNED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_DEADLINE_EXCEEDED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_INVALID;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;

/**
 * 学生任务 Service 实现类
 */
@Service
public class StudentHomeworkServiceImpl implements StudentHomeworkService {

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private EduHomeworkAssigneeMapper homeworkAssigneeMapper;
    @Resource
    private EduHomeworkRecordService homeworkRecordService;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private StudentHomeworkMapper studentHomeworkMapper;
    @Resource
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Resource
    private HomeworkAttachmentService homeworkAttachmentService;
    @Resource
    private FileApi fileApi;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private StudentCourseServiceImpl studentCourseService;
    @Resource
    private EduHomeworkExtensionService homeworkExtensionService;

    @Override
    public PageResult<AppStudentHomeworkRespVO> getHomeworkList(Long studentId, AppStudentHomeworkPageReqVO reqVO) {
        IPage<AppStudentHomeworkRespVO> page = studentHomeworkMapper.selectHomeworkPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), studentId,
                reqVO.getCourseId(), reqVO.getStatus(),
                AppStudentHomeworkPageReqVO.SCOPE_HISTORY.equals(reqVO.getScope()));
        fillDeadlines(studentId, page.getRecords());
        fillUnlockStatus(studentId, page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    private void fillUnlockStatus(Long studentId, List<AppStudentHomeworkRespVO> homeworks) {
        Map<Long, AppStudentUnlockStatusRespVO> courseStatuses = new HashMap<>();
        for (AppStudentHomeworkRespVO homework : homeworks) {
            homework.setLocked(false);
            homework.setLockReason(null);
            Integer level = homework.getHomeworkLevel();
            if (homework.getCourseId() == null || level == null || level <= 1
                    || level > StudentLevelUnlockCalculator.MAX_LEVEL) {
                continue;
            }
            AppStudentUnlockStatusRespVO status = courseStatuses.computeIfAbsent(homework.getCourseId(),
                    courseId -> studentCourseService.getUnlockStatus(studentId, courseId));
            for (AppStudentUnlockStatusRespVO.Level item : status.getLevels()) {
                if (Objects.equals(item.getLevel(), level)) {
                    homework.setLocked(!Boolean.TRUE.equals(item.getUnlocked()));
                    homework.setLockReason(homework.getLocked() ? item.getReasonText() : null);
                    break;
                }
            }
        }
    }

    @Override
    public AppStudentHomeworkDetailRespVO getHomeworkDetail(Long studentId, Long homeworkId) {
        EduHomeworkDO homework = homeworkMapper.selectById(homeworkId);
        EduHomeworkRecordDO record = homework == null ? null : selectRecord(homeworkId, studentId);
        // 只读查看按“历史可见”：班级、课程或班课归档后，学生仍能看到自己做过的任务和成绩
        if (!archiveAccessService.canViewHistory(homework, record != null)) {
            throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        }
        validateStudentAssigned(studentId, homeworkId);
        boolean current = archiveAccessService.canView(homework);
        if (current) {
            validateUnlockForNewTask(studentId, homework, record);
        }

        AppStudentHomeworkDetailRespVO resp = new AppStudentHomeworkDetailRespVO();
        resp.setHomeworkId(homework.getId());
        resp.setHomeworkName(homework.getHomeworkName());
        resp.setHomeworkType(homework.getHomeworkType());
        resp.setHomeworkLevel(homework.getHomeworkLevel());
        resp.setCourseId(homework.getCourseId());
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(homework.getCourseId());
        if (course != null) {
            resp.setCourseName(course.getCourseName());
        }
        resp.setRequirement(homework.getRequirement());
        resp.setSubmissionMode(homework.getSubmissionMode());
        resp.setAiReviewMode(homework.getAiReviewMode());
        resp.setRequiredAttachmentTypesJson(homework.getRequiredAttachmentTypesJson());
        resp.setDeadline(homework.getDeadline());
        resp.setScore(homework.getScore());
        resp.setPassScore(homework.getPassScore());

        // 查询提交记录
        if (record != null) {
            resp.setRecordId(record.getId());
            resp.setContent(record.getContent());
            resp.setAttachmentUrls(record.getAttachmentUrls());
            resp.setSubmitTime(record.getSubmitTime());
            resp.setMyScore(record.getScore());
            resp.setComment(record.getComment());
            resp.setCorrectType(record.getCorrectType());
            resp.setCorrectTime(record.getCorrectTime());
            resp.setStatus(record.getStatus());
            resp.setRedoCount(record.getRedoCount());
            resp.setRedoDeadline(record.getRedoDeadline());
        } else {
            resp.setStatus(EduHomeworkRecordStatusEnum.NOT_SUBMITTED); // 未提交
            resp.setRedoCount(0);
        }
        LocalDateTime personalDeadline = homeworkExtensionService.getPersonalDeadline(homeworkId, studentId);
        resp.setPersonalDeadline(HomeworkDeadlineUtils.getActivePersonalDeadline(homework.getDeadline(), personalDeadline));
        resp.setEffectiveDeadline(HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework, record, personalDeadline));
        resp.setHistoryOnly(!current);
        resp.setCanSubmit(current && archiveAccessService.canSubmit(homework) && canSubmit(homework, record, personalDeadline));
        resp.setAttachmentLimits(buildAttachmentLimits(homework));
        return resp;
    }

    /** 提交页在上传前按这些上限校验；最终仍以提交时的后端校验为准 */
    private AppStudentHomeworkDetailRespVO.AttachmentLimits buildAttachmentLimits(EduHomeworkDO homework) {
        HomeworkAttachmentLimits limits = homeworkAttachmentService.getLimits();
        AppStudentHomeworkDetailRespVO.AttachmentLimits result = new AppStudentHomeworkDetailRespVO.AttachmentLimits();
        result.setMaxCount(limits.maxCount());
        result.setMaxFileSizeMb(limits.maxFileMb());
        result.setMaxTotalSizeMb(limits.maxTotalMb());
        result.setAllowedTypes(homeworkAttachmentService.getAllowedTypes(homework));
        return result;
    }

    @Override
    public Long submitHomework(Long studentId, AppStudentHomeworkSubmitReqVO req) {
        // 以任务发布快照校验，避免转班导致历史任务口径漂移
        EduHomeworkDO homework = homeworkMapper.selectById(req.getHomeworkId());
        if (!archiveAccessService.canSubmit(homework)) {
            throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        }
        validateStudentAssigned(studentId, homework.getId());
        validateUnlockForNewTask(studentId, homework, selectRecord(homework.getId(), studentId));
        // 委托给已有的 RecordService
        EduHomeworkRecordSubmitReqVO submitReqVO = new EduHomeworkRecordSubmitReqVO();
        submitReqVO.setHomeworkId(req.getHomeworkId());
        submitReqVO.setContent(req.getContent());
        submitReqVO.setAttachmentUrls(req.getAttachmentUrls());
        return homeworkRecordService.submitRecord(submitReqVO, studentId);
    }

    @Override
    public void updateHomework(Long studentId, AppStudentHomeworkUpdateReqVO req) {
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(req.getId());
        if (record == null || !Objects.equals(record.getStudentId(), studentId)) {
            throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        }
        EduHomeworkDO homework = homeworkMapper.selectById(record.getHomeworkId());
        if (homework == null) throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        EduHomeworkRecordUpdateReqVO updateReqVO = new EduHomeworkRecordUpdateReqVO();
        updateReqVO.setId(req.getId());
        updateReqVO.setContent(req.getContent());
        updateReqVO.setAttachmentUrls(req.getAttachmentUrls());
        homeworkRecordService.updateRecord(updateReqVO, studentId);
    }

    @Override
    public String uploadAttachment(Long studentId, Long homeworkId, InputStream content, long size, String name, String type) {
        EduHomeworkDO homework = homeworkMapper.selectById(homeworkId);
        if (!archiveAccessService.canSubmit(homework)) {
            throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        }
        validateStudentAssigned(studentId, homeworkId);
        // 打回重做的记录以有效截止时间为准，其余情况仍以任务截止时间为准
        EduHomeworkRecordDO record = selectRecord(homeworkId, studentId);
        validateUnlockForNewTask(studentId, homework, record);
        LocalDateTime deadline = record != null && Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.REDO)
                ? HomeworkDeadlineUtils.getEffectiveDeadline(homework, record)
                : HomeworkDeadlineUtils.withPersonalDeadline(homework.getDeadline(),
                        homeworkExtensionService.getPersonalDeadline(homeworkId, studentId));
        if (HomeworkDeadlineUtils.isExpired(deadline)) {
            throw exception(HOMEWORK_RECORD_DEADLINE_EXCEEDED);
        }
        homeworkAttachmentService.validateUpload(homework, size, type);
        return fileApi.createFile(content, size, name, "edu/homework-submission/" + homeworkId, type);
    }

    @Override
    public FileContentRespDTO getDownloadableAttachment(Long userId, String url) {
        FileContentRespDTO file;
        try {
            file = fileApi.getFile(url);
        } catch (Exception ex) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件不是已登记的平台文件");
        }
        String prefix = "edu/homework-submission/";
        if (file.getPath() == null || !file.getPath().startsWith(prefix)) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件不属于任务提交目录");
        }
        String remainder = file.getPath().substring(prefix.length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件路径不合法");
        }
        Long homeworkId;
        try {
            homeworkId = Long.valueOf(remainder.substring(0, slash));
        } catch (NumberFormatException ex) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件路径不合法");
        }
        EduHomeworkDO homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw exception(STUDENT_HOMEWORK_NOT_EXISTS);
        }
        // 附件由学生本人上传，说明有过提交：按历史可见判断，归档后仍能下载自己的附件
        boolean ownerStudent = archiveAccessService.canViewHistory(homework, true)
                && String.valueOf(userId).equals(file.getCreator())
                && homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(homeworkId, userId) != null;
        boolean ownerTeacher = Objects.equals(homework.getTeacherId(), userId);
        boolean superAdmin = eduRoleApi.isSuperAdmin(userId);
        if (!ownerStudent && !ownerTeacher && !superAdmin) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "无权访问该提交附件");
        }
        return file;
    }

    private EduHomeworkRecordDO selectRecord(Long homeworkId, Long studentId) {
        return homeworkRecordMapper.selectOne(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                        .eq(EduHomeworkRecordDO::getStudentId, studentId));
    }

    private void validateUnlockForNewTask(Long studentId, EduHomeworkDO homework, EduHomeworkRecordDO record) {
        // 已经开始的任务（含打回重做）不受后来新增的前置任务影响。
        if (record == null) {
            studentCourseService.validateLevelUnlocked(studentId, homework.getCourseId(), homework.getHomeworkLevel());
        }
    }

    /**
     * 是否允许提交：任务已发布，且未提交时未过实际截止（含个别延期），或已打回时未过有效截止；其余状态不允许。
     */
    private boolean canSubmit(EduHomeworkDO homework, EduHomeworkRecordDO record, LocalDateTime personalDeadline) {
        if (!Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)) {
            return false;
        }
        if (record == null) {
            return !HomeworkDeadlineUtils.isExpired(
                    HomeworkDeadlineUtils.withPersonalDeadline(homework.getDeadline(), personalDeadline));
        }
        if (Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.REDO)) {
            return !HomeworkDeadlineUtils.isExpired(HomeworkDeadlineUtils.getEffectiveDeadline(homework, record));
        }
        return false;
    }

    /** 列表项补上个人截止时间与实际截止时间，规则与任务详情一致。 */
    private void fillDeadlines(Long studentId, List<AppStudentHomeworkRespVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, LocalDateTime> personalDeadlines = homeworkExtensionService.getPersonalDeadlineMap(studentId,
                items.stream().map(AppStudentHomeworkRespVO::getHomeworkId).filter(Objects::nonNull).toList());
        for (AppStudentHomeworkRespVO item : items) {
            LocalDateTime personal = personalDeadlines.get(item.getHomeworkId());
            EduHomeworkDO homework = new EduHomeworkDO();
            homework.setDeadline(item.getDeadline());
            EduHomeworkRecordDO record = null;
            if (item.getRecordId() != null) {
                record = new EduHomeworkRecordDO();
                record.setStatus(item.getStatus());
                record.setRedoDeadline(item.getRedoDeadline());
            }
            item.setPersonalDeadline(HomeworkDeadlineUtils.getActivePersonalDeadline(item.getDeadline(), personal));
            item.setEffectiveDeadline(HomeworkDeadlineUtils.getStudentEffectiveDeadline(homework, record, personal));
        }
    }

    private void validateStudentAssigned(Long studentId, Long homeworkId) {
        if (homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(homeworkId, studentId) == null) {
            throw exception(STUDENT_HOMEWORK_NOT_ASSIGNED);
        }
    }

}
