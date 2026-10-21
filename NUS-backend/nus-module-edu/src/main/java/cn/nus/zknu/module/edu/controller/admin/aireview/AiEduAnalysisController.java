package cn.nus.zknu.module.edu.controller.admin.aireview;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiAnalysisClassReqVO;
import cn.nus.zknu.module.edu.service.aireview.AiEduAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@Tag(name = "管理后台 - AI分析")
@RestController
@RequestMapping("/edu/ai-review/analysis")
@Validated
public class AiEduAnalysisController {

    @Resource
    private AiEduAnalysisService aiEduAnalysisService;

    @PostMapping(value = "/class-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "班级得分分析(SSE)")
    @PreAuthorize("@ss.hasPermission('edu:ai-review:analysis')")
    public Flux<CommonResult<String>> analyzeClassScoreStream(@Valid @RequestBody AiAnalysisClassReqVO reqVO) {
        return Flux.defer(() -> aiEduAnalysisService.analyzeClassScoreStream(
                        reqVO.getClassId(), reqVO.getCourseId(), reqVO.getHomeworkId())
                        .map(CommonResult::success))
                .onErrorResume(ServiceException.class, exception -> Flux.just(CommonResult.error(exception)));
    }

}
