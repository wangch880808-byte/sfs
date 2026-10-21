package cn.nus.zknu.module.capacity.api.course;

import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 实训课程 API：供其他模块读取课程与课程团队，不直接访问 capacity 的表与 Mapper。
 */
public interface CapacityCourseApi {

    /**
     * 获得课程（不含已删除），不校验当前用户的访问范围。
     *
     * @return 不存在时返回 null
     */
    CapacityCourseRespDTO getCourse(Long id);

    /** 批量获得课程（不含已删除），不存在的 ID 忽略。 */
    List<CapacityCourseRespDTO> getCourseList(Collection<Long> ids);

    /** 批量获得课程，按 ID 索引。 */
    default Map<Long, CapacityCourseRespDTO> getCourseMap(Collection<Long> ids) {
        return CollectionUtils.convertMap(getCourseList(ids), CapacityCourseRespDTO::getId);
    }

    /** 全部在线公共课：公共课、审批通过、未归档、未删除。 */
    List<CapacityCourseRespDTO> getOnlinePublicCourseList();

    /** 课程团队成员（协作教师）用户 ID，按加入顺序；不含课程负责教师。 */
    List<Long> getCourseMemberUserIds(Long courseId);

    /** 多门课程的团队成员用户 ID；没有成员的课程不出现在结果里。 */
    Map<Long, List<Long>> getCourseMemberUserIdsMap(Collection<Long> courseIds);

    /** 用户是否为课程团队成员（不含课程负责教师）。 */
    boolean isCourseMember(Long courseId, Long userId);

    /**
     * 用户是否为课程管理员：持有公共课审批权限（capacity:course:audit），可查看、管理全部课程。
     */
    boolean isCourseAdmin(Long userId);

}
