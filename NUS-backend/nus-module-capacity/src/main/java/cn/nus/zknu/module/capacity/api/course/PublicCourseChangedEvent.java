package cn.nus.zknu.module.capacity.api.course;

/**
 * 课程成为在线公共课（公共课、审批通过、未归档）时发布，通知教务模块补齐班课关联。
 *
 * @param courseId 课程 ID
 */
public record PublicCourseChangedEvent(Long courseId) {
}
