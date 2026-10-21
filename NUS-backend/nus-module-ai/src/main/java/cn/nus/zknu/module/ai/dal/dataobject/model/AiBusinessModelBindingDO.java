package cn.nus.zknu.module.ai.dal.dataobject.model;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ai_business_model_binding")
@KeySequence("ai_business_model_binding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiBusinessModelBindingDO extends BaseDO {

    @TableId
    private Long id;
    private String businessCode;
    private Long modelId;

}
