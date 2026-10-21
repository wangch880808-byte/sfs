package cn.nus.zknu.module.edu.service.dataexchange.organization;

import lombok.Data;

@Data
public class ClassImportRow {

    private Long classId;
    private String className;
    private String majorCode;
    private Integer gradeYear;
    private Long educationLevel;
    private Long schoolingYear;
    private Integer status;
    private String remark;

}
