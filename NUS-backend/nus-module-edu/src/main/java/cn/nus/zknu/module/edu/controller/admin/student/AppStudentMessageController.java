package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageConversationRespVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageStudentAddReqVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.StudentConversationRespVO;
import cn.nus.zknu.module.edu.service.message.EduMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "学生端 - 我的留言")
@RestController
@RequestMapping("/edu/student/message")
public class AppStudentMessageController {

    @Resource
    private EduMessageService messageService;

    @GetMapping("/list")
    @Operation(summary = "我的班课会话")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-message:query')")
    public CommonResult<List<StudentConversationRespVO>> list() {
        return success(messageService.getMyConversations());
    }

    @GetMapping("/detail")
    @Operation(summary = "我的会话详情并标记教师消息已读")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-message:query')")
    public CommonResult<MessageConversationRespVO> detail(@RequestParam("classId") Long classId,
                                                           @RequestParam("courseId") Long courseId) {
        return success(messageService.getMyDetail(classId, courseId));
    }

    @PostMapping("/send")
    @Operation(summary = "发送留言")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-message:send')")
    public CommonResult<Boolean> send(@Valid @RequestBody MessageStudentAddReqVO reqVO) {
        messageService.studentAdd(reqVO.getClassId(), reqVO.getCourseId(), reqVO.getContent());
        return success(true);
    }

    @PutMapping("/read")
    @Operation(summary = "标记教师消息已读")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-message:query')")
    public CommonResult<Boolean> read(@RequestParam("classId") Long classId,
                                      @RequestParam("courseId") Long courseId) {
        messageService.markMyRead(classId, courseId);
        return success(true);
    }
}
