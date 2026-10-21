package cn.nus.zknu.module.edu.service.message;

import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 学生留言的教师接收人：与 {@code EduDataScopeService#validateClassCourseAccess} 同一口径，
 * 公共课按认领人（未认领时负责人）和班课助教接收，专属课保留课程负责人、团队和助教。
 * <p>
 * 同一班级与课程可能有多个学期的关联：优先取未归档的关联；全部归档时取全部关联，保证仍有人收到。
 */
@Component
public class EduMessageRecipientResolver {

    /** edu_class_course.status：0 正常。 */
    private static final int CLASS_COURSE_ACTIVE = 0;

    @Resource
    private EduClassCourseMapper classCourseMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduClassCourseAssistantService assistantService;

    public Set<Long> resolveTeacherRecipients(Long classId, Long courseId, CapacityCourseRespDTO course) {
        List<EduClassCourseDO> relations = classId == null || courseId == null ? List.of()
                : classCourseMapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>()
                .eq(EduClassCourseDO::getClassId, classId)
                .eq(EduClassCourseDO::getCourseId, courseId));
        List<EduClassCourseDO> chosen = chooseRelations(relations);
        List<Long> courseTeam = course == null || course.getId() == null || Boolean.TRUE.equals(course.getPublicCourse()) ? List.of()
                : capacityCourseApi.getCourseMemberUserIds(course.getId());
        Map<Long, List<Long>> assistants = chosen.isEmpty() ? Map.of()
                : assistantService.getTeacherIdsMap(chosen.stream().map(EduClassCourseDO::getId)
                .filter(Objects::nonNull).toList());
        return collectRecipients(course, courseTeam, chosen, assistants);
    }

    /** 优先取未归档的关联；全部归档时取全部关联。 */
    static List<EduClassCourseDO> chooseRelations(List<EduClassCourseDO> relations) {
        List<EduClassCourseDO> safeRelations = relations == null ? List.of()
                : relations.stream().filter(Objects::nonNull).toList();
        List<EduClassCourseDO> active = safeRelations.stream()
                .filter(relation -> relation.getStatus() == null || relation.getStatus() == CLASS_COURSE_ACTIVE)
                .toList();
        return active.isEmpty() ? safeRelations : active;
    }

    static Set<Long> collectRecipients(CapacityCourseRespDTO course, Collection<Long> courseTeam,
                                       List<EduClassCourseDO> relations, Map<Long, List<Long>> assistants) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (course != null && Boolean.TRUE.equals(course.getPublicCourse())) {
            if (relations != null) {
                relations.stream().map(EduClassCourseDO::getClaimTeacherId)
                        .filter(id -> id != null && id > 0).forEach(recipients::add);
            }
            // 未认领的班级仍要有人接收留言，按课程负责人兜底，不通知课程团队。
            if ((relations == null || relations.isEmpty()
                    || relations.stream().anyMatch(relation -> relation.getClaimTeacherId() == null))
                    && course.getTeacherId() != null && course.getTeacherId() > 0) {
                recipients.add(course.getTeacherId());
            }
        } else {
            if (course != null && course.getTeacherId() != null && course.getTeacherId() > 0) {
                recipients.add(course.getTeacherId());
            }
            addIds(recipients, courseTeam);
        }
        if (relations != null && assistants != null) {
            for (EduClassCourseDO relation : relations) {
                addIds(recipients, assistants.get(relation.getId()));
            }
        }
        return recipients;
    }

    private static void addIds(Set<Long> target, Collection<Long> ids) {
        if (ids == null) {
            return;
        }
        ids.stream().filter(id -> id != null && id > 0).forEach(target::add);
    }

}
