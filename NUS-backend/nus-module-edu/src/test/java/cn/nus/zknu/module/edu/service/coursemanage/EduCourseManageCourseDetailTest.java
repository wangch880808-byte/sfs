package cn.nus.zknu.module.edu.service.coursemanage;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageClassDetailRespVO;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EduCourseManageCourseDetailTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduCourseManageServiceImpl courseManageService;

    @Mock
    private CapacityCourseApi capacityCourseLiteMapper;
    @Mock
    private EduClassCourseMapper eduClassCourseMapper;
    @Mock
    private EduClassMapper eduClassMapper;
    @Mock
    private EduClassStudentMapper eduClassStudentMapper;
    @Mock
    private EduHomeworkMapper eduHomeworkMapper;
    @Mock
    private EduCourseResourceMapper eduCourseResourceMapper;
    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private EduClassCourseAssistantService assistantService;
    @Mock
    private EduDataScopeService dataScopeService;
    @Mock
    private cn.nus.zknu.framework.security.core.service.SecurityFrameworkService securityFrameworkService;
    @org.mockito.Spy
    private EduMetricPolicy metricPolicy = new EduMetricPolicy();

    @org.junit.jupiter.api.BeforeEach
    void initMetricPolicy() {
        org.springframework.test.util.ReflectionTestUtils.setField(metricPolicy, "configApi", configApi);
    }
    @Mock
    private EduCourseStatMapper courseStatMapper;
    @Mock
    private ConfigApi configApi;
    @Mock
    private EduHomeworkAssigneeMapper eduHomeworkAssigneeMapper;
    @Mock
    private EduMessageMapper eduMessageMapper;

    @Test
    void getCourseDetail_marksArchivedBindingsAndClasses() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(1L);
        course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        // 班课正常且班级正常、班课已封存、班级已封存三种情况
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                classCourse(11L, 21L, 0), classCourse(12L, 22L, 1), classCourse(13L, 23L, 0)));
        when(eduClassMapper.selectByIds(anyCollection())).thenReturn(List.of(
                clazz(21L, 0), clazz(22L, 0), clazz(23L, 1)));

        CourseManageDetailRespVO detail;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            detail = courseManageService.getCourseDetail(1L);
        }

        Map<Long, Boolean> archivedByBinding = detail.getClasses().stream().collect(Collectors.toMap(
                CourseManageDetailRespVO.CourseClassRespVO::getClassCourseId,
                CourseManageDetailRespVO.CourseClassRespVO::getArchived));
        assertEquals(Map.of(11L, false, 12L, true, 13L, true), archivedByBinding);
    }

    @Test
    void getCourseDetail_readsTeamAndClassAssistantsFromAssociationTables() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(1L);
        course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        // 登录人 8 只在课程团队关联表里（不是负责教师），应能查看
        when(capacityCourseLiteMapper.isCourseMember(1L, 8L)).thenReturn(true);
        when(capacityCourseLiteMapper.getCourseMemberUserIds(1L)).thenReturn(List.of(8L));
        EduClassCourseDO binding = classCourse(11L, 21L, 0);
        binding.setAssistantTeacherIds("99");
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        when(eduClassMapper.selectByIds(anyCollection())).thenReturn(List.of(clazz(21L, 0)));
        when(assistantService.getTeacherIdsMap(List.of(11L))).thenReturn(Map.of(11L, List.of(31L, 30L)));
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(
                user(7L, "张老师"), user(8L, "李老师"), user(30L, "王助教"), user(31L, "赵助教")));

        CourseManageDetailRespVO detail;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            detail = courseManageService.getCourseDetail(1L);
        }

        CourseManageDetailRespVO.CourseClassRespVO classVO = detail.getClasses().get(0);
        assertEquals("31,30", classVO.getAssistantTeacherIds());
        assertEquals("赵助教、王助教", classVO.getAssistantTeacherNames());
        assertEquals("张老师", detail.getTeacherName());
    }

    @ParameterizedTest(name = "{0}: visibleTasks={2}, canManage={3}")
    @CsvSource({
            "globalAdmin, 50, 3, true",
            "publicApprover, 51, 3, false",
            "owner, 7, 3, true",
            "teamMember, 8, 3, true",
            "otherTeacher, 9, 1, false"
    })
    void detailsUseCourseVisibilityAndManagementRules(String role, Long userId, int taskCount,
                                                     boolean canManage) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(1L);
        course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(dataScopeService.canManageCourse(1L)).thenReturn(canManage);
        lenient().when(securityFrameworkService.hasPermission(anyString())).thenReturn(true);
        switch (role) {
            case "globalAdmin" -> when(dataScopeService.isGlobalAdmin()).thenReturn(true);
            case "publicApprover" -> when(capacityCourseLiteMapper.isCourseAdmin(userId)).thenReturn(true);
            case "teamMember" -> when(capacityCourseLiteMapper.isCourseMember(1L, userId)).thenReturn(true);
            case "otherTeacher" -> when(dataScopeService.isOnlinePublicCourseForTeacher(1L)).thenReturn(true);
        }
        EduClassCourseDO binding = classCourse(11L, 21L, 0);
        binding.setSemesterName("2026-2027-1");
        when(eduClassCourseMapper.selectById(11L)).thenReturn(binding);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                homework(101L, 7L), homework(102L, 8L), homework(103L, 9L)));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            CourseManageDetailRespVO detail = courseManageService.getCourseDetail(1L);
            assertEquals(taskCount, detail.getTasks().size());
            assertEquals(canManage, detail.getCanManage());
            for (var task : detail.getTasks()) {
                boolean owned = java.util.Objects.equals(userId, task.getHomeworkId() == 101L ? 7L : task.getHomeworkId() == 102L ? 8L : 9L);
                assertEquals(owned, task.getCanManage());
                assertEquals(owned, task.getCanGrade());
            }
            assertEquals(taskCount, courseManageService.getClassDetail(11L).getTasks().size());
            if ("otherTeacher".equals(role)) {
                assertEquals(103L, detail.getTasks().get(0).getHomeworkId());
            }
        }
        verify(dataScopeService).canManageCourse(1L);
        if (role.equals("globalAdmin") || role.equals("publicApprover")) verifyNoInteractions(securityFrameworkService);
        else {
            verify(securityFrameworkService).hasPermission("edu:homework:update");
            verify(securityFrameworkService).hasPermission("edu:homework:delete");
            verify(securityFrameworkService).hasPermission("edu:homework-record:correct");
            verifyNoMoreInteractions(securityFrameworkService);
        }
        var visibleIds = "otherTeacher".equals(role) ? List.of(103L) : List.of(101L, 102L, 103L);
        verify(courseStatMapper).selectAssigneeCounts(visibleIds);
        verify(courseStatMapper).selectRecordStats(visibleIds);
        verify(courseStatMapper).selectAssignedRecords(visibleIds);
        verify(eduHomeworkAssigneeMapper, never()).selectListByHomeworkId(any());
    }

    @Test
    void courseUsesOneBatchPerMetricAndPreservesTaskOrderAndEmptyScores() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(1L);
        course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classCourse(11L, 21L, 0)));
        List<EduHomeworkDO> tasks = new ArrayList<>();
        for (long id = 120; id > 0; id--) tasks.add(homework(id, 7L));
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        var count = new EduCourseStatMapper.HomeworkCount();
        count.setHomeworkId(120L);
        count.setStudentCount(8);
        var classCount = new EduCourseStatMapper.ClassCount();
        classCount.setClassId(21L);
        classCount.setStudentCount(10);
        var stat = new EduCourseStatMapper.HomeworkStat();
        stat.setHomeworkId(120L);
        stat.setSubmittedCount(4);
        stat.setCorrectedCount(1);
        stat.setScoreTotal(241L);
        stat.setScoreCount(3);
        when(courseStatMapper.selectAssigneeCounts(anyCollection())).thenReturn(List.of(count));
        when(courseStatMapper.selectRecordStats(anyCollection())).thenReturn(List.of(stat));
        when(courseStatMapper.selectClassStudentCounts(anyCollection())).thenReturn(List.of(classCount));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getCourseDetail(1L);
            assertEquals(tasks.stream().map(EduHomeworkDO::getId).toList(),
                    detail.getTasks().stream().map(CourseManageDetailRespVO.CourseTaskRespVO::getHomeworkId).toList());
            assertEquals(10, detail.getClasses().get(0).getStudentCount());
            var first = detail.getTasks().get(0);
            assertEquals(8, first.getStudentCount());
            assertEquals(4, first.getSubmittedCount());
            assertEquals(1, first.getCorrectedCount());
            assertEquals(80.3, first.getAvgScore());
            assertEquals(0D, detail.getTasks().get(1).getAvgScore());
        }
        verify(courseStatMapper).selectAssigneeCounts(tasks.stream().map(EduHomeworkDO::getId).toList());
        verify(courseStatMapper).selectRecordStats(tasks.stream().map(EduHomeworkDO::getId).toList());
        verify(courseStatMapper).selectClassStudentCounts(java.util.Set.of(21L));
        verifyNoInteractions(eduHomeworkAssigneeMapper, metricPolicy);
        verifyNoMoreInteractions(courseStatMapper);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                EduHomeworkDO.class);
        ArgumentCaptor<Wrapper<EduHomeworkDO>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(eduHomeworkMapper).selectList(wrapper.capture());
        // 状态展示由前端筛选，服务端仍按任务 ID 倒序读取。
        assertFalse(wrapper.getValue().getSqlSegment().contains("status"));
        assertTrue(wrapper.getValue().getSqlSegment().contains("ORDER BY id DESC"));
    }

    @Test
    void classReusesBatchAssigneesAndReadsMetricConfigurationOnce() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(1L);
        course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(eduClassCourseMapper.selectById(11L)).thenReturn(classCourse(11L, 21L, 0));
        EduClassStudentDO member = new EduClassStudentDO();
        member.setClassId(21L);
        member.setStudentId(31L);
        when(eduClassStudentMapper.selectListByClassId(21L)).thenReturn(List.of(member));
        var tasks = List.of(homework(103L, 7L), homework(102L, 7L), homework(101L, 7L));
        tasks.forEach(task -> task.setScore(200));
        tasks.get(0).setStatus(3);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        List<EduHomeworkRecordDO> records = new ArrayList<>();
        List<EduHomeworkAssigneeDO> assignees = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            var record = new EduHomeworkRecordDO();
            record.setHomeworkId(tasks.get(i).getId());
            record.setStudentId(31L);
            record.setStatus(i == 2 ? 4 : 2);
            record.setScore(new Integer[]{170, 121, null}[i]);
            records.add(record);
            var assignee = new EduHomeworkAssigneeDO();
            assignee.setHomeworkId(record.getHomeworkId());
            assignee.setStudentId(31L);
            assignees.add(assignee);
        }
        when(eduHomeworkAssigneeMapper.selectListByHomeworkIds(List.of(103L, 102L, 101L))).thenReturn(assignees);
        when(courseStatMapper.selectAssignedRecords(List.of(103L, 102L, 101L))).thenReturn(records);
        when(configApi.getConfigValueByKey("edu.pass.score")).thenReturn("60.25");
        doReturn(new EduMetricPolicy.ScoreRatios(0.75, 0.85)).when(metricPolicy).scoreRatios();
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getClassDetail(11L);
            assertEquals(List.of(103L, 102L, 101L), detail.getTasks().stream()
                    .map(CourseManageClassDetailRespVO.ClassTaskRespVO::getHomeworkId).toList());
            assertEquals(3, detail.getMembers().get(0).getTotalTaskCount());
            assertEquals(3, detail.getMembers().get(0).getFinishedTaskCount());
            assertEquals(145.5, detail.getMembers().get(0).getAvgScore());
            assertEquals(100D, detail.getScoreAnalysis().getPassRate());
            assertEquals(List.of(1, 0, 1, 0), detail.getScoreAnalysis().getScoreRanges().stream()
                    .map(CourseManageClassDetailRespVO.ScoreRangeRespVO::getCount).toList());
        }
        verify(eduHomeworkAssigneeMapper).selectListByHomeworkIds(List.of(103L, 102L, 101L));
        verify(eduHomeworkAssigneeMapper, never()).selectListByHomeworkId(any());
        verify(courseStatMapper).selectAssignedRecords(List.of(103L, 102L, 101L));
        verifyNoMoreInteractions(courseStatMapper);
        verify(configApi).getConfigValueByKey("edu.pass.score");
        verify(metricPolicy).scoreRatios();
        verify(metricPolicy, never()).passScore(any());
        verify(metricPolicy, never()).scoreBand(anyDouble(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @CsvSource(value = {"60.25", "NaN", "Infinity", "-1", "101", "bad", "NULL"}, nullValues = "NULL")
    @SuppressWarnings("unchecked")
    void requestPassScoresMatchMetricPolicyIncludingFallbackAndRounding(String configured) {
        when(configApi.getConfigValueByKey("edu.pass.score")).thenReturn(configured);
        var tasks = List.of(homework(1L, 7L), homework(2L, 7L), homework(3L, 7L), homework(4L, 7L));
        tasks.get(0).setScore(200);
        tasks.get(1).setScore(99);
        tasks.get(2).setScore(null);
        tasks.get(3).setScore(200);
        tasks.get(3).setPassScore(125);
        Map<Long, List<EduHomeworkRecordDO>> records = new java.util.HashMap<>();
        tasks.forEach(task -> {
            var record = new EduHomeworkRecordDO();
            record.setStatus(2);
            record.setScore(10);
            records.put(task.getId(), List.of(record));
        });
        Map<Long, Integer> actual = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                courseManageService, "getPassScores", tasks, records);
        verify(configApi).getConfigValueByKey("edu.pass.score");
        var originalPolicy = new EduMetricPolicy();
        org.springframework.test.util.ReflectionTestUtils.setField(originalPolicy, "configApi", configApi);
        tasks.forEach(task -> assertEquals(originalPolicy.passScore(task), actual.get(task.getId())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestPassScoresKeepFallbackWhenConfigReadFailsAndSkipExplicitPassScores() {
        var task = homework(1L, 7L);
        task.setScore(200);
        var record = new EduHomeworkRecordDO();
        record.setStatus(2);
        record.setScore(0);
        when(configApi.getConfigValueByKey("edu.pass.score")).thenThrow(new IllegalStateException("测试配置读取失败"));
        Map<Long, Integer> scores = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                courseManageService, "getPassScores", List.of(task), Map.of(1L, List.of(record)));
        assertEquals(120, scores.get(1L));
        task.setPassScore(130);
        scores = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                courseManageService, "getPassScores", List.of(task), Map.of(1L, List.of(record)));
        assertEquals(130, scores.get(1L));
        verify(configApi).getConfigValueByKey("edu.pass.score");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("taskCapabilityCases")
    void taskCapabilitiesMatchWriteStatesAndSingleTaskGrading(int status, boolean update, boolean delete, boolean correct) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L); course.setStatus(0);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classCourse(11L, 21L, 0)));
        var own = homework(101L, 7L); own.setStatus(status);
        var other = homework(102L, 8L); other.setStatus(status);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(own, other));
        when(securityFrameworkService.hasPermission("edu:homework:update")).thenReturn(update);
        when(securityFrameworkService.hasPermission("edu:homework:delete")).thenReturn(delete);
        when(securityFrameworkService.hasPermission("edu:homework-record:correct")).thenReturn(correct);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getCourseDetail(1L);
            var row = detail.getTasks().get(0);
            assertEquals(delete || (update && status != 3), row.getCanManage());
            assertEquals(correct && status != 3, row.getCanGrade());
            assertFalse(detail.getTasks().get(1).getCanManage());
            assertFalse(detail.getTasks().get(1).getCanGrade());
            var controller = new cn.nus.zknu.module.edu.controller.admin.homework.EduHomeworkController();
            var service = mock(cn.nus.zknu.module.edu.service.homework.EduHomeworkService.class);
            when(service.getHomework(101L)).thenReturn(own);
            org.springframework.test.util.ReflectionTestUtils.setField(controller, "eduHomeworkService", service);
            org.springframework.test.util.ReflectionTestUtils.setField(controller, "securityFrameworkService", securityFrameworkService);
            verify(securityFrameworkService).hasPermission("edu:homework:update");
            verify(securityFrameworkService).hasPermission("edu:homework:delete");
            verify(securityFrameworkService).hasPermission("edu:homework-record:correct");
            verifyNoMoreInteractions(securityFrameworkService);
            clearInvocations(securityFrameworkService);
            assertEquals(row.getCanGrade(), controller.getHomework(101L).getData().getCanGrade());
        }
        verify(eduHomeworkMapper).selectList(any(Wrapper.class));
        verify(eduHomeworkMapper, never()).selectById(anyLong());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> taskCapabilityCases() {
        return java.util.stream.IntStream.range(0, 4).boxed().flatMap(status ->
                java.util.stream.IntStream.range(0, 8).mapToObj(bits ->
                        org.junit.jupiter.params.provider.Arguments.of(status, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0)));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,true", "1,0,0,false", "0,1,0,false", "0,0,1,false"})
    void updateOnlyCapabilityUsesExistingCourseClassAndBindingStates(int courseStatus, int classStatus, int bindingStatus,
                                                                    boolean expected) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L); course.setStatus(courseStatus);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classCourse(11L, 21L, bindingStatus)));
        when(eduClassMapper.selectByIds(anyCollection())).thenReturn(List.of(clazz(21L, classStatus)));
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(homework(101L, 7L)));
        when(securityFrameworkService.hasPermission("edu:homework:update")).thenReturn(true);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(expected, courseManageService.getCourseDetail(1L).getTasks().get(0).getCanManage());
            when(securityFrameworkService.hasPermission("edu:homework:delete")).thenReturn(true);
            assertTrue(courseManageService.getCourseDetail(1L).getTasks().get(0).getCanManage());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL,true", "NULL,旧学期,true", "本学期,NULL,true", "本学期,本学期,true", "' 本学期 ',本学期,true", "本学期,旧学期,false"}, nullValues = "NULL")
    void updateOnlyCapabilityKeepsTrimmedSemesterAndBlankFallback(String taskSemester, String bindingSemester, boolean expected) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        var binding = classCourse(11L, 21L, 0); binding.setSemesterName(bindingSemester);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        var task = homework(101L, 7L); task.setSemesterName(taskSemester);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(securityFrameworkService.hasPermission("edu:homework:update")).thenReturn(true);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(expected, courseManageService.getCourseDetail(1L).getTasks().get(0).getCanManage());
        }
    }

    @Test
    void ownArchivedPublicRowCanBeViewedWithoutActiveClaimButCannotOperate() {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(9L); course.setIsPublicCourse(0);
        var row = classCourse(11L, 21L, 1); row.setClaimTeacherId(7L);
        when(eduClassCourseMapper.selectById(11L)).thenReturn(row);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(dataScopeService.isPublicCourse(1L)).thenReturn(true);
        doThrow(new cn.nus.zknu.framework.common.exception.ServiceException(
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN))
                .when(dataScopeService).validateClassCourseAccess(21L, 1L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getClassDetail(11L);
            assertEquals(11L, detail.getClassCourseId());
            assertFalse(detail.getCanOperate());
            verify(dataScopeService, never()).canAssignToPublicClass(anyLong(), anyLong());
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            assertThrows(cn.nus.zknu.framework.common.exception.ServiceException.class,
                    () -> courseManageService.getClassDetail(11L));
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 10, 120})
    void capabilityCalculationKeepsBatchQueryCountsConstant(int count) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classCourse(11L, 21L, 0)));
        var tasks = java.util.stream.IntStream.range(0, count).mapToObj(i -> homework(100L + i, 7L)).toList();
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        when(securityFrameworkService.hasPermission(anyString())).thenReturn(true);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var rows = courseManageService.getCourseDetail(1L).getTasks();
            assertEquals(count, rows.size());
            assertTrue(rows.stream().allMatch(row -> row.getCanManage() && row.getCanGrade()));
        }
        verify(securityFrameworkService).hasPermission("edu:homework:update");
        verify(securityFrameworkService).hasPermission("edu:homework:delete");
        verify(securityFrameworkService).hasPermission("edu:homework-record:correct");
        verifyNoMoreInteractions(securityFrameworkService);
        verify(capacityCourseLiteMapper).getCourse(1L);
        verify(capacityCourseLiteMapper).getCourseMemberUserIds(1L);
        verify(capacityCourseLiteMapper, times(2)).isCourseAdmin(7L);
        verifyNoMoreInteractions(capacityCourseLiteMapper);
        verify(eduClassCourseMapper).selectList(any(Wrapper.class));
        verify(eduClassMapper).selectByIds(java.util.Set.of(21L));
        verify(adminUserMapper).selectByIds(java.util.Set.of(7L));
        verify(assistantService).getTeacherIdsMap(List.of(11L));
        verify(eduHomeworkMapper).selectList(any(Wrapper.class));
        verify(eduCourseResourceMapper).selectList(any(Wrapper.class));
        verify(courseStatMapper).selectAssigneeCounts(tasks.stream().map(EduHomeworkDO::getId).toList());
        verify(courseStatMapper).selectRecordStats(tasks.stream().map(EduHomeworkDO::getId).toList());
        verify(courseStatMapper).selectClassStudentCounts(java.util.Set.of(21L));
        verifyNoMoreInteractions(eduClassCourseMapper, eduClassMapper, adminUserMapper, assistantService,
                eduHomeworkMapper, eduCourseResourceMapper, courseStatMapper);
        verifyNoInteractions(eduHomeworkAssigneeMapper, metricPolicy);
    }


    @ParameterizedTest
    @CsvSource({"teacher,false,false", "team,false,false", "owner,true,false", "admin,true,true", "approver,true,false"})
    void publicDetailNarrowsClassesAndStatisticsAndKeepsReleasedTasks(String role, boolean full, boolean admin) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(9L);
        course.setIsPublicCourse(0); course.setStatus(0);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(dataScopeService.isPublicCourse(1L)).thenReturn(true);
        when(dataScopeService.canClaimPublicCourse(course)).thenReturn(true);
        when(dataScopeService.canReleasePublicClaim(7L)).thenReturn(true);
        if (full) when(dataScopeService.canReleasePublicClaim(8L)).thenReturn(admin || role.equals("approver"));
        when(dataScopeService.canViewAllPublic(1L)).thenReturn(full);
        when(dataScopeService.isGlobalAdmin()).thenReturn(admin);
        lenient().when(dataScopeService.isOnlinePublicCourseForTeacher(1L)).thenReturn(true);
        when(dataScopeService.canManageCourse(1L)).thenReturn(admin || role.equals("owner") || role.equals("team"));
        var mine = classCourse(14L, 21L, 0); mine.setClaimTeacherId(7L);
        var archived = classCourse(13L, 22L, 1); archived.setClaimTeacherId(7L);
        var other = classCourse(12L, 23L, 0); other.setClaimTeacherId(8L);
        var empty = classCourse(11L, 24L, 0);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mine, archived, other, empty));
        when(eduClassMapper.selectByIds(anyCollection())).thenReturn(List.of(clazz(21L, 0), clazz(22L, 0), clazz(23L, 0), clazz(24L, 0)));
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(user(7L, "认领教师"), user(8L, "他人教师"), user(9L, "负责人")));
        var former = homework(101L, 7L); former.setClassId(24L);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(former, homework(102L, 8L)));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getCourseDetail(1L);
            assertTrue(detail.getClaimMode());
            assertTrue(detail.getCanClaim());
            assertTrue(detail.getClasses().get(0).getCanRelease());
            assertTrue(detail.getClasses().get(1).getCanRelease());
            assertEquals(full, detail.getViewAllReadOnly());
            assertEquals(full ? List.of(14L, 13L, 12L, 11L) : List.of(14L, 13L), detail.getClasses().stream()
                    .map(CourseManageDetailRespVO.CourseClassRespVO::getClassCourseId).toList());
            assertEquals("认领教师", detail.getClasses().get(0).getClaimTeacherName());
            assertEquals(7L, detail.getClasses().get(0).getOwnerId());
            assertEquals("认领教师", detail.getClasses().get(0).getOwnerName());
            assertTrue(detail.getClasses().get(0).getCanOperate());
            assertTrue(detail.getClasses().get(1).getArchived());
            assertTrue(detail.getClasses().get(1).getCanOperate());
            if (full) {
                assertEquals(admin, detail.getClasses().get(2).getCanOperate());
                assertEquals(admin || role.equals("approver"), detail.getClasses().get(2).getCanRelease());
                assertFalse(detail.getClasses().get(3).getCanRelease());
                assertEquals(admin, detail.getClasses().get(3).getCanOperate());
                assertEquals(8L, detail.getClasses().get(2).getOwnerId());
                assertEquals("他人教师", detail.getClasses().get(2).getOwnerName());
                assertNull(detail.getClasses().get(3).getOwnerId());
                assertEquals("未认领", detail.getClasses().get(3).getOwnerName());
                assertNull(detail.getClasses().get(3).getClaimTeacherName());
            }
            assertEquals(full ? List.of(101L, 102L) : List.of(101L), detail.getTasks().stream()
                    .map(CourseManageDetailRespVO.CourseTaskRespVO::getHomeworkId).toList());
        }
        verify(adminUserMapper).selectByIds(anyCollection());
        verify(adminUserMapper, never()).selectById(any());
        var ids = full ? List.of(101L, 102L) : List.of(101L);
        verify(courseStatMapper).selectAssigneeCounts(ids);
        verify(courseStatMapper).selectRecordStats(ids);
        verify(eduClassCourseMapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @CsvSource(value = {"7,认领教师,true", "8,他人教师,false", "NULL,负责人,false"}, nullValues = "NULL")
    void publicClassDetailUsesClaimTeacherAndOperationFlag(Long claimant, String teacherName, boolean operate) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(9L); course.setIsPublicCourse(0);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(dataScopeService.isPublicCourse(1L)).thenReturn(true);
        when(dataScopeService.canAssignToPublicClass(1L, 21L)).thenReturn(operate);
        var relation = classCourse(11L, 21L, 0); relation.setClaimTeacherId(claimant);
        when(eduClassCourseMapper.selectById(11L)).thenReturn(relation);
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(user(7L, "认领教师"), user(8L, "他人教师"), user(9L, "负责人")));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getClassDetail(11L);
            assertEquals(teacherName, detail.getTeacherName());
            assertEquals(operate, detail.getCanOperate());
        }
        if (java.util.Objects.equals(claimant, 7L)) verify(dataScopeService, never()).validateClassCourseAccess(21L, 1L);
        else verify(dataScopeService).validateClassCourseAccess(21L, 1L);
        verify(adminUserMapper).selectByIds(anyCollection());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 10, 120})
    void publicDetailReusesLoadedClaimsAndOneUserBatchWithRealScope(int count) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(9L);
        course.setIsPublicCourse(0); course.setStatus(0); course.setPublicAuditStatus(2);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        var roles = mock(cn.nus.zknu.module.system.api.permission.EduRoleApi.class);
        when(roles.isCurrentUserTeacher()).thenReturn(true);
        var realScope = new EduDataScopeService();
        org.springframework.test.util.ReflectionTestUtils.setField(realScope, "capacityCourseApi", capacityCourseLiteMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(realScope, "classCourseMapper", eduClassCourseMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(realScope, "assistantService", assistantService);
        org.springframework.test.util.ReflectionTestUtils.setField(realScope, "eduRoleApi", roles);
        org.springframework.test.util.ReflectionTestUtils.setField(realScope, "securityFrameworkService", securityFrameworkService);
        org.springframework.test.util.ReflectionTestUtils.setField(courseManageService, "dataScopeService", realScope);
        var binding = classCourse(11L, 21L, 0); binding.setClaimTeacherId(7L);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        var tasks = java.util.stream.IntStream.range(0, count).mapToObj(i -> homework(100L + i, 7L)).toList();
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        when(securityFrameworkService.hasPermission(anyString())).thenReturn(true);
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(user(7L, "认领人"), user(9L, "负责人")));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var detail = courseManageService.getCourseDetail(1L);
            assertEquals(count, detail.getTasks().size());
            assertTrue(detail.getTasks().stream().allMatch(task -> task.getCanManage() && task.getCanGrade()));
            assertEquals("认领人", detail.getClasses().get(0).getClaimTeacherName());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
        verify(eduClassCourseMapper).selectList(any(Wrapper.class));
        verifyNoMoreInteractions(eduClassCourseMapper);
        verify(adminUserMapper).selectByIds(java.util.Set.of(7L, 9L));
        verifyNoMoreInteractions(adminUserMapper);
        verify(capacityCourseLiteMapper).getCourse(1L);
        verify(capacityCourseLiteMapper).isCourseAdmin(7L);
        verify(capacityCourseLiteMapper).isCourseMember(1L, 7L);
        verify(capacityCourseLiteMapper).getCourseMemberUserIds(1L);
        verifyNoMoreInteractions(capacityCourseLiteMapper);
    }

    @ParameterizedTest
    @CsvSource(value = {"8,8,团队成员", "7,7,课程负责人", "99,7,课程负责人", "system,7,课程负责人",
            "admin,7,课程负责人", "9223372036854775808,7,课程负责人", "NULL,7,课程负责人"}, nullValues = "NULL")
    void exclusiveClassOwnerUsesCreatorOnlyForCourseTeacherOrMember(String creator, Long ownerId, String ownerName) {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        when(capacityCourseLiteMapper.getCourseMemberUserIds(1L)).thenReturn(List.of(8L));
        var binding = classCourse(11L, 21L, 0); binding.setCreator(creator);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(user(7L, "课程负责人"), user(8L, "团队成员")));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var row = courseManageService.getCourseDetail(1L).getClasses().get(0);
            assertEquals(ownerId, row.getOwnerId());
            assertEquals(ownerName, row.getOwnerName());
        }
        verify(capacityCourseLiteMapper).getCourseMemberUserIds(1L);
        verify(capacityCourseLiteMapper, never()).isCourseMember(anyLong(), anyLong());
        verify(adminUserMapper).selectByIds(java.util.Set.of(7L, 8L));
        verify(adminUserMapper, never()).selectById(any());
    }

    @Test
    void courseShowsAllStatusesAndTaskTeachersWhileDraftStatisticsStayZero() {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        var binding = classCourse(11L, 21L, 0); binding.setSemesterName("本学期");
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        var archived = homework(104L, 7L); archived.setStatus(3);
        var closed = homework(103L, 8L); closed.setStatus(2);
        var published = homework(102L, 9L); published.setStatus(1);
        var draft = homework(101L, 7L); draft.setStatus(0);
        var tasks = List.of(archived, closed, published, draft);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        when(securityFrameworkService.hasPermission(anyString())).thenReturn(true);
        when(adminUserMapper.selectByIds(anyCollection())).thenReturn(List.of(user(7L, "负责人"), user(8L, "已离开团队的教师")));
        var count = new EduCourseStatMapper.HomeworkCount(); count.setHomeworkId(104L); count.setStudentCount(5);
        var stat = new EduCourseStatMapper.HomeworkStat(); stat.setHomeworkId(104L);
        stat.setSubmittedCount(4); stat.setCorrectedCount(3); stat.setScoreTotal(241L); stat.setScoreCount(3);
        when(courseStatMapper.selectAssigneeCounts(List.of(104L, 103L, 102L))).thenReturn(List.of(count));
        when(courseStatMapper.selectRecordStats(List.of(104L, 103L, 102L))).thenReturn(List.of(stat));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var rows = courseManageService.getCourseDetail(1L).getTasks();
            assertEquals(List.of(104L, 103L, 102L, 101L), rows.stream().map(CourseManageDetailRespVO.CourseTaskRespVO::getHomeworkId).toList());
            assertEquals(List.of(3, 2, 1, 0), rows.stream().map(CourseManageDetailRespVO.CourseTaskRespVO::getStatus).toList());
            assertEquals(List.of(7L, 8L, 9L, 7L), rows.stream().map(CourseManageDetailRespVO.CourseTaskRespVO::getTeacherId).toList());
            assertEquals(List.of("负责人", "已离开团队的教师", "-", "负责人"), rows.stream().map(CourseManageDetailRespVO.CourseTaskRespVO::getTeacherName).toList());
            assertTrue(rows.get(0).getCanManage());
            assertFalse(rows.get(0).getCanGrade());
            assertEquals(5, rows.get(0).getStudentCount());
            assertEquals(4, rows.get(0).getSubmittedCount());
            assertEquals(3, rows.get(0).getCorrectedCount());
            assertEquals(80.3D, rows.get(0).getAvgScore());
            assertEquals("本学期", rows.get(0).getSemesterName());
            assertEquals(0, rows.get(3).getStudentCount());
            assertEquals(0, rows.get(3).getSubmittedCount());
            assertEquals(0, rows.get(3).getCorrectedCount());
            assertEquals(0D, rows.get(3).getAvgScore());
        }
        verify(adminUserMapper).selectByIds(java.util.Set.of(7L, 8L, 9L));
        verifyNoMoreInteractions(adminUserMapper);
        verify(courseStatMapper).selectAssigneeCounts(List.of(104L, 103L, 102L));
        verify(courseStatMapper).selectRecordStats(List.of(104L, 103L, 102L));
    }

    @Test
    void courseWithOnlyDraftsSkipsPublishedStatisticsQueries() {
        var course = new CapacityCourseRespDTO(); course.setId(1L); course.setTeacherId(7L);
        when(capacityCourseLiteMapper.getCourse(1L)).thenReturn(course);
        var draft = homework(101L, 7L); draft.setStatus(0);
        when(eduHomeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(draft));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var row = courseManageService.getCourseDetail(1L).getTasks().get(0);
            assertEquals(0, row.getStatus());
            assertEquals(0, row.getStudentCount());
            assertEquals(0, row.getSubmittedCount());
            assertEquals(0, row.getCorrectedCount());
            assertEquals(0D, row.getAvgScore());
        }
        verifyNoInteractions(courseStatMapper);
    }

    private static EduHomeworkDO homework(Long id, Long teacherId) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(id);
        homework.setCourseId(1L);
        homework.setClassId(21L);
        homework.setTeacherId(teacherId);
        return homework;
    }

    private static AdminUserDO user(Long id, String nickname) {
        AdminUserDO user = new AdminUserDO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private static EduClassCourseDO classCourse(Long id, Long classId, Integer status) {
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setId(id);
        relation.setClassId(classId);
        relation.setCourseId(1L);
        relation.setStatus(status);
        return relation;
    }

    private static EduClassDO clazz(Long id, Integer status) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setStatus(status);
        return clazz;
    }

}
