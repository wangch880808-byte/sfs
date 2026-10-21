package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordPageMapper;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkRecordPageAccessTest extends BaseMockitoUnitTest {
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkRecordMapper recordMapper;
    @Mock private EduHomeworkRecordPageMapper pageMapper;
    @Mock private EduClassCourseMapper classCourseMapper;
    @Mock private CapacityCourseApi courses;
    @Mock private EduRoleApi roles;
    @Mock private EduHomeworkRecordHistoryService history;
    private EduHomeworkRecordServiceImpl records;
    private EduAiBusinessAccessService access;

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EduHomeworkRecordDO.class);
        TableInfoHelper.initTableInfo(assistant, EduClassCourseDO.class);
    }

    @BeforeEach
    void setUp() {
        var scope = new EduDataScopeService();
        ReflectionTestUtils.setField(scope, "capacityCourseApi", courses);
        ReflectionTestUtils.setField(scope, "eduRoleApi", roles);
        ReflectionTestUtils.setField(scope, "classCourseMapper", classCourseMapper);
        access = new EduAiBusinessAccessService();
        ReflectionTestUtils.setField(access, "dataScopeService", scope);
        ReflectionTestUtils.setField(access, "homeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(access, "homeworkRecordMapper", recordMapper);
        ReflectionTestUtils.setField(access, "classCourseMapper", classCourseMapper);
        records = new EduHomeworkRecordServiceImpl();
        ReflectionTestUtils.setField(records, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(records, "eduHomeworkRecordMapper", recordMapper);
        ReflectionTestUtils.setField(records, "recordPageMapper", pageMapper);
        ReflectionTestUtils.setField(records, "businessAccessService", access);
        ReflectionTestUtils.setField(records, "historyService", history);
    }

    @ParameterizedTest
    @CsvSource({"owner,0,true", "owner,1,true", "admin,0,true", "admin,1,true",
            "leader,0,true", "leader,1,true", "member,0,true", "member,1,true",
            "approver,0,true", "approver,1,true", "unrelated,0,true", "unrelated,1,true",
            "owner,0,false", "owner,1,false", "admin,0,false", "admin,1,false",
            "leader,0,false", "leader,1,false", "member,0,false", "member,1,false",
            "approver,0,false", "approver,1,false", "unrelated,0,false", "unrelated,1,false"})
    void viewingAndCorrectionKeepSeparatePermissions(String role, int bindingStatus, boolean publicCourse) {
        long userId = switch (role) { case "owner" -> 7L; case "leader" -> 8L; default -> 9L; };
        var homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setTeacherId(7L);
        homework.setCourseId(2L);
        homework.setClassId(3L);
        homework.setStatus(1);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        var record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setHomeworkId(100L);
        record.setStatus(0);
        when(recordMapper.selectById(1L)).thenReturn(record);
        lenient().when(roles.isCurrentUserAdmin()).thenReturn(role.equals("admin"));
        var course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(8L);
        // 在线公共课的无关教师仍不属于任务记录的查看范围。
        course.setIsPublicCourse(publicCourse ? 0 : 1);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        lenient().when(courses.getCourse(2L)).thenReturn(course);
        lenient().when(courses.isCourseMember(2L,userId)).thenReturn(role.equals("member"));
        lenient().when(courses.isCourseAdmin(userId)).thenReturn(role.equals("approver"));
        var binding = new EduClassCourseDO();
        binding.setStatus(bindingStatus);
        lenient().when(classCourseMapper.selectOne(any())).thenReturn(bindingStatus == 0 ? binding : null);
        var req = new EduHomeworkRecordPageReqVO();
        req.setHomeworkId(100L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            if (role.equals("unrelated")
                    || publicCourse && role.equals("member") || !publicCourse && role.equals("approver")) {
                assertServiceException(() -> records.getRecordPage(req), FORBIDDEN);
                assertServiceException(() -> records.getRecord(1L), FORBIDDEN);
                assertServiceException(() -> records.getHistory(1L), FORBIDDEN);
                verifyNoInteractions(history);
                assertServiceException(() -> access.requireRecordViewAccess(1L), FORBIDDEN);
                verify(recordMapper, never()).selectPage(eq(req), any());
            } else {
                when(recordMapper.selectPage(eq(req), any())).thenReturn(new PageResult<>(List.of(record),1L));
                assertEquals(1, records.getRecordPage(req).getTotal());
                assertSame(record, records.getRecord(1L));
                records.getHistory(1L);
                verify(history).listByRecordId(1L);
                assertSame(record, access.requireRecordViewAccess(1L));
            }
            var correct = new EduHomeworkRecordCorrectReqVO();
            correct.setId(1L);
            // 用不可批改状态探测归属校验后的路径，不执行写库。
            if (role.equals("owner")) {
                assertServiceException(() -> records.correctRecord(correct), HOMEWORK_RECORD_CORRECT_STATUS_INVALID);
                assertDoesNotThrow(() -> records.getCorrectPage(req));
            } else {
                assertServiceException(() -> records.correctRecord(correct), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> records.getCorrectPage(req), HOMEWORK_NOT_EXISTS);
            }
            verifyNoInteractions(classCourseMapper);
        }
    }

    @Test
    void unspecifiedHomeworkPagesUseOwnedJoinWithoutLoadingHistoricalIds() {
        var req = new EduHomeworkRecordPageReqVO();
        when(pageMapper.selectOwnedPage(req,7L,false)).thenReturn(PageResult.empty());
        when(pageMapper.selectOwnedPage(req,7L,true)).thenReturn(PageResult.empty());
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            records.getRecordPage(req);
            records.getCorrectPage(req);
            verify(pageMapper).selectOwnedPage(req,7L,false);
            verify(pageMapper).selectOwnedPage(req,7L,true);
        }
        verifyNoInteractions(homeworkMapper,recordMapper,courses,roles);
    }

    @Test
    void noLoginHasEmptyUnspecifiedPagesAndNoDatabaseReads() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            assertTrue(records.getRecordPage(new EduHomeworkRecordPageReqVO()).getList().isEmpty());
            assertTrue(records.getCorrectPage(new EduHomeworkRecordPageReqVO()).getList().isEmpty());
        }
        verifyNoInteractions(pageMapper, homeworkMapper, recordMapper);
    }
}
