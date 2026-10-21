package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_DEADLINE_EXCEEDED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 首次提交以任务截止时间与个人延期中较晚的为准。 */
class EduHomeworkRecordSubmitDeadlineTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkRecordDO.class);
    }

    @InjectMocks
    private EduHomeworkRecordServiceImpl recordService;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock
    private EduHomeworkRecordHistoryService historyService;
    @Mock
    private HomeworkAttachmentService homeworkAttachmentService;
    @Mock
    private EduHomeworkAiReviewService aiReviewService;
    @Mock
    private EduHomeworkExtensionService homeworkExtensionService;

    @BeforeEach
    void activeAssignedHomework() {
        when(archiveAccessService.canSubmit(any())).thenReturn(true);
        when(assigneeMapper.selectByHomeworkIdAndStudentId(100L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
    }

    @Test
    void submitAfterTaskDeadlineWithinPersonalExtensionIsAccepted() {
        when(homeworkMapper.selectById(100L)).thenReturn(homework(LocalDateTime.now().minusHours(1)));
        when(homeworkExtensionService.getPersonalDeadline(100L, 10L)).thenReturn(LocalDateTime.now().plusHours(3));

        recordService.submitRecord(request(), 10L);

        verify(recordMapper).insert(any(EduHomeworkRecordDO.class));
    }

    @Test
    void submitAfterTaskDeadlineWithoutExtensionIsRejected() {
        when(homeworkMapper.selectById(100L)).thenReturn(homework(LocalDateTime.now().minusHours(1)));

        assertServiceException(() -> recordService.submitRecord(request(), 10L), HOMEWORK_RECORD_DEADLINE_EXCEEDED);
        verify(recordMapper, never()).insert(any(EduHomeworkRecordDO.class));
    }

    @Test
    void submitAfterPersonalExtensionIsRejected() {
        when(homeworkMapper.selectById(100L)).thenReturn(homework(LocalDateTime.now().minusDays(2)));
        when(homeworkExtensionService.getPersonalDeadline(100L, 10L)).thenReturn(LocalDateTime.now().minusMinutes(1));

        assertServiceException(() -> recordService.submitRecord(request(), 10L), HOMEWORK_RECORD_DEADLINE_EXCEEDED);
    }

    @Test
    void submitBeforeTaskDeadlineDoesNotLookUpExtension() {
        when(homeworkMapper.selectById(100L)).thenReturn(homework(LocalDateTime.now().plusHours(1)));

        recordService.submitRecord(request(), 10L);

        verifyNoInteractions(homeworkExtensionService);
        verify(recordMapper).insert(any(EduHomeworkRecordDO.class));
    }

    private static EduHomeworkDO homework(LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setStatus(1);
        homework.setDeadline(deadline);
        return homework;
    }

    private static EduHomeworkRecordSubmitReqVO request() {
        EduHomeworkRecordSubmitReqVO request = new EduHomeworkRecordSubmitReqVO();
        request.setHomeworkId(100L);
        request.setContent("answer");
        return request;
    }

}
