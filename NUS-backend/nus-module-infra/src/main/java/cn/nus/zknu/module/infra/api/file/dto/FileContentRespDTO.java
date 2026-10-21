package cn.nus.zknu.module.infra.api.file.dto;

import lombok.Data;

/**
 * 平台文件内容 DTO，仅供模块内部调用。
 */
@Data
public class FileContentRespDTO {

    private Long id;
    private String name;
    private String url;
    private String path;
    private String type;
    private Long size;
    private String creator;
    private byte[] content;

}
