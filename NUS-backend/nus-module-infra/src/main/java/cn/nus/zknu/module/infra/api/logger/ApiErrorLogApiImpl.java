package cn.nus.zknu.module.infra.api.logger;

import cn.nus.zknu.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.nus.zknu.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.nus.zknu.module.infra.service.logger.ApiErrorLogService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * API 访问日志的 API 接口
 *
 * @author hyh
 */
@Service
@Validated
public class ApiErrorLogApiImpl implements ApiErrorLogCommonApi {

    @Resource
    private ApiErrorLogService apiErrorLogService;

    @Override
    public void createApiErrorLog(ApiErrorLogCreateReqDTO createDTO) {
        apiErrorLogService.createApiErrorLog(createDTO);
    }

}
