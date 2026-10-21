package cn.nus.zknu.module.infra.api.file;

import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件 API 接口
 *
 * @author hyh
 */
public interface FileApi {

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @return 文件路径
     */
    default String createFile(byte[] content) {
        return createFile(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name    文件名称，允许空
     * @return 文件路径
     */
    default String createFile(byte[] content, String name) {
        return createFile(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content   文件内容
     * @param name      文件名称，允许空
     * @param directory 目录，允许空
     * @param type      文件的 MIME 类型，允许空
     * @return 文件路径
     */
    String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content,
                      String name, String directory, String type);

    String createFile(InputStream content, long size, String name, String directory, String type);

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url               完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(@NotEmpty(message = "URL 不能为空") String url,
                         Integer expirationSeconds);

    /**
     * 根据文件访问地址删除文件
     *
     * @param url 完整的文件访问地址
     */
    void deleteFile(@NotEmpty(message = "URL 不能为空") String url);

    /**
     * 读取已登记的平台文件。不会访问任意外部 URL。
     *
     * @param url      文件访问地址
     * @param maxBytes 允许读取的最大字节数
     * @return 文件元数据及内容
     */
    FileContentRespDTO getFileContent(@NotEmpty(message = "URL 不能为空") String url,
                                      @NotNull(message = "最大文件大小不能为空") Long maxBytes);

    /**
     * 获得已登记的平台文件元数据，不读取文件内容。
     */
    FileContentRespDTO getFile(@NotEmpty(message = "URL 不能为空") String url);

    boolean writeFile(@NotEmpty(message = "URL 不能为空") String url, OutputStream outputStream);

}
