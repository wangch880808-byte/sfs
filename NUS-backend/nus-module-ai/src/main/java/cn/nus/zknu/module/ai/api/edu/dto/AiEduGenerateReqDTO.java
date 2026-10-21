package cn.nus.zknu.module.ai.api.edu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI教育生成请求 DTO
 */
@Data
public class AiEduGenerateReqDTO implements Serializable {

    /**
     * 生成类型：comment=评语, suggestion=建议
     */
    private String type;
    /**
     * 学生数据(JSON)
     */
    private String studentData;

    /** 审计使用的业务对象标识，不发送给模型。 */
    private String businessObjectId;

}
