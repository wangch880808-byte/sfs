package cn.nus.zknu.framework.common.exception.enums;

/**
 * 业务异常错误码区段，仅供各模块核对，实际错误码在各模块 ErrorCodeConstants 中定义。
 * <p>
 * 十位错误码格式：1（业务异常）+ 三位系统编号 + 三位模块编号 + 三位错误编号。
 */
public class ServiceErrorCodeRange {

    // infra:    [1-001-000-000, 1-002-000-000)
    // system:   [1-002-000-000, 1-003-000-000)
    // ai:       [1-040-000-000, 1-041-000-000)
    // edu:      [1-050-000-000, 1-051-000-000)
    // capacity: [1-060-000-000, 1-061-000-000)

}
