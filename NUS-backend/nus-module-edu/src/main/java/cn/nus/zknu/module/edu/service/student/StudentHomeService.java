package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeOverviewRespVO;

/**
 * 学生首页 Service 接口
 */
public interface StudentHomeService {

    /**
     * 获取首页概览（待完成任务数、近期成绩、未读消息数）
     */
    AppStudentHomeOverviewRespVO getOverview(Long studentId);

}
