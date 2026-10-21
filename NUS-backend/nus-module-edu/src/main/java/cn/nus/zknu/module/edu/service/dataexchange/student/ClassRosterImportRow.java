package cn.nus.zknu.module.edu.service.dataexchange.student;

import lombok.Data;

@Data
public class ClassRosterImportRow {

    private String username;
    private String nickname;
    private Integer sex;
    private String mobile;
    private String email;
    private String remark;
    private String majorCode;
    private Integer gradeYear;
    private Integer status;
    private String accountAction;
    private String relationAction;

}
