package cn.nus.zknu.module.system.controller.admin.auth;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.ratelimiter.core.annotation.RateLimiter;
import cn.nus.zknu.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.nus.zknu.framework.ratelimiter.core.keyresolver.impl.ExpressionRateLimiterKeyResolver;
import cn.nus.zknu.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver;
import cn.nus.zknu.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.nus.zknu.module.system.controller.admin.auth.vo.WechatMiniProgramAuthVO.*;
import cn.nus.zknu.module.system.service.wechat.WechatMiniProgramAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 微信小程序认证")
@RestController
@RequestMapping("/system/wechat-mini-program")
@Validated
public class WechatMiniProgramAuthController {

    @Resource
    private WechatMiniProgramAuthService authService;

    @GetMapping("/config")
    @PermitAll
    @Operation(summary = "获得微信小程序登录公开配置")
    public CommonResult<ConfigRespVO> getConfig() {
        return success(authService.getConfig());
    }

    @PostMapping("/qr/login")
    @PermitAll
    @Operation(summary = "创建网页登录小程序码")
    @RateLimiter(time = 60, count = 10, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<QrCreateRespVO> createLoginQrCode() {
        return success(authService.createLoginQrCode());
    }

    @PostMapping("/qr/bind")
    @Operation(summary = "创建当前账号的微信绑定小程序码")
    @RateLimiter(time = 60, count = 10, keyResolver = UserRateLimiterKeyResolver.class)
    public CommonResult<QrCreateRespVO> createBindQrCode() {
        return success(authService.createBindQrCode(getLoginUserId()));
    }

    @GetMapping("/qr/status")
    @PermitAll
    @Operation(summary = "查询扫码状态")
    @RateLimiter(time = 60, count = 120, keyResolver = ExpressionRateLimiterKeyResolver.class,
            keyArg = "#reqVO.ticket")
    public CommonResult<QrStatusRespVO> getQrStatus(@Valid QrStatusReqVO reqVO) {
        return success(authService.getQrStatus(reqVO));
    }

    @PostMapping("/qr/exchange")
    @PermitAll
    @Operation(summary = "使用已确认的扫码票据换取平台令牌")
    @RateLimiter(time = 60, count = 10, keyResolver = ExpressionRateLimiterKeyResolver.class,
            keyArg = "#reqVO.ticket")
    public CommonResult<AuthLoginRespVO> exchangeQrLogin(@Valid @RequestBody QrExchangeReqVO reqVO) {
        return success(authService.exchangeQrLogin(reqVO));
    }

    @PostMapping("/session")
    @PermitAll
    @Operation(summary = "使用 wx.login code 登录小程序")
    @RateLimiter(time = 60, count = 30, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<SessionRespVO> loginByCode(@Valid @RequestBody SessionReqVO reqVO) {
        return success(authService.loginByCode(reqVO.getCode()));
    }

    @PostMapping("/qr/scan")
    @PermitAll
    @Operation(summary = "小程序登记已扫描二维码")
    @RateLimiter(time = 60, count = 20, keyResolver = ExpressionRateLimiterKeyResolver.class,
            keyArg = "#reqVO.miniSessionToken")
    public CommonResult<QrStatusRespVO> scanQrCode(@Valid @RequestBody QrActionReqVO reqVO) {
        return success(authService.scanQrCode(reqVO));
    }

    @PostMapping("/qr/confirm")
    @PermitAll
    @Operation(summary = "小程序确认绑定或网页登录")
    @RateLimiter(time = 60, count = 10, keyResolver = ExpressionRateLimiterKeyResolver.class,
            keyArg = "#reqVO.miniSessionToken")
    public CommonResult<QrStatusRespVO> confirmQrCode(@Valid @RequestBody QrActionReqVO reqVO) {
        return success(authService.confirmQrCode(reqVO));
    }

    @GetMapping("/binding")
    @Operation(summary = "查询当前账号的微信绑定状态")
    public CommonResult<BindingRespVO> getBinding() {
        return success(authService.getBinding(getLoginUserId()));
    }

    @DeleteMapping("/binding")
    @Operation(summary = "解绑当前账号的微信")
    public CommonResult<Boolean> unbind() {
        authService.unbind(getLoginUserId());
        return success(true);
    }

}
