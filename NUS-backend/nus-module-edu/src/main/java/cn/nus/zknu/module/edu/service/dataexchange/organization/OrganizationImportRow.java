package cn.nus.zknu.module.edu.service.dataexchange.organization;

import lombok.Data;

@Data
public class OrganizationImportRow {

    private String deptCode;
    private String name;
    private String deptType;
    private String parentDeptCode;
    private Integer sort;
    private Integer status;
    private String leaderUsername;
    private Long leaderUserId;
    private String phone;
    private String email;

}
