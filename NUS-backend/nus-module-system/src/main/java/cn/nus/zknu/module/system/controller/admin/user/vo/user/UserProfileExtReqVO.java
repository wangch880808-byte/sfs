package cn.nus.zknu.module.system.controller.admin.user.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - 用户扩展信息 Request VO")
@Data
public class UserProfileExtReqVO {

    @Schema(description = "职称", example = "1")
    private Integer professionalTitle;

    @Schema(description = "入学年份", example = "2026")
    private Integer gradeYear;

    @Schema(description = "班级 ID 数组", example = "[1,2,3]")
    private Set<Long> classIds;

}
