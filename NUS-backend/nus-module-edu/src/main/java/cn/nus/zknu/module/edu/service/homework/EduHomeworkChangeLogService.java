package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.framework.common.util.monitor.TracerUtils;
import cn.nus.zknu.framework.common.util.servlet.ServletUtils;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.system.api.logger.OperateLogApi;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实训任务关键字段变更记录：写入系统操作日志（谁、什么时候、旧值→新值），与任务更新在同一事务内。
 */
@Service
public class EduHomeworkChangeLogService {

    public static final String TYPE = "实训任务";
    public static final String SUB_TYPE_PASS_SCORE = "修改达标分数线";

    @Resource
    private OperateLogApi operateLogApi;

    public void recordPassScoreChange(EduHomeworkDO homework, Integer oldPassScore, Integer newPassScore,
                                      long submittedCount) {
        OperateLogCreateReqDTO log = new OperateLogCreateReqDTO();
        log.setTraceId(TracerUtils.getTraceId());
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        log.setUserId(loginUser == null || loginUser.getId() == null ? 0L : loginUser.getId());
        log.setUserType(loginUser == null || loginUser.getUserType() == null
                ? UserTypeEnum.ADMIN.getValue() : loginUser.getUserType());
        log.setType(TYPE);
        log.setSubType(SUB_TYPE_PASS_SCORE);
        log.setBizId(homework.getId());
        log.setAction(StrUtil.format("将实训任务「{}」的达标分数线从 {} 分改为 {} 分（已有 {} 份提交）",
                StrUtil.blankToDefault(homework.getHomeworkName(), "未命名任务"),
                oldPassScore == null ? "默认" : oldPassScore, newPassScore, submittedCount));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("field", "passScore");
        extra.put("oldValue", oldPassScore);
        extra.put("newValue", newPassScore);
        extra.put("submittedCount", submittedCount);
        log.setExtra(JsonUtils.toJsonString(extra));
        HttpServletRequest request = ServletUtils.getRequest();
        log.setRequestMethod(request == null ? "PUT" : request.getMethod());
        log.setRequestUrl(request == null ? "/edu/homework/update" : request.getRequestURI());
        log.setUserIp(request == null ? "127.0.0.1"
                : StrUtil.blankToDefault(ServletUtils.getClientIP(request), "127.0.0.1"));
        log.setUserAgent(request == null ? "-" : StrUtil.blankToDefault(ServletUtils.getUserAgent(request), "-"));
        operateLogApi.createOperateLog(log);
    }
}
