package cn.nus.zknu.module.system.dal.dataobject.social;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import cn.nus.zknu.framework.mybatis.core.type.PrefixedEncryptTypeHandler;
import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;
import me.zhyd.oauth.config.AuthConfig;

import java.util.Map;

/**
 * 社交客户端 DO
 * <p>
 * 对应 {@link AuthConfig} 配置，满足不同租户，有自己的客户端配置，实现社交（三方）登录
 *
 * @author hyh
 */
@TableName(value = "system_social_client", autoResultMap = true)
@KeySequence("system_social_client_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialClientDO extends BaseDO {

    /**
     * 编号，自增
     */
    @TableId
    private Long id;
    /**
     * 应用名
     */
    private String name;
    /**
     * 社交类型
     * <p>
     * 枚举 {@link SocialTypeEnum}
     */
    private Integer socialType;
    /**
     * 用户类型
     * <p>
     * 目的：不同用户类型，对应不同的小程序，需要自己的配置
     * <p>
     * 枚举 {@link UserTypeEnum}
     */
    private Integer userType;
    /**
     * 状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 客户端 id
     */
    private String clientId;
    /**
     * 客户端 Secret
     */
    @TableField(typeHandler = PrefixedEncryptTypeHandler.class)
    private String clientSecret;

    /**
     * 代理编号
     * <p>
     * 目前只有部分“社交类型”在使用：
     * 1. 预留扩展字段：用于第三方平台的代理编号
     */
    private String agentId;

    /**
     * publicKey 公钥
     * <p>
     * 目前只有部分“社交类型”在使用：
     * 1. 支付宝：支付宝公钥
     */
    private String publicKey;

    /**
     * 平台扩展配置。不同社交平台可按需保存非密钥参数。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> config;

}
