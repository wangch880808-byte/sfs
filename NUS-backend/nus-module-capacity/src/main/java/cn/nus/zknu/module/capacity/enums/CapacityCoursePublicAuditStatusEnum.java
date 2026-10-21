package cn.nus.zknu.module.capacity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公共课审批状态枚举。
 */
@Getter
@AllArgsConstructor
public enum CapacityCoursePublicAuditStatusEnum {

    NONE(0, "无需审批"),
    PENDING(1, "待审批"),
    APPROVED(2, "审批通过"),
    REJECTED(3, "审批驳回");

    private final Integer status;
    private final String description;

}
