package cn.nus.zknu.module.capacity.service.course;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseMemberDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMemberMapper;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class CapacityCourseMemberServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CapacityCourseMemberServiceImpl memberService;
    @Mock
    private CapacityCourseMemberMapper memberMapper;
    @Mock
    private CapacityCourseMapper courseMapper;

    @Test
    void replaceMembers_locksCourseBeforeReadingMembersInSameTransaction() throws Exception {
        when(memberMapper.selectListByCourseIdForUpdate(1L)).thenReturn(List.of(member(102L, 1L, 20L)));

        memberService.replaceMembers(1L, List.of(30L));

        var order = inOrder(courseMapper, memberMapper);
        order.verify(courseMapper).selectByIdForUpdate(1L);
        order.verify(memberMapper).selectListByCourseIdForUpdate(1L);
        order.verify(memberMapper).deleteByIds(List.of(102L));
        order.verify(memberMapper).insert(any(CapacityCourseMemberDO.class));
        verify(memberMapper, never()).selectListByCourseId(1L);
        Transactional transactional = CapacityCourseMemberServiceImpl.class
                .getMethod("replaceMembers", Long.class, java.util.Collection.class).getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
    }

    @Test
    void replaceMembers_addsMissingRemovesExtraAndKeepsExisting() {
        when(memberMapper.selectListByCourseIdForUpdate(1L)).thenReturn(List.of(
                member(101L, 1L, 10L), member(102L, 1L, 20L), member(103L, 1L, 10L)));

        memberService.replaceMembers(1L, List.of(10L, 30L, 30L, 0L));

        // 20 不在目标里，103 是 10 的重复行
        verify(memberMapper).deleteByIds(List.of(102L, 103L));
        ArgumentCaptor<CapacityCourseMemberDO> captor = ArgumentCaptor.forClass(CapacityCourseMemberDO.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getCourseId());
        assertEquals(30L, captor.getValue().getUserId());
        assertEquals(CapacityCourseMemberDO.ROLE_TEAM, captor.getValue().getRoleType());
    }

    @Test
    void replaceMembers_emptyTargetRemovesEveryone() {
        when(memberMapper.selectListByCourseIdForUpdate(1L)).thenReturn(List.of(member(101L, 1L, 10L)));

        memberService.replaceMembers(1L, null);

        verify(memberMapper).deleteByIds(List.of(101L));
        verify(memberMapper, never()).insert(any(CapacityCourseMemberDO.class));
    }

    @Test
    void replaceMembers_unchangedTeamWritesNothing() {
        when(memberMapper.selectListByCourseIdForUpdate(1L)).thenReturn(List.of(member(101L, 1L, 10L), member(102L, 1L, 20L)));

        memberService.replaceMembers(1L, List.of(20L, 10L));

        verify(memberMapper, never()).deleteByIds(anyCollection());
        verify(memberMapper, never()).insert(any(CapacityCourseMemberDO.class));
    }

    @Test
    void replaceMembers_concurrentDuplicateInsertIsIgnored() {
        when(memberMapper.selectListByCourseIdForUpdate(1L)).thenReturn(List.of());
        when(memberMapper.insert(any(CapacityCourseMemberDO.class))).thenThrow(new DuplicateKeyException("dup"));

        assertDoesNotThrow(() -> memberService.replaceMembers(1L, List.of(10L)));
    }

    @Test
    void replaceMembers_withoutCourseDoesNothing() {
        memberService.replaceMembers(null, List.of(10L));

        verifyNoInteractions(memberMapper, courseMapper);
    }

    @Test
    void readsMembersInJoinOrderWithoutDuplicates() {
        when(memberMapper.selectListByCourseId(1L)).thenReturn(List.of(
                member(1L, 1L, 30L), member(2L, 1L, 10L), member(3L, 1L, 30L)));
        when(memberMapper.selectListByCourseIds(anyCollection())).thenReturn(List.of(
                member(1L, 1L, 30L), member(2L, 1L, 10L), member(4L, 2L, 40L)));
        when(memberMapper.existsByCourseIdAndUserId(1L, 10L)).thenReturn(true);

        assertEquals(List.of(30L, 10L), memberService.getMemberUserIds(1L));
        assertEquals(Map.of(1L, List.of(30L, 10L), 2L, List.of(40L)),
                memberService.getMemberUserIdsMap(List.of(1L, 2L, 3L)));
        assertTrue(memberService.isMember(1L, 10L));
        assertFalse(memberService.isMember(1L, null));
        assertTrue(memberService.getMemberUserIds(null).isEmpty());
        assertTrue(memberService.getMemberUserIdsMap(List.of()).isEmpty());
    }

    private static CapacityCourseMemberDO member(Long id, Long courseId, Long userId) {
        CapacityCourseMemberDO member = new CapacityCourseMemberDO();
        member.setId(id);
        member.setCourseId(courseId);
        member.setUserId(userId);
        return member;
    }

}
