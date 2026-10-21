package cn.nus.zknu.module.edu.dbit;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentHomeworkMapper;
import cn.nus.zknu.module.edu.dbit.support.EduDbIntegrationTest;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XML / Wrapper 查询在真实 MySQL 上的冒烟测试：语法、列名、分页计数、口径。
 */
class EduMapperSqlDbTest extends EduDbIntegrationTest {

    @Autowired
    private StudentHomeworkMapper studentHomeworkMapper;
    @Autowired
    private StudentCourseMapper studentCourseMapper;
    @Autowired
    private StatisticsMapper statisticsMapper;
    @Autowired
    private EduHomeworkRecordService recordService;

    // ========== StudentHomeworkMapper：当前任务与往期任务 ==========

    @Test
    void studentHomeworkPage_currentAndHistoryScopes() {
        long teacherId = newUserId();
        Scope active = newScope(teacherId);
        Scope archivedClass = newScope(teacherId, 1, 0);
        long student = newUserId();
        joinClass(active, student);
        joinClass(archivedClass, student);
        LocalDateTime earlier = LocalDateTime.now().minusDays(1);

        long submitted = homework(active).name("已批改").create();
        long open = homework(active).name("未提交").create();
        long archivedDone = homework(active).name("已归档有提交").status(3).create();
        long archivedTodo = homework(active).name("已归档无提交").status(3).create();
        long draft = homework(active).name("草稿").status(0).create();
        long oldClassTask = homework(archivedClass).name("旧班级任务").create();
        long notAssigned = homework(active).name("未分配").create();
        for (long homeworkId : List.of(submitted, open, archivedDone, archivedTodo, draft)) {
            assign(homeworkId, active, student);
        }
        assign(oldClassTask, archivedClass, student);
        insertRecord(submitted, student, EduHomeworkRecordStatusEnum.CORRECTED, 90, earlier);
        insertRecord(archivedDone, student, EduHomeworkRecordStatusEnum.CORRECTED, 75, earlier);
        insertRecord(oldClassTask, student, EduHomeworkRecordStatusEnum.SUBMITTED, null, earlier);
        insertRecord(notAssigned, student, EduHomeworkRecordStatusEnum.SUBMITTED, null, earlier);

        IPage<AppStudentHomeworkRespVO> current = page(student, null, null, false, 10);
        assertEquals(List.of(open, submitted), ids(current));
        assertEquals(2, current.getTotal());
        AppStudentHomeworkRespVO open0 = current.getRecords().get(0);
        assertEquals(EduHomeworkRecordStatusEnum.NOT_SUBMITTED, open0.getStatus());
        assertNull(open0.getRecordId());
        assertEquals(Boolean.FALSE, open0.getHistoryOnly());
        assertEquals(90, current.getRecords().get(1).getMyScore());

        IPage<AppStudentHomeworkRespVO> history = page(student, null, null, true, 10);
        assertEquals(List.of(oldClassTask, archivedDone), ids(history));
        assertEquals(2, history.getTotal());
        assertTrue(history.getRecords().stream().allMatch(item -> Boolean.TRUE.equals(item.getHistoryOnly())));

        // 筛选与分页计数（count 语句由分页插件改写，单独核对）
        assertEquals(List.of(open), ids(page(student, null, 0, false, 10)));
        assertEquals(List.of(submitted), ids(page(student, null, EduHomeworkRecordStatusEnum.CORRECTED, false, 10)));
        assertEquals(List.of(oldClassTask), ids(page(student, archivedClass.courseId(), null, true, 10)));
        IPage<AppStudentHomeworkRespVO> firstOfTwo = page(student, null, null, false, 1);
        assertEquals(2, firstOfTwo.getTotal());
        assertEquals(1, firstOfTwo.getRecords().size());
        IPage<AppStudentHomeworkRespVO> historyFirst = page(student, null, null, true, 1);
        assertEquals(2, historyFirst.getTotal());
    }

