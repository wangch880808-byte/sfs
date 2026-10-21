package cn.nus.zknu.module.edu.dbit.support;

import cn.nus.zknu.framework.common.exception.ErrorCode;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.edu.service.aireview.AiReviewCalibrationService;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.aireview.HomeworkAiReviewFailureNotifier;
import cn.nus.zknu.module.edu.service.aireview.HomeworkReviewEvidenceService;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.aireview.support.StudentVisibleCommentFormatter;
import cn.nus.zknu.module.edu.service.homework.HomeworkAttachmentService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.api.permission.PermissionApi;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 连接真实 MySQL 的集成测试基类（标签 {@code db}，默认构建不运行）。
 * <p>
 * 运行方式（需要本机 Docker 里已有 mysql:8.4.6 镜像，不会拉取）：
 * <pre>
 * git -C NUS-doc show release/V1.0.1.6:'3-数据库文件/ry261-test.sql' &gt; /tmp/baseline17.sql
 * TZ=Asia/Shanghai mvnd -o -pl nus-module-edu -am test -Pdb-it \
 *     -Dnus.it.baseline-sql=/tmp/baseline17.sql \
 *     -Dnus.it.migration-sql=deploy/database/migrations/V18__v1_0_1_7_reliability_flow.sql \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dtest='*DbTest'
 * </pre>
 * 每个 JVM 只启动一个容器、一个 Spring 上下文；各测试自己造班级、课程、任务和学生，互不依赖，不做清理。
 */
@Tag("db")
@SpringBootTest(classes = EduDbItConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "nus.info.base-package=cn.nus.zknu.module",
                "mybatis.lazy-initialization=true",
                "mybatis-plus.configuration.map-underscore-to-camel-case=true",
                "mybatis-plus.global-config.banner=false",
                "mybatis-plus.global-config.db-config.id-type=AUTO",
                "mybatis-plus.global-config.db-config.logic-delete-value=1",
                "mybatis-plus.global-config.db-config.logic-not-delete-value=0",
                "mybatis-plus.type-aliases-package=cn.nus.zknu.module.*.dal.dataobject",
                "mybatis-plus.encryptor.password=db-it-only-not-a-secret",
                "mybatis-plus-join.banner=false",
                "mybatis-plus-join.sub-table-logic=true",
                "mybatis-plus-join.ms-cache=true",
                "mybatis-plus-join.table-alias=t",
                "mybatis-plus-join.logic-del-type=on",
                "spring.datasource.hikari.maximum-pool-size=12",
        })
public abstract class EduDbIntegrationTest {

