package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_REDO_SCOPE_ARCHIVED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.MESSAGE_SCOPE_ARCHIVED;

/**
 * 学生端任务可见性及提交校验。
 * <p>
 * 分两种口径：
 * <ul>
 *     <li>可提交（{@link #canSubmit}）与当前可见（{@link #canView}）：任务已发布，且班级、课程、班课都在用；</li>
 *     <li>历史可见（{@link #canViewHistory}）：只读查看成绩、评语与轨迹，不要求班级、课程、班课仍在用，
 *     归档上学期的班级后学生仍能看到自己做过的任务。</li>
 * </ul>
 */
@Service
public class EduHomeworkArchiveAccessService {

    private static final int ARCHIVED = 1;

    @Resource
    private EduClassMapper classMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private EduHomeworkMapper homeworkMapper;

    public boolean canView(EduHomeworkDO homework) {
        return homework != null && (Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)
                || Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.CLOSED)) && isInActiveScope(homework);
    }

    public boolean canSubmit(EduHomeworkDO homework) {
        return homework != null && Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.PUBLISHED)
                && isInActiveScope(homework);
    }

    /**
     * 历史可见（只读）：已发布、已截止的任务，或已归档但学生提交过的任务；不看班级、课程、班课是否归档。
     * 是否分配给该学生由调用方另行校验。
     *
     * @param hasRecord 学生是否有提交记录
     */
    public boolean canViewHistory(EduHomeworkDO homework, boolean hasRecord) {
        if (homework == null) {
            return false;
        }
        Integer status = homework.getStatus();
        return Objects.equals(status, EduHomeworkStatusEnum.PUBLISHED)
                || Objects.equals(status, EduHomeworkStatusEnum.CLOSED)
                || (Objects.equals(status, EduHomeworkStatusEnum.ARCHIVED) && hasRecord);
    }

    public boolean isInActiveScope(EduHomeworkDO homework) {
        if (homework == null) {
            return false;
        }
        EduClassDO clazz = classMapper.selectById(homework.getClassId());
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(homework.getCourseId());
        if (clazz == null || course == null || !Objects.equals(clazz.getStatus(), 0)
                || !Objects.equals(course.getStatus(), 0)) {
            return false;
        }
        return hasActiveBinding(homework.getClassId(), homework.getCourseId(), homework.getSemesterName());
    }

    /**
     * 打回前确认任务所在的班级、课程、班课都在用；归档后的成绩只读。
     */
    public void validateRedoScopeActive(Long homeworkId) {
        EduHomeworkDO homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            return;
        }
        String archived = archivedScopeLabel(homework.getClassId(), homework.getCourseId(),
                homework.getSemesterName());
        if (archived != null) {
            throw exception(HOMEWORK_REDO_SCOPE_ARCHIVED, archived, archived);
        }
    }

    /**
     * 回复留言前确认班级、课程、班课都在用；归档后的留言只读。
     */
    public void validateMessageScopeActive(Long classId, Long courseId) {
        String archived = archivedScopeLabel(classId, courseId, null);
        if (archived != null) {
            throw exception(MESSAGE_SCOPE_ARCHIVED, archived, archived);
        }
    }

    /**
     * 第一个已归档的上级对象名称（班级、课程、班课）；都在用时返回 null。
     */
    public String archivedScopeLabel(Long classId, Long courseId, String semesterName) {
        EduClassDO clazz = classMapper.selectById(classId);
        if (clazz == null || Objects.equals(clazz.getStatus(), ARCHIVED)) {
            return "班级";
        }
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (course == null || Objects.equals(course.getStatus(), ARCHIVED)) {
            return "课程";
        }
        return hasActiveBinding(classId, courseId, semesterName) ? null : "班课";
    }

    private boolean hasActiveBinding(Long classId, Long courseId, String semesterName) {
        return classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getClassId, classId)
                .eq(EduClassCourseDO::getCourseId, courseId)
                .eq(EduClassCourseDO::getStatus, 0)).stream()
                .anyMatch(binding -> StrUtil.isBlank(semesterName)
                        || StrUtil.isBlank(binding.getSemesterName())
                        || StrUtil.equals(StrUtil.trim(semesterName),
                                StrUtil.trim(binding.getSemesterName())));
    }
}
