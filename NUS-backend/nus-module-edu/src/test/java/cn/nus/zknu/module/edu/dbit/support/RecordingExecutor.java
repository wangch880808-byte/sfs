package cn.nus.zknu.module.edu.dbit.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 代替 AI 初评线程池：不执行任务，只记录每次派发，并在派发的那一刻用一条新连接读出“已提交”的初评状态。
 * <p>
 * 新连接读不到未提交的数据，所以快照里有排队记录就说明派发发生在事务提交之后。
 */
public class RecordingExecutor implements Executor {

    private final DataSource dataSource;
    private final List<Dispatch> dispatches = Collections.synchronizedList(new ArrayList<>());

    public RecordingExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void execute(Runnable command) {
        dispatches.add(new Dispatch(command, readCommittedReviewStatuses()));
    }

    public List<Dispatch> dispatches() {
        synchronized (dispatches) {
            return List.copyOf(dispatches);
        }
    }

    public void clear() {
        dispatches.clear();
    }

    /** 直接从连接池取新连接（不经过 Spring 的事务绑定），只看已提交的数据。 */
    private Map<Long, Integer> readCommittedReviewStatuses() {
        Map<Long, Integer> statuses = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, status FROM edu_homework_ai_review WHERE deleted = 0");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                statuses.put(rs.getLong(1), rs.getInt(2));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取初评快照失败", ex);
        }
        return statuses;
    }

    /** 一次派发：线程池收到的任务，以及派发时其他连接能看到的初评状态（id → status）。 */
    public record Dispatch(Runnable task, Map<Long, Integer> committedReviewStatuses) {
    }
}
