package cn.nus.zknu.module.infra.framework.file.core.client.local;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.nus.zknu.module.infra.framework.file.core.client.AbstractFileClient;
import cn.nus.zknu.module.infra.framework.file.core.utils.FilePathUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 本地文件客户端
 *
 * @author hyh
 */
public class LocalFileClient extends AbstractFileClient<LocalFileClientConfig> {

    public LocalFileClient(Long id, LocalFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        FileUtil.mkdir(config.getBasePath());
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 执行写入
        String filePath = getFilePath(path);
        FileUtil.mkParentDirs(filePath);
        FileUtil.writeBytes(content, filePath);
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public String upload(InputStream content, long size, String path, String type) throws Exception {
        String filePath = getFilePath(path);
        FileUtil.mkParentDirs(filePath);
        Files.copy(content, Paths.get(filePath));
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public void delete(String path) {
        String filePath = getFilePath(path);
        FileUtil.del(filePath);
    }

    @Override
    public byte[] getContent(String path) {
        String filePath = getFilePath(path);
        try {
            return FileUtil.readBytes(filePath);
        } catch (IORuntimeException ex) {
            if (ex.getMessage().startsWith("File not exist:")) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public boolean writeContent(String path, OutputStream outputStream) throws Exception {
        Path filePath = Paths.get(getFilePath(path));
        if (!Files.exists(filePath)) {
            return false;
        }
        Files.copy(filePath, outputStream);
        return true;
    }

    private String getFilePath(String path) {
        String normalizedPath = FilePathUtils.normalizeRelativePath(path);
        Path basePath = Paths.get(config.getBasePath()).toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(normalizedPath).normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new IllegalArgumentException("文件路径不能越界");
        }
        return resolvedPath.toString();
    }

}
