package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.util.MyBatisUtils;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 单独维护列表查询，避免改动其他分片共用的提交 Mapper。 */
@Mapper
public interface EduHomeworkRecordPageMapper {
    List<EduHomeworkRecordDO> selectOwnedRecords(IPage<EduHomeworkRecordDO> page,
            @Param("req") EduHomeworkRecordPageReqVO req, @Param("teacherId") Long teacherId,
            @Param("correction") boolean correction);

    default PageResult<EduHomeworkRecordDO> selectOwnedPage(EduHomeworkRecordPageReqVO req,
                                                           Long teacherId, boolean correction) {
        if (PageParam.PAGE_SIZE_NONE.equals(req.getPageSize())) {
            List<EduHomeworkRecordDO> records = selectOwnedRecords(null, req, teacherId, correction);
            return new PageResult<>(records, (long) records.size());
        }
        IPage<EduHomeworkRecordDO> page = MyBatisUtils.buildPage(req);
        List<EduHomeworkRecordDO> records = selectOwnedRecords(page, req, teacherId, correction);
        return new PageResult<>(records, page.getTotal());
    }

    List<RankRow> selectRanks(@Param("homeworkIds") Collection<Long> homeworkIds);

    @Data
    class RankRow {
        private Long homeworkId;
        private Long studentId;
        private Integer score;
        private Integer rank;
        private Integer total;
    }
}
