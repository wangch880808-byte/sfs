package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_REQUIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_TYPE_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_EMPTY;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_TOO_MANY;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_TOTAL_TOO_LARGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class HomeworkAttachmentServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HomeworkAttachmentService attachmentService;
    @Mock
    private FileApi fileApi;
    @Mock
    private ConfigApi configApi;

    private static final long MB = 1024L * 1024;

    @Test
    void invalidJsonIsRejected() {
        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("text", "[]"), 10L, "answer", "http://metadata.local"),
                HOMEWORK_ATTACHMENT_INVALID, "附件地址必须是字符串 JSON 数组");
    }

    @Test
    void requiredAttachmentIsEnforcedByBackend() {
        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[]"),
                HOMEWORK_ATTACHMENT_REQUIRED);
    }

    @Test
    void unregisteredUrlIsRejectedWithoutNetworkAccess() {
        String url = "http://169.254.169.254/latest/meta-data";
        when(fileApi.getFile(url)).thenThrow(new IllegalArgumentException("not platform file"));

        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"" + url + "\"]"),
                HOMEWORK_ATTACHMENT_INVALID, "附件不是已登记的平台文件");
    }

    @Test
    void attachmentOfAnotherUserIsRejected() {
        String url = "https://school.example/admin-api/infra/file/1/get/a.png";
        when(fileApi.getFile(url)).thenReturn(file(url, "20", "image/png", 100L));

        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"" + url + "\"]"),
                HOMEWORK_ATTACHMENT_INVALID, "只能提交本人上传的附件");
    }

    @Test
    void genericDatePathUploadIsRejected() {
        // 通用上传接口生成的日期目录文件不属于作业提交目录，不能作为作业附件
        String url = "https://school.example/admin-api/infra/file/1/get/20260926/a_1.png";
        FileContentRespDTO file = file(url, "10", "image/png", 100L);
        file.setPath("20260926/a_1.png");
        when(fileApi.getFile(url)).thenReturn(file);

        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"" + url + "\"]"),
                HOMEWORK_ATTACHMENT_INVALID, "附件不属于当前任务");
    }

    @Test
    void mismatchedMimeTypeIsRejected() {
        String url = "https://school.example/admin-api/infra/file/1/get/a.mp3";
        when(fileApi.getFile(url)).thenReturn(file(url, "10", "audio/mpeg", 100L));

        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"" + url + "\"]"),
                HOMEWORK_ATTACHMENT_TYPE_INVALID);
    }

    @Test
    void validPlatformFileIsNormalized() {
        String url = "https://school.example/admin-api/infra/file/1/get/a.png";
        when(fileApi.getFile(url)).thenReturn(file(url, "10", "image/png", 100L));

        String result = attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"" + url + "\"]");

        assertEquals("[\"" + url + "\"]", result);
    }

    @Test
    void limitsFallBackToDefaultsWhenNotConfigured() {
        assertEquals(new HomeworkAttachmentLimits(10, 100, 300), attachmentService.getLimits());
    }

    @Test
    void limitsAreReadFromConfig() {
        config("5", "20", "60");
        assertEquals(new HomeworkAttachmentLimits(5, 20, 60), attachmentService.getLimits());
    }

    @Test
    void outOfRangeOrMalformedLimitsFallBackPerKey() {
        // 个数 0 越界、单个 501 MB 超过 multipart 上限、合计不是数字：各自回退默认值
        config("0", "501", "abc");
        assertEquals(new HomeworkAttachmentLimits(10, 100, 300), attachmentService.getLimits());

        config("51", "-1", "5121");
        assertEquals(new HomeworkAttachmentLimits(10, 100, 300), attachmentService.getLimits());

        // 边界值可用
        config("50", "500", "5120");
        assertEquals(new HomeworkAttachmentLimits(50, 500, 5120), attachmentService.getLimits());
        config("1", "1", "1");
        assertEquals(new HomeworkAttachmentLimits(1, 1, 1), attachmentService.getLimits());
    }

    @Test
    void configReadFailureFallsBackToDefaults() {
        when(configApi.getConfigValueByKey(anyString())).thenThrow(new IllegalStateException("db down"));
        assertEquals(new HomeworkAttachmentLimits(10, 100, 300), attachmentService.getLimits());
    }

    @Test
    void uploadUsesConfiguredSingleFileLimit() {
        config("10", "2", "300");
        EduHomeworkDO homework = homework("mixed", null);

        attachmentService.validateUpload(homework, 2 * MB, "image/png");
        assertServiceException(() -> attachmentService.validateUpload(homework, 2 * MB + 1, "image/png"),
                STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE, 2);
        assertServiceException(() -> attachmentService.validateUpload(homework, 0, "image/png"),
                STUDENT_HOMEWORK_ATTACHMENT_EMPTY);
    }

    @Test
    void submitUsesConfiguredCountLimit() {
        config("1", "100", "300");
        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "", "[\"a\",\"b\"]"),
                STUDENT_HOMEWORK_ATTACHMENT_TOO_MANY, 1);
    }

    @Test
    void submitUsesConfiguredTotalLimit() {
        config("10", "100", "3");
        String first = "https://school.example/admin-api/infra/file/1/get/a.png";
        String second = "https://school.example/admin-api/infra/file/1/get/b.png";
        when(fileApi.getFile(first)).thenReturn(file(first, "10", "image/png", 2 * MB));
        when(fileApi.getFile(second)).thenReturn(file(second, "10", "image/png", 2 * MB));

        assertServiceException(() -> attachmentService.validateAndNormalize(
                homework("image", "[\"image\"]"), 10L, "",
                "[\"" + first + "\",\"" + second + "\"]"),
                STUDENT_HOMEWORK_ATTACHMENT_TOTAL_TOO_LARGE, 3);
    }

    @Test
    void allowedTypesFollowTaskConfiguration() {
        assertEquals(java.util.List.of("audio", "image"),
                attachmentService.getAllowedTypes(homework("text", "[\"image\",\"audio\"]")));
        assertEquals(java.util.List.of("audio", "image", "video"),
                attachmentService.getAllowedTypes(homework("mixed", null)));
        assertEquals(java.util.List.of(), attachmentService.getAllowedTypes(homework("text", null)));
        // 任务配置不合法时不阻断详情，提交时再报错
        assertEquals(java.util.List.of(), attachmentService.getAllowedTypes(homework("image", "[\"pdf\"]")));
    }

    private void config(String count, String fileMb, String totalMb) {
        when(configApi.getConfigValueByKey(HomeworkAttachmentService.CONFIG_MAX_COUNT)).thenReturn(count);
        when(configApi.getConfigValueByKey(HomeworkAttachmentService.CONFIG_MAX_FILE_MB)).thenReturn(fileMb);
        when(configApi.getConfigValueByKey(HomeworkAttachmentService.CONFIG_MAX_TOTAL_MB)).thenReturn(totalMb);
    }

    private static EduHomeworkDO homework(String mode, String requiredTypes) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setSubmissionMode(mode);
        homework.setRequiredAttachmentTypesJson(requiredTypes);
        return homework;
    }

    private static FileContentRespDTO file(String url, String creator, String type, long size) {
        FileContentRespDTO file = new FileContentRespDTO();
        file.setUrl(url);
        file.setPath("edu/homework-submission/1/a.png");
        file.setCreator(creator);
        file.setType(type);
        file.setSize(size);
        return file;
    }

}
