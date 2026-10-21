package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentListReqVO;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentPageReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;

import java.util.Collection;
import java.util.List;

/**
 * 班级学生关联 Service
 */
public interface EduClassStudentService {

    /**
     * 获得可加入学生分页
     *
     * @param reqVO 查询条件
     * @return 分页数据
     */
    PageResult<AdminUserDO> getAvailableStudentPage(EduClassStudentPageReqVO reqVO);

    /**
     * 获得班级已加入学生列表
     *
     * @param reqVO 查询条件
     * @return 学生列表
     */
    List<AdminUserDO> getAssignedStudentList(EduClassStudentListReqVO reqVO);

    /**
     * 批量加入学生
     *
     * @param classId    班级 ID
     * @param studentIds 学生 ID 列表
     */
    void addStudents(Long classId, Collection<Long> studentIds, String remark);

    /**
     * 批量移出学生
     *
     * @param classId    班级 ID
     * @param studentIds 学生 ID 列表
     */
    void removeStudents(Long classId, Collection<Long> studentIds);

    /**
     * 班级关系及画像已由用户管理写入，同一事务内补齐任务快照和入班通知。
     *
     * @param classId 班级 ID
     * @param studentIds 实际发生关系变更的学生 ID
     * @param joining 是否入班
     */
    void syncAssigneesForMembershipChange(Long classId, Collection<Long> studentIds, boolean joining);

    /**
     * 检查加入班级是否需要备注
     *
     * @param classId    班级 ID
     * @param studentIds 学生 ID 列表
     * @return 是否需要备注
     */
    boolean isAddRemarkRequired(Long classId, Collection<Long> studentIds);

    /**
     * 按班级批量删除关联关系
     *
     * @param classIds 班级 ID 列表
     */
    void deleteByClassIds(Collection<Long> classIds);

}
