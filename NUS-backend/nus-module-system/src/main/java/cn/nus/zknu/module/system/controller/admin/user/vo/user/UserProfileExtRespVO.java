package cn.nus.zknu.module.system.controller.admin.user.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - 用户扩展信息 Response VO")
@Data
public class UserProfileExtRespVO {

    @Schema(description = "自增 ID", example = "1")
    private Long id;

    @Schema(description = "用户 ID", example = "1024")
    private Long userId;

    @Schema(description = "用户类型", example = "1")
    private Integer userType;

    @Schema(description = "职称", example = "1")
    private Integer professionalTitle;

    @Schema(description = "入学年份", example = "2026")
    private Integer gradeYear;

    @Schema(description = "班级 ID 数组", example = "[1,2,3]")
    private Set<Long> classIds;

}