    // ========== StudentCourseMapper：等级闯关 ==========

    @Test
    void selectLevelTasks_returnsAssignedTasks_withLatestCorrectScoreFromHistory() {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long student = newUserId();
        long classmate = newUserId();
        joinClass(scope, student);
        joinClass(scope, classmate);
        LocalDateTime past = LocalDateTime.now().minusDays(3);
        long basic = homework(scope).level(1).passScore(60).deadline(past).create();
        long middle = homework(scope).level(2).passScore(null).deadline(LocalDateTime.now().plusDays(2)).create();
        long advancedArchived = homework(scope).level(3).status(3).deadline(past).create();
        long basicOnlyForClassmate = homework(scope).level(1).deadline(past).create();
        assign(basic, scope, student);
        assign(middle, scope, student);
        assign(advancedArchived, scope, student);
        assign(basicOnlyForClassmate, scope, classmate);
        // 已打回：记录上是 50 分，但最近一次批改是 70 分
        long redoRecord = insertRecord(basic, student, EduHomeworkRecordStatusEnum.REDO, 50, past.minusDays(1));
        history(redoRecord, basic, student, "CORRECT", 2, 50);
        history(redoRecord, basic, student, "CORRECT", 2, 70);
        history(redoRecord, basic, student, "REDO", 3, 70);
        insertRecord(advancedArchived, student, EduHomeworkRecordStatusEnum.CORRECTED, 88, past.minusDays(1));

        List<StudentLevelTaskRow> rows = studentCourseMapper.selectLevelTasks(student, scope.courseId(), false);
        assertEquals(List.of(basic, middle), rows.stream().map(StudentLevelTaskRow::getHomeworkId).toList());
        StudentLevelTaskRow basicRow = rows.get(0);
        assertEquals(1, basicRow.getHomeworkLevel());
        assertEquals(60, basicRow.getPassScore());
        assertEquals(100, basicRow.getFullScore());
        assertEquals(redoRecord, basicRow.getRecordId());
        assertEquals(EduHomeworkRecordStatusEnum.REDO, basicRow.getRecordStatus());
        assertEquals(50, basicRow.getRecordScore());
        assertEquals(70, basicRow.getLastCorrectScore());
        assertNotNull(basicRow.getDeadline());
        StudentLevelTaskRow middleRow = rows.get(1);
        assertEquals(60, middleRow.getPassScore(), "pass_score 为 NOT NULL DEFAULT 60");
        assertNull(middleRow.getRecordId());

        // 历史口径：归档但提交过的高级任务也算
        List<StudentLevelTaskRow> historyRows = studentCourseMapper.selectLevelTasks(student, scope.courseId(), true);
        assertEquals(List.of(basic, middle, advancedArchived),
                historyRows.stream().map(StudentLevelTaskRow::getHomeworkId).toList());

        // 班级已发布的各等级任务数：含没分配给该学生的任务，不含已归档任务
        Map<Integer, Integer> counts = studentCourseMapper.selectClassLevelTaskCounts(student, scope.courseId())
                .stream().collect(Collectors.toMap(row -> ((Number) row.get("homeworkLevel")).intValue(),
                        row -> ((Number) row.get("taskCount")).intValue()));
        assertEquals(Map.of(1, 2, 2, 1), counts);

        Map<String, Object> progress = studentCourseMapper.selectCourseProgress(student, scope.courseId(), false);
        assertEquals(2, ((Number) progress.get("totalHomework")).intValue());
        assertTrue(studentCourseMapper.existsStudentCourse(student, scope.courseId()));
        assertTrue(studentCourseMapper.existsStudentCourseHistory(student, scope.courseId()));
    }

    // ========== StatisticsMapper：看板计数 ==========

