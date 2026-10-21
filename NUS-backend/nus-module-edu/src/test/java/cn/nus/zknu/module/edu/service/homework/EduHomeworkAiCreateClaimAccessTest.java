package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** AI 预览、确认、题库生成共享班课校验，任一目标无认领权限都不能继续创建。 */
class EduHomeworkAiCreateClaimAccessTest extends BaseMockitoUnitTest {
    @InjectMocks private EduHomeworkAiCreateServiceImpl service;
    @Mock private CapacityCourseApi capacityCourseApi;
    @Mock private EduAiBusinessAccessService aiBusinessAccessService;
    @Mock private EduDataScopeService dataScopeService;
    @Mock private EduClassCourseMapper eduClassCourseMapper;
    @Mock private EduClassMapper eduClassMapper;

    @ParameterizedTest
    @ValueSource(longs = {3L, 4L})
    void anyUnclaimedTargetRejectsWholeCreation(Long deniedClass) {
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        lenient().when(dataScopeService.canAssignToPublicClass(eq(2L), anyLong()))
                .thenAnswer(invocation -> !deniedClass.equals(invocation.getArgument(1)));
        assertServiceException(() -> ReflectionTestUtils.invokeMethod(service, "validateClassRelations", 2L, List.of(3L, 4L)), FORBIDDEN);
        verifyNoInteractions(eduClassCourseMapper, eduClassMapper);
    }

    @Test
    void claimedClassesStillRequireActiveBindingsAndClasses() {
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(dataScopeService.canAssignToPublicClass(eq(2L), anyLong())).thenReturn(true);
        var one = relation(3L); var two = relation(4L);
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(one, two));
        when(eduClassMapper.selectById(anyLong())).thenAnswer(invocation -> {
            var clazz = new EduClassDO(); clazz.setId(invocation.getArgument(0)); clazz.setStatus(0); return clazz;
        });
        Map<Long, EduClassCourseDO> result = ReflectionTestUtils.invokeMethod(service, "validateClassRelations", 2L, List.of(3L, 4L));
        assertEquals(Map.of(3L, one, 4L, two), result);
        verify(aiBusinessAccessService, never()).validateClassAccess(anyLong());
        verify(dataScopeService).canAssignToPublicClass(2L, 3L);
        verify(dataScopeService).canAssignToPublicClass(2L, 4L);
    }

    @Test
    void privateClassesKeepOriginalClassAccessValidation() {
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(relation(3L)));
        var clazz = new EduClassDO(); clazz.setStatus(0);
        when(eduClassMapper.selectById(3L)).thenReturn(clazz);
        ReflectionTestUtils.invokeMethod(service, "validateClassRelations", 2L, List.of(3L));
        verify(aiBusinessAccessService).validateClassAccess(3L);
        verify(dataScopeService, never()).canAssignToPublicClass(anyLong(), anyLong());
    }

    private EduClassCourseDO relation(Long classId) {
        var relation = new EduClassCourseDO(); relation.setCourseId(2L); relation.setClassId(classId); relation.setStatus(0);
        return relation;
    }
}
