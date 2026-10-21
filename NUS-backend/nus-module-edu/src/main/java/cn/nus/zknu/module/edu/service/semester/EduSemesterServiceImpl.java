package cn.nus.zknu.module.edu.service.semester;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterSaveReqVO;
import cn.nus.zknu.module.edu.dal.redis.RedisKeyConstants;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.infra.service.config.ConfigCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
import java.util.Optional;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_CONFIG_SAVE_FAILED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_DATE_RANGE_INVALID;

@Service
@Slf4j
public class EduSemesterServiceImpl implements EduSemesterService {

    private static final String CATEGORY = "edu";
    private static final int CONFIG_TYPE_CUSTOM = 2;

    private static final String KEY_SEMESTER_NAME = "edu.current.semester.name";
    private static final String KEY_SEMESTER_START_DATE = "edu.current.semester.start-date";
    private static final String KEY_SEMESTER_END_DATE = "edu.current.semester.end-date";
    private static final String KEY_SEMESTER_AUTO_ENABLED = "edu.current.semester.auto-enabled";

    @Resource
    private DataSource dataSource;
    @Resource
    private ConfigCacheService configCacheService;
    @Resource
    private EduPublicCourseSyncScheduler publicCourseSyncScheduler;

    @Override
    @Cacheable(cacheNames = RedisKeyConstants.CURRENT_SEMESTER, key = "'current'", unless = "#result.configReadFailed")
    public EduSemesterRespVO getCurrentSemester() {
        EduSemesterRespVO respVO = new EduSemesterRespVO();
        respVO.setSemesterName(getConfigValue(respVO, KEY_SEMESTER_NAME).orElse("未设置"));
        respVO.setSemesterStartDate(parseDate(getConfigValue(respVO, KEY_SEMESTER_START_DATE).orElse(null)));
        respVO.setSemesterEndDate(parseDate(getConfigValue(respVO, KEY_SEMESTER_END_DATE).orElse(null)));
        respVO.setAutoEnabled(getConfigValue(respVO, KEY_SEMESTER_AUTO_ENABLED).map(Boolean::parseBoolean).orElse(false));
        return respVO;
    }

    @Override
    public void saveCurrentSemester(EduSemesterSaveReqVO reqVO) {
        if (reqVO.getSemesterStartDate().isAfter(reqVO.getSemesterEndDate())) {
            throw exception(SEMESTER_DATE_RANGE_INVALID);
        }
        String operator = String.valueOf(Optional.ofNullable(getLoginUserId()).orElse(0L));
        try {
            executeInTransaction(conn -> {
                // 手工设置需要在同一事务内关闭自动更新，防止后台任务覆盖。
                upsertAutoEnabled(conn, false, operator);
                saveSemester(conn, reqVO.getSemesterName().trim(), reqVO.getSemesterStartDate(),
                        reqVO.getSemesterEndDate(), operator);
            });
        } catch (Exception e) {
            log.error("[saveCurrentSemester] 保存当前学期配置失败", e);
            throw exception(SEMESTER_CONFIG_SAVE_FAILED);
        }
        invalidateSemesterConfigs();
        syncPublicCoursesQuietly();
    }

    @Override
    public void setAutoEnabled(boolean autoEnabled) {
        String operator = String.valueOf(Optional.ofNullable(getLoginUserId()).orElse(0L));
        try {
            executeInTransaction(conn -> {
                upsertAutoEnabled(conn, autoEnabled, operator);
                if (autoEnabled) {
                    EduSemesterPeriod period = calculateAutomaticSemester(LocalDate.now());
                    saveSemester(conn, period.semesterName(), period.startDate(), period.endDate(), operator);
                }
            });
        } catch (Exception e) {
            log.error("[setAutoEnabled] 设置学期自动更新模式失败，autoEnabled={}", autoEnabled, e);
            throw exception(SEMESTER_CONFIG_SAVE_FAILED);
        }
        invalidateSemesterConfigs();
        if (autoEnabled) {
            syncPublicCoursesQuietly();
        }
    }

