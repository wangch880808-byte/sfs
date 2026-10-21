package cn.nus.zknu.module.system.enums.social;

import cn.hutool.core.util.ArrayUtil;
import cn.nus.zknu.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 社交平台的类型枚举
 *
 * @author hyh
 */
@Getter
@AllArgsConstructor
public enum SocialTypeEnum implements ArrayValuable<Integer> {

    /**
     * Gitee
     *
     * @see <a href="https://gitee.com/api/v5/oauth_doc#/">接入文档</a>
     */
    GITEE(10, "GITEE"),
    /**
     * 钉钉
     *
     * @see <a href="https://developers.dingtalk.com/document/app/obtain-identity-credentials">接入文档</a>
     */
    DINGTALK(20, "DINGTALK"),
    /**
     * 支付宝小程序
     *
     * @see <a href="https://opendocs.alipay.com/mini/05dxgc?pathHash=1a3ecb13">接入文档</a>
     */
    ALIPAY_MINI_PROGRAM(40, "ALIPAY"),
    /**
     * 微信小程序。该类型通过微信 code2Session 接入，不经过 JustAuth。
     */
    WECHAT_MINI_PROGRAM(50, "WECHAT_MINI_PROGRAM"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(SocialTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 类型的标识
     */
    private final String source;

    public static SocialTypeEnum valueOfType(Integer type) {
        return ArrayUtil.firstMatch(o -> o.getType().equals(type), values());
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
