package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRemindLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EduHomeworkRemindLogMapper extends BaseMapperX<EduHomeworkRemindLogDO> {

    default List<EduHomeworkRemindLogDO> selectListByHomeworkIdAndType(Long homeworkId, String remindType) {
        return selectList(new LambdaQueryWrapperX<EduHomeworkRemindLogDO>()
                .eq(EduHomeworkRemindLogDO::getHomeworkId, homeworkId)
                .eq(EduHomeworkRemindLogDO::getRemindType, remindType));
    }

    /** 发送失败时撤销占位，下次运行可以重试；占位行只是去重标记，不是业务数据。 */
    @Delete("DELETE FROM edu_homework_remind_log WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);

}
