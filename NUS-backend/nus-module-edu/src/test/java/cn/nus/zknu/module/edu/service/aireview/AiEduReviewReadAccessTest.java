package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduReviewTaskMapper;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiEduReviewReadAccessTest extends BaseMockitoUnitTest {
    @InjectMocks private AiEduReviewServiceImpl service;
    @Mock private EduAiBusinessAccessService accessService;
    @Mock private AiEduReviewTaskMapper reviewTaskMapper;
    @Mock private AiReviewCalibrationService calibrationService;

    @Test
    void statusAndCalibrationUseReadPermissionWithoutRequiringWriteAccess() {
        var task = new AiEduReviewTaskDO(); task.setId(1L); task.setHomeworkId(2L); task.setTotalCount(4);
        task.setSuccessCount(2); task.setFailCount(1);
        var homework = new EduHomeworkDO(); homework.setId(2L);
        var calibration = new AiReviewCalibrationRespVO();
        when(reviewTaskMapper.selectById(1L)).thenReturn(task);
        when(accessService.requireHomeworkViewAccess(2L)).thenReturn(homework);
        when(calibrationService.calculate(7L, homework)).thenReturn(calibration);
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(75, service.getTaskStatus(1L).getProgress());
            assertSame(calibration, service.getCalibration(2L));
        }
        verify(accessService, times(2)).requireHomeworkViewAccess(2L);
        verify(accessService, never()).requireHomeworkAccess(anyLong());
    }
}
