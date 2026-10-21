package cn.nus.zknu.server.security;

import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 运行：TZ=Asia/Shanghai mvnd -o -q -pl nus-server -am test -Dtest=EndpointPermissionsSnapshotTest
 * -Dsurefire.failIfNoSpecifiedTests=false。仅在审查接口变更时附加
 * -Dnus.permission.snapshot.update=true 重写 src/test/resources/security/endpoint-permissions.tsv。
 */
class EndpointPermissionsSnapshotTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/security/endpoint-permissions.tsv");

    // key 是「HTTP 方法 空格 路径」；每项的值必须说明为什么可以匿名访问。
    private static final Map<String, String> PERMIT_ALL = Map.ofEntries(
            Map.entry("GET /infra/file/{configId}/get/**", "现有文件接口由业务层按配置或用户上下文处理"),
            Map.entry("GET /infra/monitor-access/consume", "监控访问采用独立票据校验或撤销流程"),
            Map.entry("GET /infra/monitor-access/verify", "监控访问采用独立票据校验或撤销流程"),
            Map.entry("GET /system/auth/social-auth-redirect", "社交登录回调入口"),
            Map.entry("GET /system/auth/social-login-config", "登录页读取社交登录公开配置"),
            Map.entry("GET /system/wechat-mini-program/config", "小程序登录页读取公开配置"),
            Map.entry("GET /system/wechat-mini-program/qr/status", "登录页按票据轮询扫码状态"),
            Map.entry("POST /infra/monitor-access/revoke", "监控访问采用独立票据校验或撤销流程"),
            Map.entry("POST /system/auth/login", "账号密码登录入口"),
            Map.entry("POST /system/auth/logout", "退出登录入口，容许令牌失效后调用"),
            Map.entry("POST /system/auth/refresh-token", "刷新令牌入口"),
            Map.entry("POST /system/auth/social-login", "社交登录入口"),
            Map.entry("POST /system/captcha/check", "登录前获取或校验验证码"),
            Map.entry("POST /system/captcha/get", "登录前获取或校验验证码"),
            Map.entry("POST /system/wechat-mini-program/qr/confirm", "小程序凭会话令牌确认扫码"),
            Map.entry("POST /system/wechat-mini-program/qr/exchange", "已确认票据换取平台令牌"),
            Map.entry("POST /system/wechat-mini-program/qr/login", "网页登录创建小程序扫码票据"),
            Map.entry("POST /system/wechat-mini-program/qr/scan", "小程序凭会话令牌登记扫码"),
            Map.entry("POST /system/wechat-mini-program/session", "小程序 wx.login code 换取会话")
    );

    // key 是「HTTP 方法 空格 路径」；每项的值必须说明为什么登录即可访问。
    private static final Map<String, String> AUTHENTICATED = Map.ofEntries(
            Map.entry("DELETE /system/social-user/unbind", "登录用户查询或操作本人社交账号绑定"),
            Map.entry("DELETE /system/wechat-mini-program/binding", "现有微信绑定接口以当前登录账号为对象"),
            Map.entry("GET /edu/semester/current", "登录用户读取当前学期信息"),
            Map.entry("GET /edu/student/capacity/analysis", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/detail", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/basic", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/export-pdf", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/skills", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/trend", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/course/get", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/list", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/resource/download", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/unlock-status", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/home/overview", "现有学生首页按登录身份读取本人概览"),
            Map.entry("GET /edu/student/news/get", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/news/page", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/news/sources", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/profile/get", "现有学生资料接口按登录身份操作本人资料"),
            Map.entry("GET /infra/config/get-value-by-key", "登录用户读取业务配置值（沿用现有接口）"),
            Map.entry("GET /infra/file/presigned-url", "现有文件接口由业务层按配置或用户上下文处理"),
            Map.entry("GET /system/auth/get-permission-info", "登录用户读取本人权限信息"),
            Map.entry("GET /system/dept/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/dept/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/dict-data/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/dict-data/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/dict-type/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/dict-type/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/menu/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/menu/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/notify-message/get-unread-count", "登录用户查询或标记本人通知"),
            Map.entry("GET /system/notify-message/get-unread-list", "登录用户查询或标记本人通知"),
            Map.entry("GET /system/notify-message/my-page", "登录用户查询或标记本人通知"),
            Map.entry("GET /system/post/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/post/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/role/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/role/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/social-user/get-bind-list", "登录用户查询或操作本人社交账号绑定"),
            Map.entry("GET /system/user/list-all-simple", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/user/profile/get", "登录用户查询或更新本人资料"),
            Map.entry("GET /system/user/simple-list", "登录用户在表单中读取基础选项列表"),
            Map.entry("GET /system/wechat-mini-program/binding", "现有微信绑定接口以当前登录账号为对象"),
            Map.entry("POST /infra/file/create", "现有文件接口由业务层按配置或用户上下文处理"),
            Map.entry("POST /infra/file/upload", "现有文件接口由业务层按配置或用户上下文处理"),
            Map.entry("POST /system/social-user/bind", "登录用户查询或操作本人社交账号绑定"),
            Map.entry("POST /system/wechat-mini-program/qr/bind", "现有微信绑定接口以当前登录账号为对象"),
            Map.entry("PUT /edu/student/profile/password", "现有学生资料接口按登录身份操作本人资料"),
            Map.entry("PUT /edu/student/profile/update", "现有学生资料接口按登录身份操作本人资料"),
            Map.entry("PUT /system/notify-message/update-all-read", "登录用户查询或标记本人通知"),
            Map.entry("PUT /system/notify-message/update-read", "登录用户查询或标记本人通知"),
            Map.entry("PUT /system/user/profile/update", "登录用户查询或更新本人资料"),
            Map.entry("PUT /system/user/profile/update-password", "登录用户查询或更新本人资料")
    );

    // 学生端特殊接口须逐项说明原因；普通接口必须显式包含 student 角色。
    private static final Map<String, String> STUDENT_EXCEPTIONS = Map.ofEntries(
            Map.entry("GET /edu/student/capacity/analysis", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/detail", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/basic", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/export-pdf", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/skills", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/capacity/report/trend", "现有能力画像接口按登录身份读取本人报告"),
            Map.entry("GET /edu/student/course/get", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/list", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/resource/download", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/course/unlock-status", "现有课程接口按登录身份读取本人课程或资源"),
            Map.entry("GET /edu/student/home/overview", "现有学生首页按登录身份读取本人概览"),
            Map.entry("GET /edu/student/news/get", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/news/page", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/news/sources", "现有学生资讯向登录用户提供阅读内容"),
            Map.entry("GET /edu/student/profile/get", "现有学生资料接口按登录身份操作本人资料"),
            Map.entry("PUT /edu/student/profile/password", "现有学生资料接口按登录身份操作本人资料"),
            Map.entry("PUT /edu/student/profile/update", "现有学生资料接口按登录身份操作本人资料")
    );

    private static final Set<String> STUDENT_HOMEWORK_PATHS = Set.of(
            "GET /edu/student/homework/list", "GET /edu/student/homework/get",
            "GET /edu/student/homework/history", "POST /edu/student/homework/submit",
            "PUT /edu/student/homework/update", "POST /edu/student/homework/attachment/upload",
            "GET /edu/student/homework/attachment/download");
    private static final Set<String> WECHAT_PATHS = Set.of(
            "GET /system/wechat-mini-program/config", "POST /system/wechat-mini-program/qr/login",
            "POST /system/wechat-mini-program/qr/bind", "GET /system/wechat-mini-program/qr/status",
            "POST /system/wechat-mini-program/qr/exchange", "POST /system/wechat-mini-program/session",
            "POST /system/wechat-mini-program/qr/scan", "POST /system/wechat-mini-program/qr/confirm",
            "GET /system/wechat-mini-program/binding", "DELETE /system/wechat-mini-program/binding");

    @Test
    void endpointPermissionsMatchReviewedSnapshot() throws Exception {
        List<String> actual = scan();
        String rendered = String.join("\n", actual) + "\n";
        boolean update = Boolean.getBoolean("nus.permission.snapshot.update");
        if (!update) {
            assertTrue(Files.exists(SNAPSHOT), "缺少接口权限快照；审查后使用 -Dnus.permission.snapshot.update=true 生成");
            List<String> expected = Files.readAllLines(SNAPSHOT, StandardCharsets.UTF_8);
            if (!expected.equals(actual)) {
                Set<String> removed = new TreeSet<>(expected);
                removed.removeAll(actual);
                Set<String> added = new TreeSet<>(actual);
                added.removeAll(expected);
                fail("接口权限快照有变化（- 为旧、+ 为新）：\n- " + String.join("\n- ", removed)
                        + "\n+ " + String.join("\n+ ", added));
            }
        }
        assertEquals(actual.size(), new TreeSet<>(actual).size(), "重复的 HTTP 方法和路径；请检查控制器映射");
        checkAllowList(actual, "PERMIT_ALL", PERMIT_ALL);
        checkAllowList(actual, "AUTHENTICATED", AUTHENTICATED);
        Set<String> studentExceptionsFound = new TreeSet<>();
        for (String row : actual) {
            String[] fields = row.split("\t", -1);
            String key = fields[0] + " " + fields[1];
            if (fields[1].startsWith("/edu/student/")) {
                if (STUDENT_EXCEPTIONS.containsKey(key)) {
                    assertEquals("AUTHENTICATED", fields[2], key + " 的学生端例外安全要求已改变");
                    studentExceptionsFound.add(key);
                } else {
                    assertTrue(fields[2].contains("hasRole('student')"), key + " 缺少 student 角色校验");
                }
            }
        }
        assertEquals(STUDENT_EXCEPTIONS.keySet(), studentExceptionsFound, "学生端例外清单与现有接口不一致");
        STUDENT_EXCEPTIONS.forEach((key, reason) -> assertFalse(reason.isBlank(), key + " 缺少例外理由"));
        checkContract(actual, "/edu/student/homework/", STUDENT_HOMEWORK_PATHS);
        checkContract(actual, "/system/wechat-mini-program/", WECHAT_PATHS);
        if (update) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, rendered, StandardCharsets.UTF_8);
        }
    }

    private static void checkAllowList(List<String> rows, String security, Map<String, String> allowed) {
        Set<String> actual = new TreeSet<>();
        for (String row : rows) {
            String[] fields = row.split("\t", -1);
            if (security.equals(fields[2])) {
                actual.add(fields[0] + " " + fields[1]);
            }
        }
        assertEquals(allowed.keySet(), actual, security + " 接口清单改变；请逐项审查并说明用途");
        allowed.forEach((key, reason) -> assertFalse(reason.isBlank(), key + " 缺少允许理由"));
    }

    private static void checkContract(List<String> rows, String prefix, Set<String> expected) {
        Set<String> actual = new TreeSet<>();
        for (String row : rows) {
            String[] fields = row.split("\t", -1);
            if (fields[1].startsWith(prefix)) {
                actual.add(fields[0] + " " + fields[1]);
            }
        }
        assertEquals(expected, actual, prefix + " 小程序接口路径集合改变");
    }

    private static List<String> scan() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> rows = new ArrayList<>();
        for (var bean : scanner.findCandidateComponents("cn.nus.zknu")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            RequestMapping root = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                PreAuthorize authorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (authorize == null) {
                    authorize = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
                }
                boolean permitAll = AnnotatedElementUtils.hasAnnotation(method, PermitAll.class)
                        || AnnotatedElementUtils.hasAnnotation(type, PermitAll.class);
                assertFalse(authorize != null && permitAll, type.getName() + "#" + method.getName() + " 同时声明两种安全要求");
                String security = authorize != null ? authorize.value().trim().replaceAll("\\s+", " ")
                        : permitAll ? "PERMIT_ALL" : "AUTHENTICATED";
                String[] roots = paths(root);
                String[] leaves = paths(mapping);
                RequestMethod[] methods = mapping.method().length > 0 ? mapping.method()
                        : root != null && root.method().length > 0 ? root.method() : new RequestMethod[0];
                List<String> verbs = methods.length == 0 ? List.of("ANY")
                        : Arrays.stream(methods).map(Enum::name).sorted().toList();
                for (String verb : verbs) {
                    for (String base : roots) {
                        for (String leaf : leaves) {
                            rows.add(verb + "\t" + path(base, leaf) + "\t" + security);
                        }
                    }
                }
            }
        }
        rows.sort(String::compareTo);
        assertFalse(rows.isEmpty(), "未扫描到任何 RestController；请检查测试类路径");
        return rows;
    }

    private static String[] paths(RequestMapping mapping) {
        if (mapping == null) {
            return new String[]{""};
        }
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length > 0 ? paths : new String[]{""};
    }

    private static String path(String base, String leaf) {
        String joined = "/" + base + "/" + leaf;
        joined = joined.replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }
}