    static {
        // 与线上一致；Maven 的 db-it profile 也会设置
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    private static final AtomicLong USER_IDS = new AtomicLong(910_000);

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabase database = MySqlTestDatabase.get();
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    // ========== 外部依赖：AI 模型、通知发送、文件存储、用户与权限 ==========
    @MockitoBean
    protected AiEduReviewApi aiEduReviewApi;
    @MockitoBean
    protected HomeworkReviewEvidenceService reviewEvidenceService;
    @MockitoBean
    protected StudentVisibleCommentFormatter studentVisibleCommentFormatter;
    @MockitoBean
    protected EduAiBusinessAccessService aiBusinessAccessService;
    @MockitoBean
    protected AiReviewCalibrationService aiReviewCalibrationService;
    @MockitoBean
    protected HomeworkAiReviewFailureNotifier aiReviewFailureNotifier;
    @MockitoBean
    protected EduAiReviewPolicy aiReviewPolicy;
    @MockitoBean
    protected EduBusinessNotifyService businessNotifyService;
    @MockitoBean
    protected HomeworkAttachmentService homeworkAttachmentService;
    @MockitoBean
    protected SecurityFrameworkService securityFrameworkService;
    @MockitoBean
    protected EduRoleApi eduRoleApi;
    @MockitoBean
    protected PermissionApi permissionApi;
    @MockitoBean
    protected AdminUserService adminUserService;
    @MockitoBean
    protected AdminUserApi adminUserApi;
    /** 真实实现外包一层 spy：并发测试在“读完记录、写入之前”的调用点上卡住两个请求。 */
    @MockitoSpyBean
    protected EduHomeworkAiReviewService aiReviewService;

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @Autowired
    protected RecordingExecutor aiReviewExecutor;

    protected TransactionTemplate tx;

    @BeforeEach
    void setUpDbTest() {
        tx = new TransactionTemplate(transactionManager);
        aiReviewExecutor.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDownDbTest() {
        SecurityContextHolder.clearContext();
    }

    // ========== 登录用户 ==========

    protected static long newUserId() {
        return USER_IDS.incrementAndGet();
    }

    /** 把当前线程的登录用户设为 userId（教师操作、DB 自动填充 creator/updater 用）。 */
    protected static void loginAs(Long userId) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setUserType(2);
        user.setInfo(Map.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
    }

    // ========== 造数据（直接写库，列名以基线 + V18 为准） ==========

    /** 插入一行并返回自增主键；值为 null 的列不写，交给表的默认值。 */
    protected long insert(String table, Map<String, Object> row) {
        Map<String, Object> columns = new LinkedHashMap<>();
        row.forEach((name, value) -> {
            if (value != null) {
                columns.put(name, value);
            }
        });
        String names = String.join(", ", columns.keySet());
        String values = columns.keySet().stream().map(name -> ":" + name).collect(Collectors.joining(", "));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update("INSERT INTO " + table + " (" + names + ") VALUES (" + values + ")",
                new MapSqlParameterSource(columns), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        assertNotNull(key, "插入 " + table + " 没有返回主键");
        return key.longValue();
    }

    /** 新建一个在用的班级 + 课程 + 班课绑定。 */
    protected Scope newScope(long teacherId) {
        return newScope(teacherId, 0, 0);
    }

    protected Scope newScope(long teacherId, int classStatus, int courseStatus) {
        Map<String, Object> clazz = new LinkedHashMap<>();
        clazz.put("class_name", "it-class-" + newUserId());
        clazz.put("grade_year", 2026);
        clazz.put("dept_id", 0L);
        clazz.put("education_level", 1L);
        clazz.put("schooling_year", 4L);
        clazz.put("status", classStatus);
        long classId = insert("edu_class", clazz);
        Map<String, Object> course = new LinkedHashMap<>();
        course.put("indicator_id", 0L);
        course.put("course_name", "it-course-" + classId);
        course.put("teacher_Id", teacherId);
        course.put("status", courseStatus);
        long courseId = insert("capacity_course", course);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("class_id", classId);
        binding.put("course_id", courseId);
        binding.put("status", 0);
        insert("edu_class_course", binding);
        return new Scope(classId, courseId, teacherId);
    }

    protected HomeworkBuilder homework(Scope scope) {
        return new HomeworkBuilder(scope);
    }

    /** 学生加入班级。 */
    protected void joinClass(Scope scope, long studentId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("class_id", scope.classId());
        row.put("student_id", studentId);
        insert("edu_class_student", row);
    }

    /** 把任务分配给学生。 */
    protected void assign(long homeworkId, Scope scope, long studentId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("homework_id", homeworkId);
        row.put("student_id", studentId);
        row.put("class_id", scope.classId());
        row.put("assigned_time", LocalDateTime.now().minusDays(7));
        insert("edu_homework_assignee", row);
    }

    /** 直接写一条提交记录（不经过 Service），用于查询类测试。 */
    protected long insertRecord(long homeworkId, long studentId, int status, Integer score, LocalDateTime submitTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("homework_id", homeworkId);
        row.put("student_id", studentId);
        row.put("content", "it");
        row.put("submit_time", submitTime);
        row.put("score", score);
        row.put("status", status);
        row.put("redo_count", 0);
        row.put("create_time", submitTime);
        row.put("update_time", submitTime);
        return insert("edu_homework_record", row);
    }

    /** 把提交记录的更新时间改到过去（模拟“学生早些时候提交，教师现在批改”，避免与本次操作落在同一秒）。 */
    protected LocalDateTime ageRecord(long recordId, int minutes) {
        LocalDateTime past = LocalDateTime.now().withNano(0).minusMinutes(minutes);
        jdbc.update("UPDATE edu_homework_record SET update_time = ? WHERE id = ?", past, recordId);
        return past;
    }

    // ========== 断言与并发 ==========

    protected static void assertServiceError(ErrorCode expected, Throwable actual) {
        assertNotNull(actual, "应抛出业务异常 " + expected.getMsg());
        ServiceException ex = assertInstanceOf(ServiceException.class, actual,
                () -> "应为业务异常，实际：" + actual);
        assertEquals(expected.getCode(), ex.getCode(), () -> "错误码不符，实际提示：" + ex.getMessage());
    }

    /**
     * 每个任务在独立线程里执行（各自的登录用户与事务），全部结束后按提交顺序返回结果或异常。
     */
    protected static <T> List<Outcome<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException ex) {
                    outcomes.add(new Outcome<>(null, ex.getCause()));
                } catch (java.util.concurrent.TimeoutException ex) {
                    throw new AssertionError("并发任务 60 秒未结束，可能死锁", ex);
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 在另一个线程里执行（不会复用当前线程绑定的事务连接，只能看到已提交的数据）。 */
    protected static <T> T inAnotherThread(Callable<T> task) {
        try {
            Outcome<T> outcome = runConcurrently(List.of(task)).get(0);
            if (!outcome.ok()) {
                throw new AssertionError("另一线程执行失败", outcome.error());
            }
            return outcome.value();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    public record Outcome<T>(T value, Throwable error) {
        public boolean ok() {
            return error == null;
        }
    }

    /**
     * 让前 parties 次调用都到达后再一起放行，之后的调用直接通过；用于把并发请求卡在“读完、未写”的位置。
     */
    public static final class Rendezvous {
        private final CountDownLatch arrived;
        private final AtomicLong remaining;

        public Rendezvous(int parties) {
            this.arrived = new CountDownLatch(parties);
            this.remaining = new AtomicLong(parties);
        }

        public void arrive() throws InterruptedException {
            if (remaining.getAndDecrement() <= 0) {
                return;
            }
            arrived.countDown();
            if (!arrived.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发请求没有在 20 秒内同时到达");
            }
        }
    }

    // ========== 数据结构 ==========

    /** 一组在用的班级、课程（含班课绑定）与任课教师。 */
    public record Scope(long classId, long courseId, long teacherId) {
    }

    public final class HomeworkBuilder {
        private final Scope scope;
        private String name = "it-homework";
        private int level = 1;
        private int status = 1;
        private int fullScore = 100;
        private Integer passScore = 60;
        private int autoGrade = 0;
        private LocalDateTime deadline = LocalDateTime.now().plusDays(3).withNano(0);
        private String semesterName;

        private HomeworkBuilder(Scope scope) {
            this.scope = scope;
        }

        public HomeworkBuilder name(String value) {
            this.name = value;
            return this;
        }

        public HomeworkBuilder level(int value) {
            this.level = value;
            return this;
        }

        public HomeworkBuilder status(int value) {
            this.status = value;
            return this;
        }

        public HomeworkBuilder passScore(Integer value) {
            this.passScore = value;
            return this;
        }

        public HomeworkBuilder autoGrade(boolean value) {
            this.autoGrade = value ? 1 : 0;
            return this;
        }

        public HomeworkBuilder deadline(LocalDateTime value) {
            this.deadline = value.withNano(0);
            return this;
        }

        public HomeworkBuilder semester(String value) {
            this.semesterName = value;
            return this;
        }

        public long create() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("homework_name", name);
            row.put("homework_type", 1);
            row.put("homework_level", level);
            row.put("course_id", scope.courseId());
            row.put("class_id", scope.classId());
            row.put("semester_name", semesterName);
            row.put("requirement", "it");
            row.put("deadline", deadline);
            row.put("auto_grade", autoGrade);
            row.put("submission_mode", "text");
            row.put("pass_score", passScore);
            row.put("score", fullScore);
            row.put("teacher_id", scope.teacherId());
            row.put("status", status);
            row.put("creator", String.valueOf(scope.teacherId()));
            return insert("edu_homework", row);
        }
    }
}
