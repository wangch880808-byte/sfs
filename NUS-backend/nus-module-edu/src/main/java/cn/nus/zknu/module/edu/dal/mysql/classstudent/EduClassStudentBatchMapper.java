package cn.nus.zknu.module.edu.dal.mysql.classstudent;

import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 班级成员变更专用，避免逐学生执行恢复和画像更新。 */
@Mapper
public interface EduClassStudentBatchMapper {
    int restoreRelations(@Param("ids") Collection<Long> ids, @Param("status") Integer status,
                         @Param("joinTime") LocalDateTime joinTime, @Param("remark") String remark);
    int insertRelations(@Param("rows") List<EduClassStudentDO> rows, @Param("operator") String operator);
    List<EduHomeworkAssigneeDO> selectAssigneesIncludingDeleted(@Param("homeworkIds") Collection<Long> homeworkIds,
                                                              @Param("studentIds") Collection<Long> studentIds);
    int restoreAssignees(@Param("ids") Collection<Long> ids);
    int removeAssignees(@Param("ids") Collection<Long> ids);
    int insertAssignees(@Param("rows") List<EduHomeworkAssigneeDO> rows, @Param("operator") String operator);
    int insertProfiles(@Param("rows") List<UserProfileDO> rows, @Param("operator") String operator);
    int updateProfiles(@Param("rows") List<UserProfileDO> rows, @Param("operator") String operator);
}
