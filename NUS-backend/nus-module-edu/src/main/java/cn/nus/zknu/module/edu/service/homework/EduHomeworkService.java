package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;

/**
 * 实训任务 Service 接口
 */
public interface EduHomeworkService {

    /**
     * 创建实训任务
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createHomework(EduHomeworkSaveReqVO createReqVO);

    /**
     * 生成 AI新建任务预览
     *
     * @param reqVO 预览请求
     * @return 预览结果
     */
    Long startAiCreateHomeworkPreviewTask(EduHomeworkAiCreatePreviewReqVO reqVO);

    EduHomeworkAiCreateTaskStatusRespVO getAiCreateHomeworkPreviewTaskStatus(Long taskId);

    void recoverAiCreateHomeworkPreviewTasks();

    /**
     * 重新生成 AI新建任务单题预览
     *
     * @param reqVO 重新生成请求
     * @return 预览题目
     */
    EduHomeworkAiCreatePreviewRespVO.Item regenerateAiCreateHomeworkQuestion(EduHomeworkAiCreateRegenerateReqVO reqVO);

    /**
     * 确认 AI新建任务并批量创建实训任务
     *
     * @param reqVO 确认请求
     * @return 创建结果
     */
    EduHomeworkAiCreateConfirmRespVO confirmAiCreateHomework(EduHomeworkAiCreateConfirmReqVO reqVO);

    /**
     * 更新实训任务
     *
     * @param updateReqVO 更新信息
     */
    void updateHomework(EduHomeworkSaveReqVO updateReqVO);

    /**
     * 删除实训任务（级联删除任务记录）
     *
     * @param id 编号
     */
    void deleteHomework(Long id);

    void restoreHomework(Long id);

    /**
     * 获取实训任务详情
     *
     * @param id 编号
     * @return 实训任务
     */
    EduHomeworkDO getHomework(Long id);

    /**
     * 分页查询实训任务
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduHomeworkDO> getHomeworkPage(EduHomeworkPageReqVO reqVO);

    /**
     * 获取任务统计
     *
     * @param homeworkId 任务 ID
     * @return 统计信息
     */
    EduHomeworkStatisticsRespVO getHomeworkStatistics(Long homeworkId);

}
