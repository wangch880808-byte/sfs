package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchItemMapper;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchMapper;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportBatchStatusEnum;
import cn.nus.zknu.module.infra.api.file.FileApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_FILE_TYPE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduImportBatchServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private EduImportHandler handler;
    @Mock
    private EduImportBatchMapper batchMapper;
    @Mock
    private EduImportBatchItemMapper itemMapper;
    @Mock
    private FileApi fileApi;

    private EduImportBatchServiceImpl service;

    @BeforeEach
    void setUp() {
        when(handler.getBizType()).thenReturn("TEST");
        when(handler.getTemplateVersion()).thenReturn("1.0");
        service = new EduImportBatchServiceImpl(List.of(handler));
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
    }

    @Test
    void preview_rejectsNonXlsxFile() {
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "a,b".getBytes());

        assertServiceException(() -> service.preview("TEST", "1.0", null, file),
                IMPORT_FILE_TYPE_INVALID);

        verifyNoInteractions(fileApi, batchMapper, itemMapper);
    }

    @Test
    void preview_persistsNormalizedRowsAndPlannedCounts() {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 1, 2});
        EduImportPreviewRow row = EduImportPreviewRow.builder()
                .sheetName("数据")
                .rowNumber(2)
                .businessKey("KEY-1")
                .normalizedData("{\"code\":\"KEY-1\"}")
                .action(EduImportRowActionEnum.INSERT)
                .errors(Collections.emptyList())
                .build();
        when(handler.preview(any(), isNull())).thenReturn(List.of(row));
        when(fileApi.createFile(any(), eq("data.xlsx"), eq("edu/import/test"), any()))
                .thenReturn("https://files/import/data.xlsx");
        doAnswer(invocation -> {
            invocation.<EduImportBatchDO>getArgument(0).setId(1L);
            return 1;
        }).when(batchMapper).insert(any(EduImportBatchDO.class));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            EduImportBatchRespVO result = service.preview("TEST", "1.0", null, file);

            assertEquals("PREVIEW_READY", result.getStatus());
            assertEquals(1, result.getTotalCount());
            assertEquals(1, result.getInsertedCount());
            assertEquals(0, result.getInvalidCount());
        }
        verify(handler).validatePermission();
        verify(itemMapper).insertBatch(argThat(items -> items.size() == 1
                && "KEY-1".equals(items.iterator().next().getBusinessKey())));
    }

    @Test
    void preview_countsSkippedRowsByReason() {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 1, 2});
        List<EduImportPreviewRow> rows = List.of(
                skipRow(2, "{\"skipReason\":\"UNCHANGED\"}", Collections.emptyList()),
                skipRow(3, "{\"skipReason\":\"UNCHANGED\"}", Collections.emptyList()),
                skipRow(4, "{\"skipReason\":\"EMPTY\"}", Collections.emptyList()),
                // 有错误的行算进错误数，不算进跳过原因
                skipRow(5, "{\"skipReason\":\"EMPTY\"}", List.of("成绩不能为空")),
                EduImportPreviewRow.builder().sheetName("数据").rowNumber(6).normalizedData("{\"score\":70}")
                        .action(EduImportRowActionEnum.UPDATE).errors(Collections.emptyList()).build());
        when(handler.preview(any(), isNull())).thenReturn(rows);
        when(fileApi.createFile(any(), any(), any(), any())).thenReturn("https://files/import/data.xlsx");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            EduImportBatchRespVO result = service.preview("TEST", "1.0", null, file);

            assertEquals(java.util.Map.of("UNCHANGED", 2, "EMPTY", 1), result.getSkipReasonCounts());
            assertEquals(1, result.getUpdatedCount());
            assertEquals(3, result.getSkippedCount());
            assertEquals(1, result.getInvalidCount());
        }
    }

    @Test
    void countSkipReasons_isNullWhenHandlerHasNoReasons() {
        assertEquals(null, EduImportBatchServiceImpl.countSkipReasons(
                java.util.stream.Stream.of("{\"code\":\"A\"}", null, "not json", "{\"skipReason\":\" \"}")));
    }

    private static EduImportPreviewRow skipRow(int rowNumber, String normalizedData, List<String> errors) {
        return EduImportPreviewRow.builder().sheetName("数据").rowNumber(rowNumber).normalizedData(normalizedData)
                .action(EduImportRowActionEnum.SKIP).errors(new java.util.ArrayList<>(errors)).build();
    }

    @Test
    void preview_returnsPasswordPolicyForConfirmation() {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 1, 2});
        EduImportPreviewRow row = EduImportPreviewRow.builder()
                .sheetName("数据")
                .rowNumber(2)
                .businessKey("KEY-1")
                .normalizedData("{\"code\":\"KEY-1\"}")
                .action(EduImportRowActionEnum.INSERT)
                .errors(Collections.emptyList())
                .build();
        when(handler.preview(any(), isNull())).thenReturn(List.of(row));
        when(handler.getInitialPasswordMode()).thenReturn(EduImportHandler.INITIAL_PASSWORD_MODE_FIXED);
        when(handler.getDefaultInitialPassword()).thenReturn("admin123");
        when(fileApi.createFile(any(), eq("data.xlsx"), eq("edu/import/test"), any()))
                .thenReturn("https://files/import/data.xlsx");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            EduImportBatchRespVO result = service.preview("TEST", "1.0", null, file);

            assertEquals(EduImportHandler.INITIAL_PASSWORD_MODE_FIXED, result.getInitialPasswordMode());
            assertEquals("admin123", result.getDefaultInitialPassword());
        }
    }

    @Test
    void cancel_marksPreviewBatchCancelled() {
        EduImportBatchDO batch = new EduImportBatchDO();
        batch.setId(1L);
        batch.setBatchNo("IMP-1");
        batch.setOperatorId(7L);
        batch.setBizType("TEST");
        batch.setTemplateVersion("1.0");
        batch.setStatus(EduImportBatchStatusEnum.PREVIEW_READY.getStatus());
        when(batchMapper.selectByBatchNo("IMP-1")).thenReturn(batch);
        when(batchMapper.cancelPreview(1L)).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            EduImportBatchRespVO result = service.cancel("IMP-1");
            assertEquals(EduImportBatchStatusEnum.CANCELLED.getStatus(), result.getStatus());
        }
        assertEquals(EduImportBatchStatusEnum.CANCELLED.getStatus(), batch.getStatus());
        verify(batchMapper).cancelPreview(1L);
    }

    @Test
    void getErrorFileDownloadUrl_returnsPresignedUrlWhenSupported() {
        EduImportBatchDO batch = errorBatch();
        when(batchMapper.selectByBatchNo("IMP-1")).thenReturn(batch);
        when(fileApi.presignGetUrl(batch.getErrorFileUrl(), 300)).thenReturn("https://files/signed.xlsx");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals("https://files/signed.xlsx", service.getErrorFileDownloadUrl("IMP-1"));
        }
    }

    @Test
    void getErrorFileDownloadUrl_fallsBackForLocalStorage() {
        EduImportBatchDO batch = errorBatch();
        when(batchMapper.selectByBatchNo("IMP-1")).thenReturn(batch);
        when(fileApi.presignGetUrl(batch.getErrorFileUrl(), 300))
                .thenThrow(new UnsupportedOperationException("not supported"));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(batch.getErrorFileUrl(), service.getErrorFileDownloadUrl("IMP-1"));
        }
    }

    private EduImportBatchDO errorBatch() {
        EduImportBatchDO batch = new EduImportBatchDO();
        batch.setId(1L);
        batch.setBatchNo("IMP-1");
        batch.setOperatorId(7L);
        batch.setBizType("TEST");
        batch.setTemplateVersion("1.0");
        batch.setErrorFileUrl("http://localhost/files/error.xlsx");
        return batch;
    }

}
