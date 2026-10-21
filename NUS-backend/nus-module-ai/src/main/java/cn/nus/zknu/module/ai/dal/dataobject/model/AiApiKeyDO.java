package cn.nus.zknu.module.ai.dal.dataobject.model;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import cn.nus.zknu.framework.mybatis.core.type.PrefixedEncryptTypeHandler;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI API 秘钥 DO
 *
 * @author hyh
 */
@TableName(value = "ai_api_key", autoResultMap = true)
@KeySequence("ai_api_key_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiApiKeyDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 密钥
     */
    @TableField(typeHandler = PrefixedEncryptTypeHandler.class)
    private String apiKey;
    /**
     * 平台
     * <p>
     * 枚举 {@link AiPlatformEnum}
     */
    private String platform;
    /**
     * API 地址
     */
    private String url;
    /**
     * 状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}
