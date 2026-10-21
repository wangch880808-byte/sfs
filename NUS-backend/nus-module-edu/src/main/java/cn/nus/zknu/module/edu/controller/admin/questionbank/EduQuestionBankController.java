package cn.nus.zknu.module.edu.controller.admin.questionbank;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankRespVO;
import cn.nus.zknu.module.edu.service.questionbank.EduQuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 题库管理")
@RestController
@RequestMapping("/edu/question-bank")
@Validated
public class EduQuestionBankController {

    @Resource
    private EduQuestionBankService eduQuestionBankService;

    @GetMapping("/page")
    @Operation(summary = "分页查询题库")
    @PreAuthorize("@ss.hasPermission('edu:question-bank:query')")
    public CommonResult<PageResult<QuestionBankRespVO>> getPage(@Validated QuestionBankPageReqVO reqVO) {
        return success(eduQuestionBankService.getPage(reqVO));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清空题库")
    @PreAuthorize("@ss.hasPermission('edu:question-bank:manage')")
    public CommonResult<Boolean> clear() {
        eduQuestionBankService.clearByTeacher();
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除题目")
    @Parameter(name = "id", description = "题目ID", required = true)
    @PreAuthorize("@ss.hasPermission('edu:question-bank:manage')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        eduQuestionBankService.delete(id);
        return success(true);
    }

}
