package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRemindLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

/** 多值插入使提醒占位在批次内原子成功；唯一键冲突时由服务逐条重试。 */
@Mapper
public interface EduHomeworkRemindBatchMapper {
    int insertLogs(@Param("rows") List<EduHomeworkRemindLogDO> rows, @Param("operator") String operator);
    int deleteFailedLogs(@Param("ids") Collection<Long> ids);
}