    @Test
    void dashboardCounts() {
        long totalStudentsBefore = statisticsMapper.selectTotalStudents();
        long totalTeachersBefore = statisticsMapper.selectTotalTeachers();
        long totalCoursesBefore = statisticsMapper.selectTotalCourses();
        long totalHomeworksBefore = statisticsMapper.selectTotalHomeworks();
        long aiCorrectionsBefore = statisticsMapper.selectAiCorrections();

        long teacherId = insertUser(1);
        long student1 = insertUser(2);
        long student2 = insertUser(2);
        long student3 = insertUser(2);
        Scope scope = newScope(teacherId);
        long homeworkId = homework(scope).passScore(60).create();
        homework(scope).status(0).create(); // 草稿不计
        LocalDateTime now = LocalDateTime.now();
        insertRecord(homeworkId, student1, EduHomeworkRecordStatusEnum.SUBMITTED, null, now);
        insertRecord(homeworkId, student2, EduHomeworkRecordStatusEnum.RESUBMITTED, 40, now);
        long corrected = insertRecord(homeworkId, student3, EduHomeworkRecordStatusEnum.CORRECTED, 95, now);
        aiReview(corrected, homeworkId, student3, HomeworkAiReviewStatusEnum.ACCEPTED);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("class_id", scope.classId());
        message.put("course_id", scope.courseId());
        message.put("student_id", student1);
        message.put("content", "老师好");
        message.put("type", 0);
        message.put("read_status", 0);
        insert("edu_message", message);

        assertEquals(totalStudentsBefore + 3, statisticsMapper.selectTotalStudents());
        assertEquals(totalTeachersBefore + 1, statisticsMapper.selectTotalTeachers());
        assertEquals(totalCoursesBefore + 1, statisticsMapper.selectTotalCourses());
        assertEquals(totalHomeworksBefore + 1, statisticsMapper.selectTotalHomeworks());
        assertEquals(aiCorrectionsBefore + 1, statisticsMapper.selectAiCorrections());
        assertEquals(3, statisticsMapper.selectWeekSubmissions(teacherId));
        assertEquals(2, statisticsMapper.selectPendingCorrections(teacherId));
        assertEquals(1, statisticsMapper.selectTeacherCourseCount(teacherId));
        assertEquals(1, statisticsMapper.selectUnreadMessages(teacherId));
        List<Map<String, Object>> recent = statisticsMapper.selectRecentHomeworks(teacherId);
        assertEquals(1, recent.size());
        assertEquals(homeworkId, ((Number) recent.get(0).get("homeworkId")).longValue());

        Map<String, Object> passRate = statisticsMapper.selectTotalPassRate(teacherId, List.of(scope.classId()),
                scope.courseId(), null, 0.75, 0.85);
        assertEquals(1, ((Number) passRate.get("totalRecords")).intValue());
        assertEquals(1, ((Number) passRate.get("excellentCount")).intValue());
        assertEquals(1, statisticsMapper.selectWorkTrend(scope.courseId(), scope.classId()).size());
        assertEquals(1, statisticsMapper.selectTeacherCourses(teacherId).size());
        assertEquals(1, statisticsMapper.selectCourseClasses(teacherId, scope.courseId()).size());
        assertEquals(1, statisticsMapper.selectHomeworkTopStudents(homeworkId).size());
        assertEquals(1, statisticsMapper.selectHomeworkLastStudents(homeworkId).size());
        assertEquals(1, statisticsMapper.selectCourseAnalysisAvg(scope.courseId(), null).size());
        assertNotNull(statisticsMapper.selectClassIdsByDept(null, null));
    }

    // ========== 批改队列 ==========

