package cn.nus.zknu.module.ai.dal.dataobject.prompt;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词版本。同一业务只追加新版本，不修改历史内容；最多一个版本处于启用状态。
 */
@TableName("ai_prompt_template")
@KeySequence("ai_prompt_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiPromptTemplateDO extends BaseDO {

    @TableId
    private Long id;
    /** 提示词业务码，见 AiPromptCodeEnum */
    private String businessCode;
    /** 版本号，从 1 开始递增 */
    private Integer version;
    private String content;
    /** 是否为当前启用版本 */
    private Boolean active;
    private String remark;

}
