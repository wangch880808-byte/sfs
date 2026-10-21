package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsSourceRespVO;
import cn.nus.zknu.module.edu.service.student.StudentNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "学生端 - 校园资讯")
@RestController
@RequestMapping("/edu/student/news")
@Validated
public class AppStudentNewsController {

    @Resource
    private StudentNewsService studentNewsService;

    @GetMapping("/sources")
    @Operation(summary = "资讯来源列表")
    public CommonResult<List<AppStudentNewsSourceRespVO>> getNewsSources() {
        return success(studentNewsService.getNewsSources());
    }

    @GetMapping("/page")
    @Operation(summary = "资讯分页列表")
    public CommonResult<PageResult<AppStudentNewsRespVO>> getNewsPage(@Validated AppStudentNewsPageReqVO reqVO) {
        return success(studentNewsService.getNewsPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "资讯详情")
    @Parameter(name = "id", description = "资讯 ID", required = true, example = "1")
    public CommonResult<AppStudentNewsDetailRespVO> getNews(@RequestParam("id") Long id) {
        return success(studentNewsService.getNews(id));
    }

}
