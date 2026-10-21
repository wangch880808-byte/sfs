package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.*;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;

import java.io.InputStream;

/**
 * 学生任务 Service 接口
 */
public interface StudentHomeworkService {

    /**
     * 我的任务列表（支持课程/状态筛选）
     */
    PageResult<AppStudentHomeworkRespVO> getHomeworkList(Long studentId, AppStudentHomeworkPageReqVO reqVO);

    /**
     * 任务详情（含提交记录、批改结果）
     */
    AppStudentHomeworkDetailRespVO getHomeworkDetail(Long studentId, Long homeworkId);

    /**
     * 提交任务
     */
    Long submitHomework(Long studentId, AppStudentHomeworkSubmitReqVO req);

    /**
     * 更新任务
     */
    void updateHomework(Long studentId, AppStudentHomeworkUpdateReqVO req);

    /**
     * 班级排名
     */

    /**
     * 上传作业附件，复用平台文件服务。
     */
    String uploadAttachment(Long studentId, Long homeworkId, InputStream content, long size, String name, String type);

    FileContentRespDTO getDownloadableAttachment(Long userId, String url);

}
