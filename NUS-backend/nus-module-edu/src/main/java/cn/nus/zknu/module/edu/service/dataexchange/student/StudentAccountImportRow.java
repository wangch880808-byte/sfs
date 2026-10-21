package cn.nus.zknu.module.edu.service.dataexchange.student;

import lombok.Data;

@Data
public class StudentAccountImportRow {

    private String username;
    private String nickname;
    private Integer sex;
    private String majorCode;
    private Integer gradeYear;
    private Long classId;
    private String mobile;
    private String email;
    private Integer status;

}
