package cn.nus.zknu.module.system.service.user.classstudent;

import java.util.Collection;
import java.util.Set;

/**
 * 用户-班级关联 Service（system 模块侧）
 */
public interface UserClassStudentService {

    /**
     * 同步学生班级关联
     *
     * @param userId   用户 ID
     * @param userType 用户类型
     * @param classIds 班级 ID 列表
     */
    void syncStudentClasses(Long userId, Integer userType, Set<Long> classIds);

    /**
     * 按学生删除关联
     *
     * @param studentId 学生 ID
     */
    void deleteByStudentId(Long studentId);

    /**
     * 批量按学生删除关联
     *
     * @param studentIds 学生 ID 列表
     */
    void deleteByStudentIds(Collection<Long> studentIds);

}
