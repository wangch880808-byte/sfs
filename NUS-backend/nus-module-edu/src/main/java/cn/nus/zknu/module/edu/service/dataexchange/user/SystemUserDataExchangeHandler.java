package cn.nus.zknu.module.edu.service.dataexchange.user;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SystemUserDataExchangeHandler extends AbstractUserAccountDataExchangeHandler {

    public static final String BIZ_TYPE = "SYSTEM_USER";

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public String getFileName() {
        return "系统用户导入模板_V1.xlsx";
    }

    @Override
    protected int userType() {
        return UserAccountDataExchangeSupport.SYSTEM_USER_TYPE;
    }

    @Override
    protected String permission() {
        return "system:user:import";
    }

    @Override
    protected String identityRoleCode() {
        return null;
    }

    @Override
    protected String accountLabel() {
        return "登录账号";
    }

    @Override
    protected boolean includeProfessionalTitle() {
        return false;
    }

    @Override
    protected List<String> rules() {
        return List.of(
                "必填项：登录账号、姓名（表头带 *）；其余字段可留空。登录账号和手机号必须保持文本格式。",
                "本模板仅导入系统用户；教师和学生请分别使用对应业务页面的模板。",
                "新建账号默认使用系统初始密码，确认导入前可统一覆写；已有账号不会重置密码。",
                "更新时空单元格不覆盖原值；已有教师或学生账号不会被转换为系统用户。");
    }

    @Override
    protected List<String> exampleRow() {
        return List.of("adminuser", "教务示例", "CS", "admin@example.edu.cn",
                "13800138001", "女", "正常");
    }

}
