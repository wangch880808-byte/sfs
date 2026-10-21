package cn.nus.zknu.module.system.service.wechat;

import cn.nus.zknu.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.nus.zknu.module.system.controller.admin.auth.vo.WechatMiniProgramAuthVO.*;

public interface WechatMiniProgramAuthService {

    ConfigRespVO getConfig();

    QrCreateRespVO createLoginQrCode();

    QrCreateRespVO createBindQrCode(Long userId);

    QrStatusRespVO getQrStatus(QrStatusReqVO reqVO);

    AuthLoginRespVO exchangeQrLogin(QrExchangeReqVO reqVO);

    SessionRespVO loginByCode(String code);

    QrStatusRespVO scanQrCode(QrActionReqVO reqVO);

    QrStatusRespVO confirmQrCode(QrActionReqVO reqVO);

    BindingRespVO getBinding(Long userId);

    void unbind(Long userId);

}
