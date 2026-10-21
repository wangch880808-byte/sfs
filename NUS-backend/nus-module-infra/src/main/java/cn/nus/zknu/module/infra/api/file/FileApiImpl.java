package cn.nus.zknu.module.infra.api.file;

import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.infra.dal.dataobject.file.FileDO;
import cn.nus.zknu.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件 API 实现类
 *
 * @author hyh
 */
@Service
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public String createFile(byte[] content, String name, String directory, String type) {
        return fileService.createFile(content, name, directory, type);
    }

    @Override
    public String createFile(InputStream content, long size, String name, String directory, String type) {
        return fileService.createFile(content, size, name, directory, type);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public void deleteFile(String url) {
        fileService.deleteFileByUrl(url);
    }

    @Override
    @SneakyThrows
    public FileContentRespDTO getFileContent(String url, Long maxBytes) {
        FileDO file = fileService.getFileByUrl(url);
        if (file.getSize() != null && file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过允许大小");
        }
        byte[] content = fileService.getFileContent(file.getConfigId(), file.getPath());
        if (content == null) {
            throw new IllegalArgumentException("平台文件不存在");
        }
        if (content.length > maxBytes) {
            throw new IllegalArgumentException("文件超过允许大小");
        }
        FileContentRespDTO result = toDTO(file);
        result.setSize((long) content.length);
        result.setContent(content);
        return result;
    }

    @Override
    public FileContentRespDTO getFile(String url) {
        return toDTO(fileService.getFileByUrl(url));
    }

    @Override
    @SneakyThrows
    public boolean writeFile(String url, OutputStream outputStream) {
        FileDO file = fileService.getFileByUrl(url);
        return fileService.writeFileContent(file.getConfigId(), file.getPath(), outputStream);
    }

    private FileContentRespDTO toDTO(FileDO file) {
        FileContentRespDTO result = new FileContentRespDTO();
        result.setId(file.getId());
        result.setName(file.getName());
        result.setUrl(file.getUrl());
        result.setType(file.getType());
        result.setSize(file.getSize());
        result.setCreator(file.getCreator());
        result.setPath(file.getPath());
        return result;
    }

}
