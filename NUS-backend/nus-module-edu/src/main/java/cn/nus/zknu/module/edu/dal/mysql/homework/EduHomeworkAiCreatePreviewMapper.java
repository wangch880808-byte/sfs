package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EduHomeworkAiCreatePreviewMapper extends BaseMapperX<EduHomeworkAiCreatePreviewDO> {

    default EduHomeworkAiCreatePreviewDO selectByPreviewId(String previewId) {
        return selectOne(EduHomeworkAiCreatePreviewDO::getPreviewId, previewId);
    }

    default EduHomeworkAiCreatePreviewDO selectByTeacherAndClientRequestId(Long teacherId, String clientRequestId) {
        return selectOne(new LambdaQueryWrapperX<EduHomeworkAiCreatePreviewDO>()
                .eq(EduHomeworkAiCreatePreviewDO::getTeacherId, teacherId)
                .eq(EduHomeworkAiCreatePreviewDO::getClientRequestId, clientRequestId));
    }

    default List<EduHomeworkAiCreatePreviewDO> selectPendingTasks(int limit) {
        return selectList(new LambdaQueryWrapperX<EduHomeworkAiCreatePreviewDO>()
                .eq(EduHomeworkAiCreatePreviewDO::getTaskStatus, "PENDING")
                .orderByAsc(EduHomeworkAiCreatePreviewDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

}
