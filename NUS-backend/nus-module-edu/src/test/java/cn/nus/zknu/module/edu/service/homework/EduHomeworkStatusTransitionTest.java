package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_STATUS_DRAFT_FORBIDDEN;
import static org.mockito.Mockito.*;

class EduHomeworkStatusTransitionTest extends BaseMockitoUnitTest {
    @Mock
    private EduHomeworkPublishService publishService;
    @Mock
    private EduHomeworkAiCreateService aiCreateService;
    @Mock
    private EduHomeworkStatisticsService statisticsService;

    @InjectMocks
    private EduHomeworkServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;

    @Test
    void publishedHomeworkCannotBecomeDraft() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setTeacherId(7L);
        homework.setStatus(1);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        EduHomeworkSaveReqVO request = new EduHomeworkSaveReqVO();
        request.setId(1L);
        request.setStatus(0);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> service.updateHomework(request), HOMEWORK_STATUS_DRAFT_FORBIDDEN);
        }
        verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
    }
}
