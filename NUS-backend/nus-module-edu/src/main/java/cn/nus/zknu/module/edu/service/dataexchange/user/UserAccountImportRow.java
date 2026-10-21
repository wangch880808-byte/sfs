package cn.nus.zknu.module.edu.service.dataexchange.user;

import lombok.Data;

@Data
public class UserAccountImportRow {

    private String username;
    private String nickname;
    private String deptCode;
    private String email;
    private String mobile;
    private Integer sex;
    private Integer status;
    private Integer professionalTitle;

}
