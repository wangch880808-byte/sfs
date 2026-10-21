package cn.nus.zknu.module.edu.controller.admin.dataexchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 组织班级导出 Request VO")
@Data
public class EduMasterDataExportReqVO {

    private String deptCode;
    private String deptType;
    private String deptName;
    private Integer deptStatus;
    private String className;
    private Integer gradeYear;
    private Long deptId;
    private Long educationLevel;
    private Long schoolingYear;
    private Integer classStatus;

}
