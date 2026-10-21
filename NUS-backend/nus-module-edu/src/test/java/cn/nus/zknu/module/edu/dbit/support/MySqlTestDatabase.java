package cn.nus.zknu.module.edu.dbit.support;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 集成测试用的一次性 MySQL：每个测试 JVM 启动一个容器，导入基线后依次执行迁移脚本。
 * <ul>
 *     <li>只用本地已有的镜像（默认 {@code mysql:8.4.6}，与部署一致），不拉取；</li>
 *     <li>数据目录放在 tmpfs，容器随 JVM 结束删除，不碰共享库；</li>
 *     <li>SQL 用容器里的 mysql 客户端执行，与运维导入方式一致（支持条件注释、PREPARE 等）。</li>
 * </ul>
 * 系统属性：
 * <ul>
 *     <li>{@code nus.it.baseline-sql}（必填）：基线库 SQL 文件路径，如 NUS-doc 的 ry261-test.sql（基线 17）；</li>
 *     <li>{@code nus.it.migration-sql}（可选）：逗号分隔的迁移脚本路径，按顺序执行，如 V18；</li>
 *     <li>{@code nus.it.mysql-image}（可选）：镜像名，默认 mysql:8.4.6。</li>
 * </ul>
 */
public final class MySqlTestDatabase {

    public static final String BASELINE_PROPERTY = "nus.it.baseline-sql";
    public static final String MIGRATIONS_PROPERTY = "nus.it.migration-sql";
    public static final String IMAGE_PROPERTY = "nus.it.mysql-image";
    private static final String DEFAULT_IMAGE = "mysql:8.4.6";
    private static final String DATABASE = "nus";
    private static final int PORT = 3306;
    /** mysql 报错只保留“ERROR 码 at line 行号”，不回显语句内容，避免把种子数据打进日志。 */
    private static final Pattern MYSQL_ERROR = Pattern.compile("ERROR \\d+ \\(\\w+\\)( at line \\d+)?");

    private static volatile MySqlTestDatabase instance;

    private final GenericContainer<?> container;
    private final String password;
    private final List<String> appliedScripts = new ArrayList<>();

    private MySqlTestDatabase(GenericContainer<?> container, String password) {
        this.container = container;
        this.password = password;
    }

    /** 启动（每个 JVM 一次）并返回已导入基线和迁移的库。 */
    public static MySqlTestDatabase get() {
        MySqlTestDatabase current = instance;
        if (current != null) {
            return current;
        }
        synchronized (MySqlTestDatabase.class) {
            if (instance == null) {
                instance = start();
            }
            return instance;
        }
    }

    private static MySqlTestDatabase start() {
        Path baseline = requireFile(System.getProperty(BASELINE_PROPERTY), BASELINE_PROPERTY);
        List<Path> migrations = new ArrayList<>();
        String migrationList = System.getProperty(MIGRATIONS_PROPERTY, "");
        for (String item : migrationList.split(",")) {
            if (!item.isBlank()) {
                migrations.add(requireFile(item.trim(), MIGRATIONS_PROPERTY));
            }
        }
        String image = System.getProperty(IMAGE_PROPERTY, DEFAULT_IMAGE);
        // 测试专用的一次性口令：只在本 JVM 和临时容器里使用，不打印
        String password = "it-" + UUID.randomUUID().toString().replace("-", "");
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
                .withImagePullPolicy(name -> false) // 相当于 --pull=never：镜像必须已在本地
                .withEnv("MYSQL_ROOT_PASSWORD", password)
                .withEnv("MYSQL_DATABASE", DATABASE)
                .withEnv("TZ", "Asia/Shanghai")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                        "--skip-log-bin", "--innodb-flush-log-at-trx-commit=2")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                .withExposedPorts(PORT)
                .waitingFor(Wait.forLogMessage(".*ready for connections.*port: " + PORT + ".*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        MySqlTestDatabase database = new MySqlTestDatabase(container, password);
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop, "mysql-it-stop"));
        database.waitForJdbc();
        database.runScript(baseline, "baseline");
        for (Path migration : migrations) {
            database.runScript(migration, migration.getFileName().toString());
        }
        return database;
    }

    private static Path requireFile(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少系统属性 -D" + property + "：db 集成测试需要基线 SQL 文件路径。"
                    + "示例：git -C <NUS-doc> show release/V1.0.1.6:'3-数据库文件/ry261-test.sql' > /tmp/baseline17.sql，"
                    + "再加 -D" + BASELINE_PROPERTY + "=/tmp/baseline17.sql");
        }
        Path path = Paths.get(value);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("-D" + property + " 指向的文件不存在：" + value);
        }
        return path.toAbsolutePath();
    }

    private void runScript(Path script, String label) {
        String target = "/tmp/it-" + appliedScripts.size() + ".sql";
        container.copyFileToContainer(MountableFile.forHostPath(script), target);
        try {
            // 口令只通过 exec 的环境变量传给 mysql 客户端，不出现在命令行里
            Container.ExecResult result = container.execInContainer(ExecConfig.builder()
                    .command(new String[]{"sh", "-c",
                            "mysql --default-character-set=utf8mb4 -uroot " + DATABASE + " < " + target})
                    .envVars(Map.of("MYSQL_PWD", password))
                    .build());
            if (result.getExitCode() != 0) {
                Matcher matcher = MYSQL_ERROR.matcher(result.getStderr());
                String error = matcher.find() ? matcher.group() : "exit " + result.getExitCode();
                throw new IllegalStateException("执行 " + label + " 失败：" + error);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("执行 " + label + " 被中断", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("执行 " + label + " 失败", ex);
        }
        appliedScripts.add(label);
    }

    private void waitForJdbc() {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        SQLException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection ignored = DriverManager.getConnection(jdbcUrl(), username(), password())) {
                return;
            } catch (SQLException ex) {
                last = ex;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("MySQL 容器启动后无法连接", last);
    }

    public String jdbcUrl() {
        // 与 application-prod.yaml 的连接参数一致
        return "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(PORT) + "/" + DATABASE
                + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                + "&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true";
    }

    public String username() {
        return "root";
    }

    public String password() {
        return password;
    }

    public List<String> appliedScripts() {
        return List.copyOf(appliedScripts);
    }
}
