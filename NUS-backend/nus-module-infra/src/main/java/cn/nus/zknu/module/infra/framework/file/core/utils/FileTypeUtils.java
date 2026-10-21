package cn.nus.zknu.module.infra.framework.file.core.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.util.http.HttpUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 文件类型 Utils
 *
 * @author hyh
 */
@Slf4j
public class FileTypeUtils {

    private static final Tika TIKA = new Tika();

    /**
     * 获得文件的 mineType，对于 doc，jar 等文件会有误差
     *
     * @param data 文件内容
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    @SneakyThrows
    public static String getMineType(byte[] data) {
        return TIKA.detect(data);
    }

    /**
     * 已知文件名，获取文件类型，在某些情况下比通过字节数组准确，例如使用 jar 文件时，通过名字更为准确
     *
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(String name) {
        return TIKA.detect(name);
    }

    /**
     * 在拥有文件和数据的情况下，最好使用此方法，最为准确
     *
     * @param data 文件内容
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(byte[] data, String name) {
        return TIKA.detect(data, name);
    }

    public static String getMineType(InputStream inputStream, String name) throws IOException {
        return TIKA.detect(inputStream, name);
    }

    /**
     * 根据 mineType 获得文件后缀
     * <p>
     * 注意：如果获取不到，或者发生异常，都返回 null
     *
     * @param mineType 类型
     * @return 后缀，例如说 .pdf
     */
    public static String getExtension(String mineType) {
        try {
            return MimeTypes.getDefaultMimeTypes().forName(mineType).getExtension();
        } catch (MimeTypeException e) {
            log.warn("[getExtension][获取文件后缀({}) 失败]", mineType, e);
            return null;
        }
    }

    /**
     * 返回附件
     *
     * @param response 响应
     * @param filename 文件名
     * @param content  附件内容
     */
    public static void writeAttachment(HttpServletResponse response, String filename, byte[] content) throws IOException {
        // 设置 header 和 contentType
        String mineType = getMineType(content, filename);
        response.setContentType(mineType);
        // 设置内容显示、下载文件名：https://www.cnblogs.com/wq-9/articles/12165056.html
        if (isImage(mineType) && !isXmlType(mineType)) {
            // 参见 https://github.com/YunaiV/ruoyi-vue-pro/issues/692 讨论；SVG 等 XML 类型可携带脚本，仍按附件下载
            response.setHeader("Content-Disposition", "inline;filename=" + HttpUtils.encodeUtf8(filename));
        } else {
            response.setHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename));
        }
        // 针对 video 的特殊处理，解决视频地址在移动端播放的兼容性问题
        if (StrUtil.containsIgnoreCase(mineType, "video")) {
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(content.length));
        }
        // 输出附件
        IoUtil.write(response.getOutputStream(), false, content);
    }

    public static void writeAttachmentHeaders(HttpServletResponse response, String filename,
                                              String mimeType, Long contentLength) {
        String resolvedType = StrUtil.blankToDefault(mimeType, "application/octet-stream");
        response.setContentType(resolvedType);
        response.setHeader("X-Content-Type-Options", "nosniff");
        String disposition = isInlineType(resolvedType) ? "inline" : "attachment";
        response.setHeader("Content-Disposition", disposition + ";filename=" + HttpUtils.encodeUtf8(filename));
        if (contentLength != null && contentLength >= 0) {
            response.setHeader("Content-Length", String.valueOf(contentLength));
        }
        if (StrUtil.containsIgnoreCase(resolvedType, "video")) {
            response.setHeader("Accept-Ranges", "bytes");
        }
    }

    /**
     * 判断是否允许浏览器内联展示：仅图片、音频、视频，且排除 SVG 等 XML 类型，避免同源脚本执行
     *
     * @param mimeType 类型
     * @return 是否内联展示
     */
    static boolean isInlineType(String mimeType) {
        if (isXmlType(mimeType)) {
            return false;
        }
        return isImage(mimeType) || StrUtil.startWith(mimeType, "audio/") || StrUtil.startWith(mimeType, "video/");
    }

    /**
     * 判断是否是 XML 类型，例如 image/svg+xml、application/xhtml+xml
     *
     * @param mimeType 类型
     * @return 是否是 XML 类型
     */
    static boolean isXmlType(String mimeType) {
        String type = StrUtil.subBefore(StrUtil.nullToEmpty(mimeType), ';', false).trim().toLowerCase(Locale.ROOT);
        return type.endsWith("+xml") || type.endsWith("/xml");
    }

    /**
     * 判断是否是图片
     *
     * @param mineType 类型
     * @return 是否是图片
     */
    public static boolean isImage(String mineType) {
        return StrUtil.startWith(mineType, "image/");
    }

}
