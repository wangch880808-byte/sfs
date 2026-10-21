package cn.nus.zknu.module.capacity.service.course;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 课程团队成员（协作教师）Service，数据在 capacity_course_member。
 */
public interface CapacityCourseMemberService {

    /** 课程团队成员用户 ID，按加入顺序；没有成员时返回空列表。 */
    List<Long> getMemberUserIds(Long courseId);

    /** 多门课程的团队成员用户 ID；没有成员的课程不出现在结果里。 */
    Map<Long, List<Long>> getMemberUserIdsMap(Collection<Long> courseIds);

    /** 用户是否为课程团队成员（不含课程负责教师）。 */
    boolean isMember(Long courseId, Long userId);

    /**
     * 把课程团队替换为给定成员：新增缺少的、逻辑删除多余的，已有成员保持不动。
     *
     * @param courseId 课程 ID
     * @param userIds  目标成员，可为空表示清空团队
     */
    void replaceMembers(Long courseId, Collection<Long> userIds);

}
