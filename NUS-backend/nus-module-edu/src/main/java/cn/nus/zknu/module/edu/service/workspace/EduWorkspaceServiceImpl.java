package cn.nus.zknu.module.edu.service.workspace;

import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceRuntimeRespVO;
import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceTodoRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台 Service 实现
 */
@Service
public class EduWorkspaceServiceImpl implements EduWorkspaceService {

    @Resource
    private StatisticsMapper statisticsMapper;
    @Resource
    private EduCourseResourceMapper eduCourseResourceMapper;
    @Resource
    private EduHomeworkRecordMapper eduHomeworkRecordMapper;
    @Resource
    private EduDataScopeService dataScopeService;

    @Override
    public WorkspaceTodoRespVO getTodos() {
        WorkspaceTodoRespVO resp = new WorkspaceTodoRespVO();
        List<WorkspaceTodoRespVO.TodoItem> todos = new ArrayList<>();

        // 1. 公共课待审批
        if (dataScopeService.isCourseApprover()) {
            Integer pending = statisticsMapper.selectPendingPublicAuditCount();
            if (pending != null && pending > 0) {
                WorkspaceTodoRespVO.TodoItem item = new WorkspaceTodoRespVO.TodoItem();
                item.setCategory("public_audit");
                item.setLabel("公共课待审批");
                item.setCount(pending.longValue());
                item.setRoute("/capacity/course");
                todos.add(item);
            }
        }

        // 2. 课程资源待审
        Long pendingResources = eduCourseResourceMapper.selectCount(
                new LambdaQueryWrapperX<EduCourseResourceDO>()
                        .eq(EduCourseResourceDO::getReviewStatus, 0));
        if (pendingResources != null && pendingResources > 0) {
            WorkspaceTodoRespVO.TodoItem item = new WorkspaceTodoRespVO.TodoItem();
            item.setCategory("resource_review");
            item.setLabel("课程资源待审");
            item.setCount(pendingResources);
            item.setRoute("/infra/resource-review");
            todos.add(item);
        }

        // 3. 待批改作业
        Long pendingCorrections = statisticsMapper.selectPendingCorrections(null);
        if (pendingCorrections != null && pendingCorrections > 0) {
            WorkspaceTodoRespVO.TodoItem item = new WorkspaceTodoRespVO.TodoItem();
            item.setCategory("grading");
            item.setLabel("待批改作业");
            item.setCount(pendingCorrections);
            item.setRoute("/capacity/homework");
            todos.add(item);
        }

        resp.setTodos(todos);
        return resp;
    }

    @Override
    public WorkspaceRuntimeRespVO getRuntime() {
        WorkspaceRuntimeRespVO resp = new WorkspaceRuntimeRespVO();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        // 本周提交数
        Long weekSubmissions = eduHomeworkRecordMapper.selectCount(
                new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                        .ge(EduHomeworkRecordDO::getCreateTime, weekAgo)
                        .in(EduHomeworkRecordDO::getStatus, 1, 2, 4));
        resp.setWeekSubmissionCount(weekSubmissions != null ? weekSubmissions : 0);

        // 本周批改数
        Long weekGradings = eduHomeworkRecordMapper.selectCount(
                new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                        .ge(EduHomeworkRecordDO::getCorrectTime, weekAgo)
                        .isNotNull(EduHomeworkRecordDO::getScore));
        resp.setWeekGradingCount(weekGradings != null ? weekGradings : 0);

        // 本周课堂数（在用班课关联）
        resp.setWeekClassCount(statisticsMapper.selectClassCourseCount());

        // AI 调用（暂无 ai_call_log 表，用 0 占位）
        resp.setWeekAiCallCount(0L);

        // 每日趋势
        List<WorkspaceRuntimeRespVO.DailyStat> dailyStats = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            WorkspaceRuntimeRespVO.DailyStat stat = new WorkspaceRuntimeRespVO.DailyStat();
            stat.setDate(day.format(fmt));
            Long subs = eduHomeworkRecordMapper.selectCount(
                    new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                            .ge(EduHomeworkRecordDO::getCreateTime, dayStart)
                            .lt(EduHomeworkRecordDO::getCreateTime, dayEnd)
                            .in(EduHomeworkRecordDO::getStatus, 1, 2, 4));
            stat.setSubmissions(subs != null ? subs : 0);
            Long grads = eduHomeworkRecordMapper.selectCount(
                    new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                            .ge(EduHomeworkRecordDO::getCorrectTime, dayStart)
                            .lt(EduHomeworkRecordDO::getCorrectTime, dayEnd)
                            .isNotNull(EduHomeworkRecordDO::getScore));
            stat.setGradings(grads != null ? grads : 0);
            dailyStats.add(stat);
        }
        resp.setDailyStats(dailyStats);
        return resp;
    }
}
