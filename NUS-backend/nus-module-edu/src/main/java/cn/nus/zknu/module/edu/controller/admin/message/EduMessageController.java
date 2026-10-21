package cn.nus.zknu.module.edu.controller.admin.message;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.message.vo.*;
import cn.nus.zknu.module.edu.service.message.EduMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 留言互动")
@RestController
@RequestMapping("/edu/message")
@Validated
public class EduMessageController {

    @Resource
    private EduMessageService eduMessageService;

    @GetMapping("/student-list")
    @Operation(summary = "获取留言学生列表")
    @PreAuthorize("@ss.hasPermission('edu:message:query')")
    public CommonResult<List<MessageStudentListRespVO>> getStudentList(
            @RequestParam("classId") Long classId,
            @RequestParam("courseId") Long courseId,
            @RequestParam(value = "name", required = false) String name) {
        return success(eduMessageService.getStudentList(classId, courseId, name));
    }

    @GetMapping("/detail")
    @Operation(summary = "获取留言详情")
    @PreAuthorize("@ss.hasPermission('edu:message:query')")
    public CommonResult<MessageConversationRespVO> getDetail(
            @RequestParam("classId") Long classId,
            @RequestParam("courseId") Long courseId,
            @RequestParam("studentId") Long studentId) {
        return success(eduMessageService.getDetail(classId, courseId, studentId));
    }

    @PostMapping("/reply")
    @Operation(summary = "教师回复留言")
    @PreAuthorize("@ss.hasPermission('edu:message:manage')")
    public CommonResult<Boolean> reply(@Valid @RequestBody MessageReplyReqVO reqVO) {
        eduMessageService.reply(reqVO.getClassId(), reqVO.getCourseId(), reqVO.getStudentId(), reqVO.getContent());
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭会话")
    @PreAuthorize("@ss.hasPermission('edu:message:manage')")
    public CommonResult<Boolean> closeConversation(@RequestParam("classId") Long classId,
                                                    @RequestParam("courseId") Long courseId,
                                                    @RequestParam("studentId") Long studentId) {
        eduMessageService.closeConversation(classId, courseId, studentId);
        return success(true);
    }

    @PutMapping("/reopen")
    @Operation(summary = "重新开启会话")
    @PreAuthorize("@ss.hasPermission('edu:message:manage')")
    public CommonResult<Boolean> reopenConversation(@RequestParam("classId") Long classId,
                                                     @RequestParam("courseId") Long courseId,
                                                     @RequestParam("studentId") Long studentId) {
        eduMessageService.reopenConversation(classId, courseId, studentId);
        return success(true);
    }

}
