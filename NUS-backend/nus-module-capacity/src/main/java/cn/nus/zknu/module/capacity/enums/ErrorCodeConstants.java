package cn.nus.zknu.module.capacity.enums;

import cn.nus.zknu.framework.common.exception.ErrorCode;

/**
 * capacity 错误码枚举类
 * <p>
 * capacity 系统，使用 1-060-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 培养指标模块 1-060-000-000 ==========
    ErrorCode INDICATOR_NOT_EXISTS = new ErrorCode(1_060_000_000, "指标不存在");
    ErrorCode INDICATOR_LEVEL_INVALID = new ErrorCode(1_060_000_001, "指标级别只能为一级或二级");
    ErrorCode INDICATOR_PARENT_REQUIRED = new ErrorCode(1_060_000_002, "二级指标必须选择一级指标");
    ErrorCode INDICATOR_PARENT_NOT_EXISTS = new ErrorCode(1_060_000_003, "父指标不存在");
    ErrorCode INDICATOR_PARENT_NOT_FIRST_LEVEL = new ErrorCode(1_060_000_004, "二级指标只能挂在一级指标下");
    ErrorCode INDICATOR_PARENT_ERROR = new ErrorCode(1_060_000_005, "父指标设置错误");
    ErrorCode INDICATOR_FIRST_LEVEL_WEIGHT_EXCEED = new ErrorCode(1_060_000_006, "所有一级指标总权重不能超过 100%");
    ErrorCode INDICATOR_SECOND_LEVEL_WEIGHT_EXCEED = new ErrorCode(1_060_000_007, "当前一级指标下的二级指标总权重不能超过 100%");
    ErrorCode INDICATOR_EXISTS_CHILDREN = new ErrorCode(1_060_000_008, "当前指标下存在子指标，无法删除");
    ErrorCode INDICATOR_LEVEL_CAN_NOT_CHANGE = new ErrorCode(1_060_000_009, "指标级别不允许修改");
    ErrorCode INDICATOR_PARENT_CAN_NOT_CHANGE = new ErrorCode(1_060_000_010, "父指标不允许修改");
    ErrorCode INDICATOR_WEIGHT_RESET_SCOPE_ERROR = new ErrorCode(1_060_000_011, "当前重设权重范围与指标数据不匹配");

    // ========== 实训课程模块 1-060-001-000 ==========
    ErrorCode COURSE_NOT_EXISTS = new ErrorCode(1_060_001_000, "实训课程不存在");
    ErrorCode COURSE_PUBLIC_AUDIT_PENDING = new ErrorCode(1_060_001_001, "当前课程不处于待审批状态");
    ErrorCode COURSE_PUBLIC_AUDIT_FORBIDDEN = new ErrorCode(1_060_001_002, "无法审批公共课：你没有「公共课审批」权限，请联系管理员在角色中授予");
    ErrorCode COURSE_IMAGE_URL_INVALID = new ErrorCode(1_060_001_003, "课程封面地址不合法，请重新上传封面");

}
