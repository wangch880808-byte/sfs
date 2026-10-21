package cn.nus.zknu.module.edu.service.dataexchange.user;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeacherMasterDataExchangeHandler extends AbstractUserAccountDataExchangeHandler {

    public static final String BIZ_TYPE = "TEACHER_MASTER";

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public String getFileName() {
        return "教师账号导入模板_V1.xlsx";
    }

    @Override
    protected int userType() {
        return UserAccountDataExchangeSupport.TEACHER_USER_TYPE;
    }

    @Override
    protected String permission() {
        return "system:user:import";
    }

    @Override
    protected String identityRoleCode() {
        return support.getTeacherRoleCode();
    }

    @Override
    protected String accountLabel() {
        return "教师工号";
    }

    @Override
    protected boolean includeProfessionalTitle() {
        return true;
    }

    @Override
    protected List<String> rules() {
        return List.of(
                "必填项：教师工号、姓名（表头带 *）；其余字段可留空。教师工号和手机号必须保持文本格式。",
                "教师身份由导入入口固定，模板不接受用户类型字段。",
                "新建账号默认使用系统初始密码，确认导入前可统一覆写；已有账号不会重置密码。",
                "更新时空单元格不覆盖原值；已有非教师身份账号不会被转换为教师。");
    }

    @Override
    protected List<String> exampleRow() {
        return List.of("T20260001", "教师示例", "CS", "teacher@example.edu.cn",
                "13800138000", "男", "正常", "讲师");
    }

}