    @Override
    public String autoUpdateCurrentSemester() {
        EduSemesterPeriod period = calculateAutomaticSemester(LocalDate.now());
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean autoEnabled = getConfigValue(conn, KEY_SEMESTER_AUTO_ENABLED, true)
                        .map(Boolean::parseBoolean).orElse(false);
                if (!autoEnabled) {
                    conn.commit();
                    return "自动更新已关闭，保留当前手动设置";
                }
                boolean changed = !Objects.equals(getConfigValue(conn, KEY_SEMESTER_NAME, false).orElse(null),
                        period.semesterName())
                        || !Objects.equals(getConfigValue(conn, KEY_SEMESTER_START_DATE, false).orElse(null),
                        period.startDate().toString())
                        || !Objects.equals(getConfigValue(conn, KEY_SEMESTER_END_DATE, false).orElse(null),
                        period.endDate().toString());
                if (changed) {
                    saveSemester(conn, period.semesterName(), period.startDate(), period.endDate(), "system");
                }
                conn.commit();
                if (changed) {
                    invalidateSemesterConfigs();
                    syncPublicCoursesQuietly();
                }
                return changed ? "已更新为" + period.semesterName()
                        : "当前已是" + period.semesterName() + "，无需更新";
            } catch (Exception e) {
                rollbackQuietly(conn);
                throw e;
            }
        } catch (Exception e) {
            log.error("[autoUpdateCurrentSemester] 自动更新当前学期失败", e);
            throw exception(SEMESTER_CONFIG_SAVE_FAILED);
        }
    }

    static EduSemesterPeriod calculateAutomaticSemester(LocalDate date) {
        int year = date.getYear();
        Month month = date.getMonth();
        if (month == Month.JANUARY) {
            return new EduSemesterPeriod(year - 1 + "-" + year + " 上学期",
                    LocalDate.of(year - 1, Month.AUGUST, 1), LocalDate.of(year, Month.JANUARY, 31));
        }
        if (month.getValue() >= Month.AUGUST.getValue()) {
            return new EduSemesterPeriod(year + "-" + (year + 1) + " 上学期",
                    LocalDate.of(year, Month.AUGUST, 1), LocalDate.of(year + 1, Month.JANUARY, 31));
        }
        return new EduSemesterPeriod(year - 1 + "-" + year + " 下学期",
                LocalDate.of(year, Month.FEBRUARY, 1), LocalDate.of(year, Month.JULY, 31));
    }

    private void invalidateSemesterConfigs() {
        // JDBC 已提交，必须在投递公共课同步任务前失效，后台线程才能读到新学期。
        if (configCacheService != null) {
            configCacheService.evict(KEY_SEMESTER_NAME);
            configCacheService.evict(KEY_SEMESTER_START_DATE);
            configCacheService.evict(KEY_SEMESTER_END_DATE);
            configCacheService.evict(KEY_SEMESTER_AUTO_ENABLED);
        }
    }

    private void syncPublicCoursesQuietly() {
        try {
            // 学期配置使用 JDBC 事务，此处已显式提交，只投递后台任务。
            publicCourseSyncScheduler.requestAll();
        } catch (Exception ex) {
            log.error("[syncPublicCoursesQuietly][学期切换后公共课同步失败]", ex);
        }
    }

    private Optional<String> getConfigValue(EduSemesterRespVO snapshot, String configKey) {
        try (Connection conn = dataSource.getConnection()) {
            return getConfigValue(conn, configKey, false);
        } catch (Exception e) {
            log.warn("[getConfigValue] 获取配置失败，key={}", configKey, e);
            snapshot.setConfigReadFailed(true);
            return Optional.empty();
        }
    }

    private Optional<String> getConfigValue(Connection conn, String configKey, boolean forUpdate) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(configQuery(forUpdate))) {
            ps.setString(1, configKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("value")) : Optional.empty();
            }
        }
    }

    private String configQuery(boolean forUpdate) {
        return "SELECT value FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1"
                + (forUpdate ? " FOR UPDATE" : "");
    }

    private LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveSemester(Connection conn, String semesterName, LocalDate startDate,
                              LocalDate endDate, String operator) throws Exception {
        upsertConfig(conn, KEY_SEMESTER_NAME, "当前学期", semesterName, "当前学期名称", operator);
        upsertConfig(conn, KEY_SEMESTER_START_DATE, "当前学期开始日期", startDate.toString(),
                "当前学期开始日期", operator);
        upsertConfig(conn, KEY_SEMESTER_END_DATE, "当前学期结束日期", endDate.toString(),
                "当前学期结束日期", operator);
    }

    private void upsertAutoEnabled(Connection conn, boolean autoEnabled, String operator) throws Exception {
        upsertConfig(conn, KEY_SEMESTER_AUTO_ENABLED, "学期自动更新", Boolean.toString(autoEnabled),
                "按自然月自动维护当前学期", operator);
    }

    private void executeInTransaction(SqlAction action) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.execute(conn);
                conn.commit();
            } catch (Exception e) {
                rollbackQuietly(conn);
                throw e;
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (Exception rollbackError) {
            log.warn("[rollbackQuietly] 回滚学期配置事务失败", rollbackError);
        }
    }

    private void upsertConfig(Connection conn, String configKey, String name, String value,
                              String remark, String operator) throws Exception {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT id FROM infra_config WHERE config_key = ? AND deleted = 0 LIMIT 1")) {
            query.setString(1, configKey);
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE infra_config SET value = ?, updater = ?, update_time = NOW() WHERE id = ?")) {
                        update.setString(1, value);
                        update.setString(2, operator);
                        update.setLong(3, id);
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO infra_config (category, type, name, config_key, value, visible, remark, creator, create_time, updater, update_time, deleted) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW(), 0)")) {
                        insert.setString(1, CATEGORY);
                        insert.setInt(2, CONFIG_TYPE_CUSTOM);
                        insert.setString(3, name);
                        insert.setString(4, configKey);
                        insert.setString(5, value);
                        insert.setBoolean(6, true);
                        insert.setString(7, remark);
                        insert.setString(8, operator);
                        insert.setString(9, operator);
                        insert.executeUpdate();
                    }
                }
            }
        }
    }

    record EduSemesterPeriod(String semesterName, LocalDate startDate, LocalDate endDate) {
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute(Connection conn) throws Exception;
    }
}
