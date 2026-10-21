package cn.nus.zknu.module.edu.dal.mysql.importbatch;

import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EduImportBatchItemMapper extends BaseMapperX<EduImportBatchItemDO> {

    default List<EduImportBatchItemDO> selectListByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<EduImportBatchItemDO>()
                .eq(EduImportBatchItemDO::getBatchId, batchId)
                .orderByAsc(EduImportBatchItemDO::getRowNumber));
    }

    /** H2 不支持 MySQL 的 JSON_EXTRACT，保留容错解析但避免加载无关明细和大字段。 */
    default List<EduImportBatchItemDO> selectSkipReasonRows(Long batchId) {
        return selectList(new LambdaQueryWrapperX<EduImportBatchItemDO>()
                .select(EduImportBatchItemDO::getNormalizedData, EduImportBatchItemDO::getValidationStatus,
                        EduImportBatchItemDO::getAction)
                .eq(EduImportBatchItemDO::getBatchId, batchId)
                .eq(EduImportBatchItemDO::getValidationStatus, "VALID")
                .eq(EduImportBatchItemDO::getAction, "SKIP")
                .orderByAsc(EduImportBatchItemDO::getRowNumber));
    }

    default PageResult<EduImportBatchItemDO> selectPageByBatchId(PageParam pageParam, Long batchId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<EduImportBatchItemDO>()
                .eq(EduImportBatchItemDO::getBatchId, batchId)
                .orderByAsc(EduImportBatchItemDO::getRowNumber));
    }

}
