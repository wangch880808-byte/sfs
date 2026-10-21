package cn.nus.zknu.module.system.service.social;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.http.HttpUtils;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO;
import cn.nus.zknu.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialClientDO;
import cn.nus.zknu.module.system.dal.mysql.social.SocialClientMapper;
import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;
import cn.nus.zknu.module.system.framework.justauth.core.AuthRequestFactory;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthAlipayRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.util.json.JsonUtils.toJsonString;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;

/**
 * 社交应用 Service 实现类
 *
 * @author hyh
 */
@Service
@Slf4j
public class SocialClientServiceImpl implements SocialClientService {
    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired(required = false) // 由于 justauth.enable 配置项，可以关闭 AuthRequestFactory 的功能，所以这里只能不强制注入
    private AuthRequestFactory authRequestFactory;

    @Resource
    private SocialClientMapper socialClientMapper;

    @Override
    public boolean isDingtalkEnabled() {
        return isDingtalkClientEnabled(socialClientMapper.selectBySocialTypeAndUserType(
                SocialTypeEnum.DINGTALK.getType(), UserTypeEnum.ADMIN.getValue()));
    }

    private boolean isDingtalkClientEnabled(SocialClientDO client) {
        return client != null && Objects.equals(client.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                && StrUtil.isNotBlank(client.getClientId()) && StrUtil.isNotBlank(client.getClientSecret());
    }

    @Override
    public String getAuthorizeUrl(Integer socialType, Integer userType, String redirectUri) {
        // 获得对应的 AuthRequest 实现
        AuthRequest authRequest = buildAuthRequest(socialType, userType);
        // 生成跳转地址
        String authorizeUri = authRequest.authorize(AuthStateUtils.createState());
        return HttpUtils.replaceUrlQuery(authorizeUri, "redirect_uri", redirectUri);
    }

    @Override
    public AuthUser getAuthUser(Integer socialType, Integer userType, String code, String state) {
        // 构建请求
        AuthRequest authRequest = buildAuthRequest(socialType, userType);
        AuthCallback authCallback = AuthCallback.builder().code(code).auth_code(code).state(state).build();
        // 执行请求
        AuthResponse<?> authResponse = authRequest.login(authCallback);
        log.info("[getAuthUser][请求社交平台 type({}) request({}) response({})]", socialType,
                toJsonString(authCallback), toJsonString(authResponse));
        if (!authResponse.ok()) {
            throw exception(SOCIAL_USER_AUTH_FAILURE, authResponse.getMsg());
        }
        return (AuthUser) authResponse.getData();
    }

    /**
     * 构建 AuthRequest 对象，支持多租户配置
     *
     * @param socialType 社交类型
     * @param userType   用户类型
     * @return AuthRequest 对象
     */
    @VisibleForTesting
    AuthRequest buildAuthRequest(Integer socialType, Integer userType) {
        if (Objects.equals(socialType, SocialTypeEnum.WECHAT_MINI_PROGRAM.getType())) {
            throw exception(SOCIAL_CLIENT_AUTH_FLOW_UNSUPPORTED);
        }
        if (Objects.equals(socialType, SocialTypeEnum.DINGTALK.getType())
                && Objects.equals(userType, UserTypeEnum.ADMIN.getValue())) {
            SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(socialType, userType);
            if (!isDingtalkClientEnabled(client) || authRequestFactory == null) {
                throw exception(SOCIAL_CLIENT_DINGTALK_DISABLED);
            }
            AuthConfig config = new AuthConfig();
            config.setClientId(client.getClientId());
            config.setClientSecret(client.getClientSecret());
            config.setIgnoreCheckRedirectUri(true);
            return authRequestFactory.createDingtalkRequest(config);
        }
        // 1. 先查找默认的配置项，从 application-*.yaml 中读取
        AuthRequest request = authRequestFactory.get(SocialTypeEnum.valueOfType(socialType).getSource());
        Assert.notNull(request, String.format("社交平台(%d) 不存在", socialType));
        // 2. 查询 DB 的配置项，如果存在则进行覆盖
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(socialType, userType);
        if (client != null && Objects.equals(client.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            // 2.1 构造新的 AuthConfig 对象
            AuthConfig authConfig = (AuthConfig) ReflectUtil.getFieldValue(request, "config");
            AuthConfig newAuthConfig = ReflectUtil.newInstance(authConfig.getClass());
            BeanUtil.copyProperties(authConfig, newAuthConfig);
            // 2.2 修改对应的 clientId + clientSecret 密钥
            newAuthConfig.setClientId(client.getClientId());
            newAuthConfig.setClientSecret(client.getClientSecret());
            if (client.getAgentId() != null) { // 如果有 agentId 则修改 agentId
                newAuthConfig.setAgentId(client.getAgentId());
            }
            // 2.3 设置会 request 里，进行后续使用
            if (SocialTypeEnum.ALIPAY_MINI_PROGRAM.getType().equals(socialType)) {
                // 特殊：如果是支付宝的小程序，多了 publicKey 属性，可见 AuthConfig 里的 alipayPublicKey 字段说明
                return new AuthAlipayRequest(newAuthConfig, client.getPublicKey());
            }
            ReflectUtil.setFieldValue(request, "config", newAuthConfig);
        }
        return request;
    }

    // =================== 客户端管理 ===================

    @Override
    public Long createSocialClient(SocialClientSaveReqVO createReqVO) {
        // 校验重复
        validateSocialClientUnique(null, createReqVO.getUserType(), createReqVO.getSocialType());
        if (StrUtil.isBlank(createReqVO.getClientSecret())) {
            throw exception(SOCIAL_CLIENT_SECRET_REQUIRED);
        }

        // 插入
        SocialClientDO client = BeanUtils.toBean(createReqVO, SocialClientDO.class);
        socialClientMapper.insert(client);
        return client.getId();
    }

    @Override
    public void updateSocialClient(SocialClientSaveReqVO updateReqVO) {
        // 校验存在
        SocialClientDO existing = validateSocialClientExists(updateReqVO.getId());
        // 校验重复
        validateSocialClientUnique(updateReqVO.getId(), updateReqVO.getUserType(), updateReqVO.getSocialType());

        // 更新
        SocialClientDO updateObj = BeanUtils.toBean(updateReqVO, SocialClientDO.class);
        if (StrUtil.isBlank(updateReqVO.getClientSecret())) {
            updateObj.setClientSecret(existing.getClientSecret());
        }
        socialClientMapper.updateById(updateObj);
    }

    @Override
    public void deleteSocialClient(Long id) {
        // 校验存在
        validateSocialClientExists(id);
        // 删除
        socialClientMapper.deleteById(id);
    }

    @Override
    public void deleteSocialClientList(List<Long> ids) {
        socialClientMapper.deleteByIds(ids);
    }

    private SocialClientDO validateSocialClientExists(Long id) {
        SocialClientDO client = socialClientMapper.selectById(id);
        if (client == null) {
            throw exception(SOCIAL_CLIENT_NOT_EXISTS);
        }
        return client;
    }

    /**
     * 校验社交应用是否重复，需要保证 userType + socialType 唯一
     * 原因是，不同端（userType）选择某个社交登录（socialType）时，需要通过 {@link #buildAuthRequest(Integer, Integer)} 构建对应的请求
     *
     * @param id         编号
     * @param userType   用户类型
     * @param socialType 社交类型
     */
    private void validateSocialClientUnique(Long id, Integer userType, Integer socialType) {
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(
                socialType, userType);
        if (client == null) {
            return;
        }
        if (id == null // 新增时，说明重复
                || ObjUtil.notEqual(id, client.getId())) { // 更新时，如果 id 不一致，说明重复
            throw exception(SOCIAL_CLIENT_UNIQUE);
        }
    }

    @Override
    public SocialClientDO getSocialClient(Long id) {
        return socialClientMapper.selectById(id);
    }

    @Override
    public PageResult<SocialClientDO> getSocialClientPage(SocialClientPageReqVO pageReqVO) {
        return socialClientMapper.selectPage(pageReqVO);
    }

    @Override
    public SocialClientDO getSocialClient(Integer socialType, Integer userType) {
        return socialClientMapper.selectBySocialTypeAndUserType(socialType, userType);
    }

}
