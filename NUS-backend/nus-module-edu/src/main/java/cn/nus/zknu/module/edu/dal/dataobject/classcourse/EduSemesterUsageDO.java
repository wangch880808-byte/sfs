package cn.nus.zknu.module.edu.dal.dataobject.classcourse;

import lombok.Data;

import java.time.LocalDate;

/**
 * 班课关联里出现过的学期（按去首尾空格后的名称汇总），不是独立的表。
 */
@Data
public class EduSemesterUsageDO {

    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer linkCount;
    private Integer activeLinkCount;

}
