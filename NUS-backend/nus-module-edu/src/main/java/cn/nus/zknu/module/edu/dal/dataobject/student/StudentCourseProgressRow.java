package cn.nus.zknu.module.edu.dal.dataobject.student;

import lombok.Data;

@Data
public class StudentCourseProgressRow {
    private Long courseId;
    private Integer totalHomework;
    private Integer completedHomework;
    private Double avgScore;
}
