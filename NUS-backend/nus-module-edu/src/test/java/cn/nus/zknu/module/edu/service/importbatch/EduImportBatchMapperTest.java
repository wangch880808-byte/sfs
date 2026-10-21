package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 校验实际 Mapper 条件，防止状态更新绕过超时/取消与成功提交的互斥。 */
class EduImportBatchMapperTest {

    private EduImportBatchMapper mapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EduImportBatchDO.class);
        mapper = mock(EduImportBatchMapper.class, CALLS_REAL_METHODS);
    }

    @Test
    void successCanOnlyReplaceConfirmingAndWritesAllCounters() {
        var batch = new EduImportBatchDO().setId(1L).setConfirmTime(LocalDateTime.now())
                .setInsertedCount(3).setUpdatedCount(2).setSkippedCount(1);
        mapper.completeConfirmation(batch);
        var wrapper = updatedCondition();
        assertThat(wrapper.getSqlSegment()).contains("id =", "status =");
        assertThat(wrapper.getSqlSet()).contains("status=", "confirm_time=", "inserted_count=", "updated_count=", "skipped_count=");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("CONFIRMING", "SUCCESS", 1L, 3, 2, 1, batch.getConfirmTime());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timeoutAlwaysGuardsStateAndStartTimeForSingleAndStartupScan(boolean single) {
        var cutoff = LocalDateTime.now().minusMinutes(30);
        mapper.failTimedOutConfirmations(single ? 1L : null, cutoff, EduImportBatchServiceImpl.TIMEOUT_REASON);
        var wrapper = updatedCondition();
        assertThat(wrapper.getSqlSegment()).contains("status =", "confirm_time <");
        assertThat(wrapper.getSqlSegment().contains("id =")).isEqualTo(single);
        assertThat(wrapper.getParamNameValuePairs().values()).contains("CONFIRMING", "FAILED", cutoff,
                EduImportBatchServiceImpl.TIMEOUT_REASON);
    }

    @Test
    void rejectionResetClearsStartTimeAndOnlyReplacesConfirming() {
        mapper.resetConfirmation(1L);
        var wrapper = updatedCondition();
        assertThat(wrapper.getSqlSegment()).contains("id =", "status =");
        assertThat(wrapper.getSqlSet()).contains("confirm_time=");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("CONFIRMING", "PREVIEW_READY", null);
    }

    @Test
    void cancelCannotReplaceConfirmingSuccessOrFailed() {
        mapper.cancelPreview(1L);
        var wrapper = updatedCondition();
        assertThat(wrapper.getSqlSegment()).contains("id =", "status IN");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("PREVIEW_READY", "PREVIEW_INVALID", "CANCELLED")
                .doesNotContain("CONFIRMING", "SUCCESS", "FAILED");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaUpdateWrapper<EduImportBatchDO> updatedCondition() {
        ArgumentCaptor<LambdaUpdateWrapper<EduImportBatchDO>> condition = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(any(EduImportBatchDO.class), condition.capture());
        return condition.getValue();
    }

}
