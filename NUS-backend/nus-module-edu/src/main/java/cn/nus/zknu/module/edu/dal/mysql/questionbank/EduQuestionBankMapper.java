package cn.nus.zknu.module.edu.dal.mysql.questionbank;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import org.apache.ibatis.annotations.Mapper;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface EduQuestionBankMapper extends BaseMapperX<EduQuestionBankDO> {

    default List<EduQuestionBankDO> selectImportByTeacherId(Long teacherId) {
        // null 与原 Objects.equals 的语义一致：只读取 teacher_id IS NULL。
        return selectList(new LambdaQueryWrapperX<EduQuestionBankDO>()
                .eq(teacherId != null, EduQuestionBankDO::getTeacherId, teacherId)
                .isNull(teacherId == null, EduQuestionBankDO::getTeacherId));
    }
}
