package cn.nus.zknu.module.edu.service.questionbank;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.QUESTION_BANK_NOT_EXISTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduQuestionBankServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduQuestionBankServiceImpl questionBankService;
    @Mock
    private EduQuestionBankMapper questionBankMapper;

    @Test
    void delete_ownQuestionIsDeleted() {
        when(questionBankMapper.selectById(1L)).thenReturn(question(7L));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            questionBankService.delete(1L);
        }

        verify(questionBankMapper).deleteById(1L);
    }

    @Test
    void delete_otherTeachersQuestionIsTreatedAsMissing() {
        when(questionBankMapper.selectById(1L)).thenReturn(question(8L));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> questionBankService.delete(1L), QUESTION_BANK_NOT_EXISTS);
        }

        verify(questionBankMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_missingQuestionIsRejected() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> questionBankService.delete(1L), QUESTION_BANK_NOT_EXISTS);
        }

        verify(questionBankMapper, never()).deleteById(any(Long.class));
    }

    private static EduQuestionBankDO question(Long teacherId) {
        EduQuestionBankDO question = new EduQuestionBankDO();
        question.setId(1L);
        question.setTeacherId(teacherId);
        return question;
    }

}
