package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_RECORD_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_NOT_EXISTS;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * AI 业务统一沿用课程、班级和作业的数据范围，避免仅凭功能权限访问他人数据。
 */
@Service
public class EduAiBusinessAccessService {

    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private EduClassCourseMapper classCourseMapper;

    public EduHomeworkRecordDO requireRecordAccess(Long recordId) {
        EduHomeworkRecordDO record = recordId == null ? null : homeworkRecordMapper.selectById(recordId);
        if (record == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        requireHomeworkCorrectionAccess(record.getHomeworkId());
        return record;
    }

    /** 记录只读沿用任务查看范围，归档班课也可查看历史成绩。 */
    public EduHomeworkDO requireHomeworkViewAccess(Long homeworkId) {
        EduHomeworkDO homework = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        validateHomeworkViewAccess(homework);
        return homework;
    }

    public void validateHomeworkViewAccess(EduHomeworkDO homework) {
        Long userId = getLoginUserId();
        if (userId == null || (!Objects.equals(homework.getTeacherId(), userId)
                && !(dataScopeService.isPublicCourse(homework.getCourseId())
                ? dataScopeService.canViewAllPublic(homework.getCourseId())
                : dataScopeService.canManageCourse(homework.getCourseId())))) {
            throw exception(FORBIDDEN);
        }
    }

    public EduHomeworkRecordDO requireRecordViewAccess(Long recordId) {
        EduHomeworkRecordDO record = recordId == null ? null : homeworkRecordMapper.selectById(recordId);
        if (record == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        requireHomeworkViewAccess(record.getHomeworkId());
        return record;
    }

    /** AI 批改与反馈仍只允许任务布置者操作。 */
    public EduHomeworkDO requireHomeworkCorrectionAccess(Long homeworkId) {
        EduHomeworkDO homework = requireHomeworkAccess(homeworkId);
        if (!Objects.equals(homework.getTeacherId(), getLoginUserId())) {
            throw exception(FORBIDDEN);
        }
        return homework;
    }

    public EduHomeworkDO requireHomeworkAccess(Long homeworkId) {
        EduHomeworkDO homework = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        if (dataScopeService.isPublicCourse(homework.getCourseId())) {
            // 认领可以随时放弃，历史任务仍按布置者授权，不依赖班课当前是否在用。
            if (getLoginUserId() == null || (!Objects.equals(homework.getTeacherId(), getLoginUserId())
                    && !dataScopeService.isGlobalAdmin())) {
                throw exception(FORBIDDEN);
            }
        } else {
            validateCourseClassAccess(homework.getCourseId(), homework.getClassId());
        }
        return homework;
    }

    public void validateCourseClassAccess(Long courseId, Long classId) {
        dataScopeService.validateCourseAccess(courseId);
        if (dataScopeService.isPublicCourse(courseId)) {
            if (!dataScopeService.canAssignToPublicClass(courseId, classId)) {
                throw exception(FORBIDDEN);
            }
        } else {
            dataScopeService.validateClassAccess(classId);
        }
        validateActiveClassCourse(courseId, classId);
    }

    private void validateActiveClassCourse(Long courseId, Long classId) {
        EduClassCourseDO relation = classCourseMapper.selectOne(
                new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getCourseId, courseId)
                        .eq(EduClassCourseDO::getClassId, classId)
                        .eq(EduClassCourseDO::getStatus, 0)
                        .last("LIMIT 1"));
        if (relation == null) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
    }

    public void validateCourseClassViewAccess(Long courseId, Long classId) {
        if (!dataScopeService.isPublicCourse(courseId)) {
            validateCourseClassAccess(courseId, classId);
            return;
        }
        dataScopeService.validateClassCourseAccess(classId, courseId);
        // 研判沿用原来只分析在用班课的范围，只把授权从布置改为查看。
        validateActiveClassCourse(courseId, classId);
    }

    public void validateCourseAccess(Long courseId) {
        if (courseId == null) {
            throw exception(FORBIDDEN);
        }
        dataScopeService.validateCourseAccess(courseId);
    }

    public void validateClassAccess(Long classId) {
        if (classId == null) {
            throw exception(FORBIDDEN);
        }
        dataScopeService.validateClassAccess(classId);
    }
}
