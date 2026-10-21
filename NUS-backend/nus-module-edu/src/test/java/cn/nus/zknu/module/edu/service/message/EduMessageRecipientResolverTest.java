package cn.nus.zknu.module.edu.service.message;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 留言接收人：课程团队读 capacity_course_member，班课助教读 edu_class_course_assistant。 */
class EduMessageRecipientResolverTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduMessageRecipientResolver resolver;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private CapacityCourseApi capacityCourseApi;
    @Mock
    private EduClassCourseAssistantService assistantService;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduClassCourseDO.class);
    }

    @Test
    void courseTeacherTeamAndClassAssistantsAreAllNotifiedOnce() {
        // 旧列里的值与关联表不同，结果只取关联表
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation(5L, "77", 0)));
        when(capacityCourseApi.getCourseMemberUserIds(2L)).thenReturn(List.of(20L, 21L, 10L));
        when(assistantService.getTeacherIdsMap(List.of(5L))).thenReturn(Map.of(5L, List.of(30L, 31L, 20L)));

        List<Long> recipients = List.copyOf(resolver.resolveTeacherRecipients(1L, 2L, course(10L)));

        assertEquals(List.of(10L, 20L, 21L, 30L, 31L), recipients);
    }

    @Test
    void archivedSemesterAssistantsAreIgnoredWhileAnActiveRelationExists() {
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                relation(5L, null, 1), relation(6L, null, 0), relation(7L, null, null)));
        when(assistantService.getTeacherIdsMap(List.of(6L, 7L))).thenReturn(Map.of(6L, List.of(41L), 7L, List.of(42L)));

        List<Long> recipients = List.copyOf(resolver.resolveTeacherRecipients(1L, 2L, course(10L)));

        assertEquals(List.of(10L, 41L, 42L), recipients);
    }

    @Test
    void fallsBackToArchivedRelationsWhenNothingIsActive() {
        List<EduClassCourseDO> chosen = EduMessageRecipientResolver.chooseRelations(List.of(relation(5L, null, 1)));
        List<Long> recipients = List.copyOf(EduMessageRecipientResolver.collectRecipients(course(10L), List.of(),
                chosen, Map.of(5L, List.of(40L))));

        assertEquals(List.of(10L, 40L), recipients);
    }

    @Test
    void missingCourseTeacherStillNotifiesTeamAndAssistants() {
        List<Long> recipients = List.copyOf(EduMessageRecipientResolver.collectRecipients(course(null), List.of(22L),
                List.of(relation(5L, null, 0), relation(6L, null, 0)), Map.of(6L, List.of(23L, 0L))));

        assertEquals(List.of(22L, 23L), recipients);
    }

    @Test
    void noCourseAndNoRelationsGivesNobody() {
        assertTrue(EduMessageRecipientResolver.collectRecipients(null, null, null, null).isEmpty());
        assertTrue(resolver.resolveTeacherRecipients(null, 2L, null).isEmpty());
        verifyNoInteractions(classCourseMapper, capacityCourseApi, assistantService);
    }


    @Test
    void publicClaimantAndAssistantsReceiveMessagesWithoutOwnerOrTeam() {
        var course = course(10L); course.setIsPublicCourse(0);
        var relation = relation(5L, null, 0); relation.setClaimTeacherId(20L);
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation));
        when(assistantService.getTeacherIdsMap(List.of(5L))).thenReturn(Map.of(5L, List.of(20L, 30L)));
        assertEquals(List.of(20L, 30L), List.copyOf(resolver.resolveTeacherRecipients(1L, 2L, course)));
        verifyNoInteractions(capacityCourseApi);
    }

    @Test
    void unclaimedPublicClassFallsBackToOwnerAndKeepsAssistants() {
        var course = course(10L); course.setIsPublicCourse(0);
        assertEquals(List.of(10L, 30L), List.copyOf(EduMessageRecipientResolver.collectRecipients(course,
                List.of(99L), List.of(relation(5L, null, 0)), Map.of(5L, List.of(30L)))));
    }

    private static CapacityCourseRespDTO course(Long teacherId) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(teacherId);
        return course;
    }

    private static EduClassCourseDO relation(Long id, String legacyCsv, Integer status) {
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setId(id);
        relation.setClassId(1L);
        relation.setCourseId(2L);
        relation.setAssistantTeacherIds(legacyCsv);
        relation.setStatus(status);
        return relation;
    }

}
