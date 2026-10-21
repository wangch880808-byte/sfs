package cn.nus.zknu.module.system.api.permission;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@link EduRoleApi} 实现：读参数配置，带默认值和格式校验。
 * <p>
 * 角色判断在每次请求的数据范围校验里都会用到，配置读一次缓存 30 秒；改配置后最多 30 秒生效。
 */
@Service
@Slf4j
public class EduRoleApiImpl implements EduRoleApi {

    /** 与角色管理中“角色标识”一致的写法：字母、数字、下划线、短横线、点、冒号 */
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,100}$");
    /** 逗号（含中文逗号）、分号或空白都可作分隔 */
    private static final Pattern SEPARATOR = Pattern.compile("[,，;；\\s]+");
    private static final long CACHE_SECONDS = 30;

    @Resource
    private ConfigApi configApi;
    @Resource
    private PermissionService permissionService;
    /** {@code @ss} 依赖 PermissionApi，PermissionApi 又依赖本类；延迟注入避免循环创建 */
    @Resource
    @Lazy
    private SecurityFrameworkService securityFrameworkService;

    private final Supplier<RoleCodes> cache = Suppliers.memoizeWithExpiration(
            this::load, CACHE_SECONDS, TimeUnit.SECONDS);

    @Override
    public Set<String> getAdminRoleCodes() {
        return cache.get().adminCodes();
    }

    @Override
    public Set<String> getSuperAdminRoleCodes() {
        return cache.get().superAdminCodes();
    }

    @Override
    public String getTeacherRoleCode() {
        return cache.get().teacherCode();
    }

    @Override
    public String getStudentRoleCode() {
        return cache.get().studentCode();
    }

    @Override
    public boolean isCurrentUserAdmin() {
        return securityFrameworkService.hasAnyRoles(toArray(getAdminRoleCodes()));
    }

    @Override
    public boolean isCurrentUserSuperAdmin() {
        return securityFrameworkService.hasAnyRoles(toArray(getSuperAdminRoleCodes()));
    }

    @Override
    public boolean isCurrentUserTeacher() {
        return securityFrameworkService.hasAnyRoles(getTeacherRoleCode());
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        return userId != null && permissionService.hasAnyRoles(userId, toArray(getSuperAdminRoleCodes()));
    }

    @Override
    public boolean isTeacher(Long userId) {
        return userId != null && permissionService.hasAnyRoles(userId, getTeacherRoleCode());
    }

    @Override
    public boolean isStudent(Long userId) {
        return userId != null && permissionService.hasAnyRoles(userId, getStudentRoleCode());
    }

    @VisibleForTesting
    RoleCodes load() {
        Set<String> superAdminCodes = readCodes(SUPER_ADMIN_CODES_KEY, DEFAULT_SUPER_ADMIN_CODES);
        Set<String> adminCodes = new LinkedHashSet<>(readCodes(ADMIN_CODES_KEY, DEFAULT_ADMIN_CODES));
        // 超级管理员始终算管理员，避免改配置时漏写而把超级管理员的数据范围收窄
        adminCodes.addAll(superAdminCodes);
        String teacherCode = readCode(TEACHER_CODE_KEY, DEFAULT_TEACHER_CODE, adminCodes);
        String studentCode = readCode(STUDENT_CODE_KEY, DEFAULT_STUDENT_CODE, adminCodes);
        if (teacherCode.equals(studentCode)) {
            log.warn("[load][教师与学生角色编码配置相同（{}），回退为默认值 {}/{}]",
                    teacherCode, DEFAULT_TEACHER_CODE, DEFAULT_STUDENT_CODE);
            teacherCode = DEFAULT_TEACHER_CODE;
            studentCode = DEFAULT_STUDENT_CODE;
        }
        return new RoleCodes(Collections.unmodifiableSet(adminCodes),
                Collections.unmodifiableSet(superAdminCodes), teacherCode, studentCode);
    }

    private Set<String> readCodes(String key, String fallback) {
        String raw = readRaw(key);
        if (StrUtil.isBlank(raw)) {
            return parse(fallback);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String token : SEPARATOR.split(raw.trim())) {
            if (token.isEmpty()) {
                continue;
            }
            if (ROLE_CODE_PATTERN.matcher(token).matches()) {
                codes.add(token);
            } else {
                log.warn("[readCodes][参数 {} 含无效角色编码，已忽略：{}]", key, token);
            }
        }
        if (codes.isEmpty()) {
            log.warn("[readCodes][参数 {} 没有有效角色编码，回退为默认值 {}]", key, fallback);
            return parse(fallback);
        }
        return codes;
    }

    /** 身份角色只取一个编码；格式不对、或与管理员角色重合时回退为默认值 */
    private String readCode(String key, String fallback, Set<String> adminCodes) {
        String raw = StrUtil.trim(readRaw(key));
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        if (!ROLE_CODE_PATTERN.matcher(raw).matches() || adminCodes.contains(raw)) {
            log.warn("[readCode][参数 {} 的值无效或与管理员角色重合，回退为默认值 {}：{}]", key, fallback, raw);
            return fallback;
        }
        return raw;
    }

    private String readRaw(String key) {
        try {
            return configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("[readRaw][读取参数 {} 失败，使用默认值]", key, ex);
            return null;
        }
    }

    private static Set<String> parse(String codes) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : SEPARATOR.split(codes)) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private static String[] toArray(Set<String> codes) {
        return codes.toArray(String[]::new);
    }

    @VisibleForTesting
    record RoleCodes(Set<String> adminCodes, Set<String> superAdminCodes, String teacherCode, String studentCode) {
    }

}
