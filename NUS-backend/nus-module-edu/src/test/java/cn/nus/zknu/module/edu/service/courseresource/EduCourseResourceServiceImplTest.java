package cn.nus.zknu.module.edu.service.courseresource;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.COURSE_RESOURCE_FILE_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.COURSE_RESOURCE_FILE_SIZE_EXCEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EduCourseResourceServiceImplTest extends BaseMockitoUnitTest {

    private static final String FILE_URL = "https://school.example/admin-api/infra/file/1/get/edu/course-resource/5/a.docx";

    @InjectMocks
    private EduCourseResourceServiceImpl resourceService;
    @Mock
    private EduCourseResourceMapper resourceMapper;
    @Mock
    private FileApi fileApi;

    @Test
    void createResource_unregisteredFileIsRejected() {
        when(fileApi.getFile(FILE_URL)).thenThrow(new IllegalArgumentException("not registered"));

        assertCreateRejected(createRequest(), "文件不是已登记的平台文件");
    }

    @Test
    void createResource_fileUploadedByAnotherUserIsRejected() {
        when(fileApi.getFile(FILE_URL)).thenReturn(file("8", "edu/course-resource/5/a.docx", 10L));

        assertCreateRejected(createRequest(), "只能使用本人上传的文件");
    }

    @Test
    void createResource_fileOutsideCourseDirectoryIsRejected() {
        when(fileApi.getFile(FILE_URL)).thenReturn(file("7", "edu/course-resource/6/a.docx", 10L));

        assertCreateRejected(createRequest(), "文件不属于当前课程的资源目录");
    }

    @Test
    void createResource_usesPlatformFileSize() {
        when(fileApi.getFile(FILE_URL)).thenReturn(file("7", "edu/course-resource/5/20260926/a_1.docx", 2048L));
        EduCourseResourceSaveReqVO request = createRequest();
        request.setFileSize(1L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            resourceService.createResource(request);
        }

        ArgumentCaptor<EduCourseResourceDO> captor = ArgumentCaptor.forClass(EduCourseResourceDO.class);
        verify(resourceMapper).insert(captor.capture());
        assertEquals(2048L, captor.getValue().getFileSize());
        assertEquals("word", captor.getValue().getFileType());
    }

    @Test
    void createResource_platformFileSizeAboveLimitIsRejected() {
        when(fileApi.getFile(FILE_URL)).thenReturn(file("7", "edu/course-resource/5/a.docx", 501L * 1024 * 1024));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> resourceService.createResource(createRequest()),
                    COURSE_RESOURCE_FILE_SIZE_EXCEED);
        }

        verify(resourceMapper, never()).insert(any(EduCourseResourceDO.class));
    }

    @Test
    void getResourcePreviewText_readsRegisteredPlatformFileWithLimit() throws Exception {
        when(resourceMapper.selectById(1L)).thenReturn(resource(FILE_URL));
        FileContentRespDTO content = file("7", "edu/course-resource/5/a.docx", 10L);
        content.setContent(docx("第一章 课程导论"));
        when(fileApi.getFileContent(FILE_URL, EduCourseResourceServiceImpl.PREVIEW_MAX_FILE_SIZE)).thenReturn(content);

        assertEquals("第一章 课程导论", resourceService.getResourcePreviewText(1L));
    }

    @Test
    void getResourcePreviewText_unregisteredUrlReturnsEmptyWithoutNetworkAccess() {
        String url = "http://169.254.169.254/latest/meta-data.docx";
        when(resourceMapper.selectById(1L)).thenReturn(resource(url));
        when(fileApi.getFileContent(eq(url), anyLong())).thenThrow(new IllegalArgumentException("not registered"));

        assertEquals("", resourceService.getResourcePreviewText(1L));
    }

    private void assertCreateRejected(EduCourseResourceSaveReqVO request, String reason) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> resourceService.createResource(request), COURSE_RESOURCE_FILE_INVALID, reason);
        }
        verify(resourceMapper, never()).insert(any(EduCourseResourceDO.class));
    }

    private static EduCourseResourceSaveReqVO createRequest() {
        EduCourseResourceSaveReqVO request = new EduCourseResourceSaveReqVO();
        request.setCourseId(5L);
        request.setResourceName("第一章课件");
        request.setFileUrl(FILE_URL);
        request.setFileType("word");
        request.setFileSize(10L);
        return request;
    }

    private static EduCourseResourceDO resource(String url) {
        EduCourseResourceDO resource = new EduCourseResourceDO();
        resource.setId(1L);
        resource.setCourseId(5L);
        resource.setFileType("word");
        resource.setFileUrl(url);
        return resource;
    }

    private static FileContentRespDTO file(String creator, String path, Long size) {
        FileContentRespDTO file = new FileContentRespDTO();
        file.setUrl(FILE_URL);
        file.setCreator(creator);
        file.setPath(path);
        file.setSize(size);
        return file;
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

}
