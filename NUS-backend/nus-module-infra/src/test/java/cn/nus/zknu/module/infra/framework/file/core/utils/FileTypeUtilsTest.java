package cn.nus.zknu.module.infra.framework.file.core.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypeUtilsTest {

    @Test
    void writeAttachmentHeaders_svgIsDownloadedAsAttachment() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        FileTypeUtils.writeAttachmentHeaders(response, "logo.svg", "image/svg+xml", 128L);

        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment;");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Length")).isEqualTo("128");
    }

    @Test
    void writeAttachmentHeaders_anyXmlBasedTypeIsDownloadedAsAttachment() {
        for (String type : new String[]{"IMAGE/SVG+XML", "image/svg+xml; charset=utf-8",
                "application/xhtml+xml", "text/xml"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            FileTypeUtils.writeAttachmentHeaders(response, "a.bin", type, null);

            assertThat(response.getHeader("Content-Disposition")).as(type).startsWith("attachment;");
        }
    }

    @Test
    void writeAttachmentHeaders_rasterImageAudioAndVideoStayInline() {
        for (String type : new String[]{"image/png", "audio/mpeg", "video/mp4"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            FileTypeUtils.writeAttachmentHeaders(response, "a.bin", type, 1L);

            assertThat(response.getHeader("Content-Disposition")).as(type).startsWith("inline;");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }
    }

    @Test
    void writeAttachment_svgContentIsDownloadedAsAttachment() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        FileTypeUtils.writeAttachment(response, "logo.svg", svg);

        assertThat(response.getContentType()).contains("svg");
        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment;");
    }

    @Test
    void anonymousAppFileControllerNoLongerExists() {
        assertThatThrownBy(() -> Class.forName("cn.nus.zknu.module.infra.controller.app.file.AppFileController"))
                .isInstanceOf(ClassNotFoundException.class);
    }

}
