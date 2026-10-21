package cn.nus.zknu.module.system.api.social;

import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;

/**
 * 社交应用的 API 接口
 *
 * @author hyh
 */
public interface SocialClientApi {

    /**
     * 获得社交平台的授权 URL
     *
     * @param socialType  社交平台的类型 {@link SocialTypeEnum}
     * @param userType    用户类型
     * @param redirectUri 重定向 URL
     * @return 社交平台的授权 URL
     */
    String getAuthorizeUrl(Integer socialType, Integer userType, String redirectUri);

}
