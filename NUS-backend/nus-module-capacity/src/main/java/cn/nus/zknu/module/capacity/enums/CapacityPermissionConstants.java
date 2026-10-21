package cn.nus.zknu.module.capacity.enums;

/**
 * capacity 模块在服务层用到的权限标识（与菜单管理中按钮的“权限标识”一致）。
 */
public interface CapacityPermissionConstants {

    /**
     * 公共课审批：审批教师的公共课申请；持有者也视为课程管理员，可查看、管理全部课程，并接收“公共课待审批”通知。
     */
    String COURSE_AUDIT = "capacity:course:audit";

}
