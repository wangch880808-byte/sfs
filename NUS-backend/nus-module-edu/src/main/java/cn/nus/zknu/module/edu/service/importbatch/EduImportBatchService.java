package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchRespVO;
import org.springframework.web.multipart.MultipartFile;

public interface EduImportBatchService {

    EduImportBatchRespVO preview(String bizType, String templateVersion, String optionsJson, MultipartFile file);

    EduImportBatchRespVO confirm(String batchNo, String initialPassword);

    EduImportBatchRespVO cancel(String batchNo);

    EduImportBatchRespVO get(String batchNo);

    PageResult<EduImportBatchRespVO> getPage(EduImportBatchPageReqVO reqVO);

    PageResult<EduImportBatchItemRespVO> getItemPage(String batchNo, PageParam pageParam);

    String getErrorFileDownloadUrl(String batchNo);

}
