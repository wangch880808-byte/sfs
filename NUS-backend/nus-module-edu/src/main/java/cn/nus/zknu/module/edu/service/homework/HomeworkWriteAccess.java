package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import java.util.Objects;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 查看范围扩大后，批改入口与界面提示仍共用原来的布置者规则。 */
public final class HomeworkWriteAccess {
    private HomeworkWriteAccess() { }

    public static boolean isOwner(EduHomeworkDO homework) {
        return isOwner(homework, getLoginUserId());
    }

    public static boolean isOwner(EduHomeworkDO homework, Long userId) {
        return homework != null && userId != null && Objects.equals(homework.getTeacherId(), userId);
    }

    public static boolean canGrade(EduHomeworkDO homework) {
        return canGrade(homework, getLoginUserId());
    }

    public static boolean canGrade(EduHomeworkDO homework, Long userId) {
        return isOwner(homework, userId) && !Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED);
    }
}
