package cn.nus.zknu.module.system.api.user;

import java.util.Set;

/**
 * 用户管理改变班级关系后发布，由教务模块在当前事务内同步任务快照。
 *
 * @param studentId 学生 ID（改为非学生时仍使用原用户 ID）
 * @param addedClassIds 实际新增的班级 ID
 * @param removedClassIds 实际移出的班级 ID
 */
public record StudentClassesChangedEvent(Long studentId, Set<Long> addedClassIds, Set<Long> removedClassIds) {

    public StudentClassesChangedEvent {
        addedClassIds = Set.copyOf(addedClassIds);
        removedClassIds = Set.copyOf(removedClassIds);
    }
}
