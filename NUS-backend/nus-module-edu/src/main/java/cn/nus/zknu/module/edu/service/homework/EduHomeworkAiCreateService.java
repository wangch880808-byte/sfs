package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateConfirmReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateConfirmRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateRegenerateReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateTaskStatusRespVO;

/** AI 创建任务内部协作接口。 */
public interface EduHomeworkAiCreateService {

    Long startAiCreateHomeworkPreviewTask(EduHomeworkAiCreatePreviewReqVO reqVO);

    EduHomeworkAiCreateTaskStatusRespVO getAiCreateHomeworkPreviewTaskStatus(Long taskId);

    void recoverAiCreateHomeworkPreviewTasks();

    EduHomeworkAiCreatePreviewRespVO.Item regenerateAiCreateHomeworkQuestion(EduHomeworkAiCreateRegenerateReqVO reqVO);

    EduHomeworkAiCreateConfirmRespVO confirmAiCreateHomework(EduHomeworkAiCreateConfirmReqVO reqVO);
}