    @Test
    void gradingQueue_listsEveryGradableRecord_withLatestAiStatus_inQueueOrder() {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long homeworkId = homework(scope).create();
        long otherHomework = homework(scope).create();
        LocalDateTime base = LocalDateTime.now().minusHours(2).withNano(0);
        long resubmitted = insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.RESUBMITTED, 40, base);
        long submittedLate = insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.SUBMITTED, null,
                base.plusMinutes(10));
        long submittedEarly = insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.SUBMITTED, null,
                base.plusMinutes(1));
        long corrected = insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.CORRECTED, 90, base);
        long redo = insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.REDO, 50, base);
        insertRecord(otherHomework, newUserId(), EduHomeworkRecordStatusEnum.SUBMITTED, null, base);
        aiReview(submittedEarly, homeworkId, 0L, HomeworkAiReviewStatusEnum.FAILED);
        aiReview(submittedEarly, homeworkId, 0L, HomeworkAiReviewStatusEnum.READY); // 最新一次
        aiReview(resubmitted, homeworkId, 0L, HomeworkAiReviewStatusEnum.QUEUED);
        loginAs(teacherId);

        EduHomeworkRecordQueueRespVO queue = recordService.getGradingQueue(homeworkId);

        assertEquals(5, queue.getTotal());
        assertEquals(List.of(submittedEarly, submittedLate, corrected, redo, resubmitted),
                queue.getList().stream().map(EduHomeworkRecordQueueItemRespVO::getId).toList());
        Map<Long, EduHomeworkRecordQueueItemRespVO> byId = queue.getList().stream()
                .collect(Collectors.toMap(EduHomeworkRecordQueueItemRespVO::getId, Function.identity()));
        assertEquals(HomeworkAiReviewStatusEnum.READY, byId.get(submittedEarly).getAiReviewStatus());
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, byId.get(resubmitted).getAiReviewStatus());
        assertNull(byId.get(corrected).getAiReviewStatus());
        assertNotNull(byId.get(corrected).getUpdateTime());

        // 批改分页：“待批改”包含已提交与已重交
        EduHomeworkRecordPageReqVO pageReq = new EduHomeworkRecordPageReqVO();
        pageReq.setHomeworkId(homeworkId);
        pageReq.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);
        PageResult<EduHomeworkRecordDO> pending = recordService.getCorrectPage(pageReq);
        assertEquals(3, pending.getTotal());
        assertEquals(List.of(submittedEarly, submittedLate, resubmitted),
                pending.getList().stream().map(EduHomeworkRecordDO::getId).toList());
        // 不传任务时按“我布置的任务”汇总
        EduHomeworkRecordPageReqVO allReq = new EduHomeworkRecordPageReqVO();
        allReq.setPageNo(1);
        allReq.setPageSize(20);
        assertEquals(6, recordService.getCorrectPage(allReq).getTotal());
    }

    // ========== 工具 ==========

    private IPage<AppStudentHomeworkRespVO> page(long studentId, Long courseId, Integer status, boolean history,
                                                 int size) {
        return studentHomeworkMapper.selectHomeworkPage(new Page<>(1, size), studentId, courseId, status, history);
    }

    private static List<Long> ids(IPage<AppStudentHomeworkRespVO> page) {
        return page.getRecords().stream().map(AppStudentHomeworkRespVO::getHomeworkId).toList();
    }

    private void history(long recordId, long homeworkId, long studentId, String event, int toStatus, Integer score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("record_id", recordId);
        row.put("homework_id", homeworkId);
        row.put("student_id", studentId);
        row.put("event_type", event);
        row.put("to_status", toStatus);
        row.put("score", score);
        insert("edu_homework_record_history", row);
    }

    private void aiReview(long recordId, long homeworkId, long studentId, int status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("homework_record_id", recordId);
        row.put("homework_id", homeworkId);
        row.put("student_id", studentId);
        row.put("status", status);
        row.put("submission_mode", "text");
        row.put("generation_token", UUID.randomUUID().toString());
        insert("edu_homework_ai_review", row);
    }

    /** 看板按 system_users 统计；只写统计需要的列，口令留空。 */
    private long insertUser(int userType) {
        long seq = newUserId();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", "it" + seq);
        row.put("nickname", "it" + seq);
        row.put("user_type", userType);
        row.put("status", 0);
        return insert("system_users", row);
    }
}
