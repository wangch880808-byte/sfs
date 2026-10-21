package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemoveImpactRespVO;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRemoveService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkRemoveControllerTest extends BaseMockitoUnitTest {
    @InjectMocks private EduHomeworkController controller;
    @Mock private EduHomeworkRemoveService removeService;

    @Test void endpointsReturnImpactAndTrueForBothRemovalForms() {
        var ids = List.of(1L, 2L);
        var impact = new EduHomeworkRemoveImpactRespVO(2, 3, 4);
        when(removeService.getRemoveImpact(ids)).thenReturn(impact);
        assertSame(impact, controller.getRemoveImpact(ids).getData());
        assertTrue(controller.removeHomework(1L).getData());
        assertTrue(controller.removeHomeworkList(ids).getData());
        verify(removeService).getRemoveImpact(ids);
        verify(removeService).removeHomeworks(List.of(1L));
        verify(removeService).removeHomeworks(ids);
    }

    @Test void removalEndpointsRequireExistingPermissionAndIdempotency() throws Exception {
        for (String name : List.of("removeHomework", "removeHomeworkList", "getRemoveImpact")) {
            var method = EduHomeworkController.class.getMethod(name,
                    name.equals("removeHomework") ? Long.class : List.class);
            assertEquals("@ss.hasPermission('edu:homework:delete')", method.getAnnotation(PreAuthorize.class).value());
            if (!name.equals("getRemoveImpact")) assertNotNull(method.getAnnotation(Idempotent.class));
        }
        assertFalse(java.util.Arrays.stream(EduHomeworkCopyController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)));
    }
}
