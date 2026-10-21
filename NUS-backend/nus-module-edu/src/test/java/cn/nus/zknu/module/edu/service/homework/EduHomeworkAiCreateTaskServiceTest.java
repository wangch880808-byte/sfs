package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAiCreatePreviewMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EduHomeworkAiCreateTaskServiceTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkAiCreatePreviewDO.class);
    }

    @Mock
    private EduHomeworkAiCreatePreviewMapper previewMapper;
    @Mock
    private Executor taskExecutor;

    @Test
    void repeatedClientRequestReturnsExistingTaskBeforeRegenerating() {
        EduHomeworkAiCreatePreviewDO existing = new EduHomeworkAiCreatePreviewDO();
        existing.setId(88L);
        when(previewMapper.selectByTeacherAndClientRequestId(7L, "same-request"))
                .thenReturn(existing);
        EduHomeworkAiCreateServiceImpl service = new EduHomeworkAiCreateServiceImpl();
        ReflectionTestUtils.setField(service, "eduHomeworkAiCreatePreviewMapper", previewMapper);
        EduHomeworkAiCreatePreviewReqVO request = new EduHomeworkAiCreatePreviewReqVO();
        request.setClientRequestId("same-request");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(88L, service.startAiCreateHomeworkPreviewTask(request));
        }

        verify(previewMapper).selectByTeacherAndClientRequestId(7L, "same-request");
        verifyNoMoreInteractions(previewMapper);
    }

    @Test
    void rejectedSubmissionReturnsClaimedTaskToPending() {
        EduHomeworkAiCreateServiceImpl service = new EduHomeworkAiCreateServiceImpl();
        ReflectionTestUtils.setField(service, "eduHomeworkAiCreatePreviewMapper", previewMapper);
        ReflectionTestUtils.setField(service, "aiTaskExecutor", taskExecutor);
        when(previewMapper.update(any(EduHomeworkAiCreatePreviewDO.class), any(LambdaUpdateWrapper.class)))
                .thenReturn(1);
        doThrow(new TaskRejectedException("full")).when(taskExecutor).execute(any());

        ReflectionTestUtils.invokeMethod(service, "submitAiCreateTask", 99L);

        verify(previewMapper, times(2))
                .update(any(EduHomeworkAiCreatePreviewDO.class), any(LambdaUpdateWrapper.class));
    }
}
