package cn.nus.zknu.module.infra.enums.monitor;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MonitorTargetEnum {

    DRUID("nus_monitor_druid", "/druid", "/druid/index.html"),
    JAVA("nus_monitor_java", "/admin", "/admin/applications"),
    SKYWALKING("nus_monitor_skywalking", "/", null);

    private final String cookieName;
    private final String cookiePath;
    private final String defaultRedirectUrl;

}
