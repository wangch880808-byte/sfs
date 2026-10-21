package cn.nus.zknu.module.edu.dal.dataobject.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import lombok.Data;

/** 分组键只供服务层使用，避免课程编号进入原有任务响应。 */
@Data
public class StudentCourseHomeworkRow {
    private Long courseId;
    private Long homeworkId;
    private AppStudentCourseDetailRespVO.Homework homework;
}
