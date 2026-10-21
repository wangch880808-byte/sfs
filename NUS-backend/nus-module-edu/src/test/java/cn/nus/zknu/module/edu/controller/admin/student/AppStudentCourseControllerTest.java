package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.student.StudentCourseService;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppStudentCourseControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppStudentCourseController controller;
    @Mock
    private StudentCourseService studentCourseService;
    @Mock
    private FileApi fileApi;

    @Test
    void downloadCourseResource_streamsRegisteredFileWithAttachmentHeaders() throws Exception {
        FileContentRespDTO file = new FileContentRespDTO();
        file.setName("课程资料.pdf");
        file.setUrl("https://school.example/admin-api/infra/file/1/get/edu/course-resource/5/a.pdf");
        file.setType("application/pdf");
        file.setSize(2048L);
        when(studentCourseService.getCourseResourceFile(10L, 30L)).thenReturn(file);
        when(fileApi.writeFile(eq(file.getUrl()), any())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            controller.downloadCourseResource(30L, response);
        }

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertEquals("2048", response.getHeader("Content-Length"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        verify(fileApi).writeFile(eq(file.getUrl()), any());
        verify(fileApi, never()).getFileContent(any(), any());
    }

    @Test
    void downloadCourseResource_missingStorageObjectReturnsNotFound() throws Exception {
        FileContentRespDTO file = new FileContentRespDTO();
        file.setName("课程资料.pdf");
        file.setUrl("https://school.example/a.pdf");
        when(studentCourseService.getCourseResourceFile(10L, 30L)).thenReturn(file);
        when(fileApi.writeFile(eq(file.getUrl()), any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            controller.downloadCourseResource(30L, response);
        }

        assertEquals(404, response.getStatus());
    }

}
