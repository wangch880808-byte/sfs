package cn.nus.zknu.module.edu.controller.admin.certificate;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.infra.service.config.ConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 证书配置")
@RestController
@RequestMapping("/edu/certificate")
@Validated
public class EduCertificateController {

    private static final String CONFIG_KEY = "edu.certificate.threshold";
    private static final double DEFAULT_THRESHOLD = 80.0;

    @Resource
    private DataSource dataSource;
    @Resource
    private ConfigCacheService configCacheService;

    @GetMapping("/threshold")
    @Operation(summary = "获取证书达标率阈值")
    @PreAuthorize("@ss.hasPermission('edu:certificate:query')")
    public CommonResult<Map<String, Object>> getThreshold() {
        double threshold = DEFAULT_THRESHOLD;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT value FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1")) {
            ps.setString(1, CONFIG_KEY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                threshold = Double.parseDouble(rs.getString("value"));
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> result = new HashMap<>();
        result.put("threshold", threshold);
        return success(result);
    }

    @PutMapping("/threshold")
    @Operation(summary = "设置证书达标率阈值")
    @PreAuthorize("@ss.hasPermission('edu:certificate:manage')")
    public CommonResult<Boolean> setThreshold(@RequestParam("threshold") Double threshold) {
        if (threshold < 0 || threshold > 100) {
            return CommonResult.error(1_050_015_001, "阈值必须在 0-100 之间");
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            // 检查是否已存在
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1")) {
                ps.setString(1, CONFIG_KEY);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    // 更新
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE infra_config SET value = ?, update_time = NOW() WHERE config_key = ? AND deleted = 0")) {
                        up.setString(1, String.valueOf(threshold));
                        up.setString(2, CONFIG_KEY);
                        up.executeUpdate();
                    }
                } else {
                    // 插入
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO infra_config (category, name, config_key, value, type, visible, remark, creator, create_time, updater, update_time, deleted) " +
                                    "VALUES ('edu', '证书达标率阈值', ?, ?, 2, 1, '学生获得证书所需的能力达标率', 'admin', NOW(), 'admin', NOW(), 0)")) {
                        ins.setString(1, CONFIG_KEY);
                        ins.setString(2, String.valueOf(threshold));
                        ins.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            return CommonResult.error(500, "配置保存失败：" + e.getMessage());
        }
        // JDBC 已提交，负缓存与旧值缓存都需要立即失效。
        configCacheService.evict(CONFIG_KEY);
        return success(true);
    }

    private static final String PASS_SCORE_KEY = "edu.pass.score";
    private static final double DEFAULT_PASS_SCORE = 60.0;

    @GetMapping("/pass-score")
    @Operation(summary = "获取达标分数线")
    @PreAuthorize("@ss.hasPermission('edu:certificate:query')")
    public CommonResult<Map<String, Object>> getPassScore() {
        double passScore = DEFAULT_PASS_SCORE;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT value FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1")) {
            ps.setString(1, PASS_SCORE_KEY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                passScore = Double.parseDouble(rs.getString("value"));
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> result = new HashMap<>();
        result.put("passScore", passScore);
        return success(result);
    }

    @PutMapping("/pass-score")
    @Operation(summary = "设置达标分数线")
    @PreAuthorize("@ss.hasPermission('edu:certificate:manage')")
    public CommonResult<Boolean> setPassScore(@RequestParam("passScore") Double passScore) {
        if (passScore < 0 || passScore > 100) {
            return CommonResult.error(1_050_015_002, "分数线必须在 0-100 之间");
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1")) {
                ps.setString(1, PASS_SCORE_KEY);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE infra_config SET value = ?, update_time = NOW() WHERE config_key = ? AND deleted = 0")) {
                        up.setString(1, String.valueOf(passScore));
                        up.setString(2, PASS_SCORE_KEY);
                        up.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO infra_config (category, name, config_key, value, type, visible, remark, creator, create_time, updater, update_time, deleted) " +
                                    "VALUES ('edu', '达标分数线', ?, ?, 2, 1, '全局达标分数线配置', 'admin', NOW(), 'admin', NOW(), 0)")) {
                        ins.setString(1, PASS_SCORE_KEY);
                        ins.setString(2, String.valueOf(passScore));
                        ins.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            return CommonResult.error(500, "配置保存失败：" + e.getMessage());
        }
        configCacheService.evict(PASS_SCORE_KEY);
        return success(true);
    }

}
