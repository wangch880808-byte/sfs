package cn.nus.zknu.module.system.controller.admin.socail;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.nus.zknu.module.system.controller.admin.socail.vo.user.SocialUserBindReqVO;
import cn.nus.zknu.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO;
import cn.nus.zknu.module.system.controller.admin.socail.vo.user.SocialUserRespVO;
import cn.nus.zknu.module.system.controller.admin.socail.vo.user.SocialUserUnbindReqVO;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialUserBindDO;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.social.SocialUserBindMapper;
import cn.nus.zknu.module.system.service.social.SocialUserService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertList;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 社交用户")
@RestController
@RequestMapping("/system/social-user")
@Validated
public class SocialUserController {

    @Resource
    private SocialUserService socialUserService;
    @Resource
    private SocialUserBindMapper socialUserBindMapper;
    @Resource
    private AdminUserService adminUserService;

    @PostMapping("/bind")
    @Operation(summary = "社交绑定，使用 code 授权码")
    public CommonResult<Boolean> socialBind(@RequestBody @Valid SocialUserBindReqVO reqVO) {
        socialUserService.bindSocialUser(new SocialUserBindReqDTO().setSocialType(reqVO.getType())
                .setCode(reqVO.getCode()).setState(reqVO.getState())
                .setUserId(getLoginUserId()).setUserType(UserTypeEnum.ADMIN.getValue()));
        return CommonResult.success(true);
    }

    @DeleteMapping("/unbind")
    @Operation(summary = "取消社交绑定")
    public CommonResult<Boolean> socialUnbind(@RequestBody SocialUserUnbindReqVO reqVO) {
        socialUserService.unbindSocialUser(getLoginUserId(), UserTypeEnum.ADMIN.getValue(), reqVO.getType(), reqVO.getOpenid());
        return CommonResult.success(true);
    }

    @GetMapping("/get-bind-list")
    @Operation(summary = "获得绑定社交用户列表")
    public CommonResult<List<SocialUserRespVO>> getBindSocialUserList() {
        List<SocialUserDO> list = socialUserService.getSocialUserList(getLoginUserId(), UserTypeEnum.ADMIN.getValue());
        return success(convertList(list, socialUser -> new SocialUserRespVO() // 返回精简信息
                .setId(socialUser.getId()).setType(socialUser.getType()).setOpenid(socialUser.getOpenid())
                .setNickname(socialUser.getNickname()).setAvatar(socialUser.getNickname())));
    }

    // ==================== 社交用户 CRUD ====================

    @GetMapping("/get")
    @Operation(summary = "获得社交用户")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('system:social-user:query')")
    public CommonResult<SocialUserRespVO> getSocialUser(@RequestParam("id") Long id) {
        SocialUserDO socialUser = socialUserService.getSocialUser(id);
        return success(fillBindInfo(socialUser));
    }

    @GetMapping("/page")
    @Operation(summary = "获得社交用户分页")
    @PreAuthorize("@ss.hasPermission('system:social-user:query')")
    public CommonResult<PageResult<SocialUserRespVO>> getSocialUserPage(@Valid SocialUserPageReqVO pageVO) {
        PageResult<SocialUserDO> pageResult = socialUserService.getSocialUserPage(pageVO);
        return success(fillBindInfo(pageResult));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除社交用户并解除绑定")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('system:social-user:delete')")
    public CommonResult<Boolean> deleteSocialUser(@RequestParam("id") Long id) {
        socialUserService.deleteSocialUser(id);
        return success(true);
    }

    private SocialUserRespVO fillBindInfo(SocialUserDO socialUser) {
        if (socialUser == null) {
            return null;
        }
        SocialUserRespVO respVO = BeanUtils.toBean(socialUser, SocialUserRespVO.class);
        List<SocialUserBindDO> binds = socialUserBindMapper.selectListBySocialUserIds(List.of(socialUser.getId()));
        if (binds.isEmpty()) {
            return respVO;
        }
        return fillBindInfo(respVO, binds.get(0), adminUserService.getUserMap(
                CollectionUtils.convertSet(binds, SocialUserBindDO::getUserId)));
    }

    private PageResult<SocialUserRespVO> fillBindInfo(PageResult<SocialUserDO> pageResult) {
        if (pageResult == null || pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return BeanUtils.toBean(pageResult, SocialUserRespVO.class);
        }
        PageResult<SocialUserRespVO> respPage = BeanUtils.toBean(pageResult, SocialUserRespVO.class);
        List<SocialUserBindDO> binds = socialUserBindMapper.selectListBySocialUserIds(
                CollectionUtils.convertSet(pageResult.getList(), SocialUserDO::getId));
        if (binds.isEmpty()) {
            return respPage;
        }
        Map<Long, SocialUserBindDO> bindMap = CollectionUtils.convertMap(binds, SocialUserBindDO::getSocialUserId,
                bind -> bind, (oldBind, newBind) -> oldBind);
        Map<Long, AdminUserDO> userMap = adminUserService.getUserMap(
                CollectionUtils.convertSet(binds, SocialUserBindDO::getUserId));
        respPage.getList().forEach(item -> fillBindInfo(item, bindMap.get(item.getId()), userMap));
        return respPage;
    }

    private SocialUserRespVO fillBindInfo(SocialUserRespVO respVO, SocialUserBindDO bind,
                                          Map<Long, AdminUserDO> userMap) {
        if (respVO == null || bind == null) {
            return respVO;
        }
        respVO.setUserId(bind.getUserId());
        AdminUserDO user = userMap.get(bind.getUserId());
        if (user != null) {
            respVO.setUserAccount(user.getUsername());
            respVO.setUserNickname(user.getNickname());
            respVO.setAccountUserType(user.getUserType());
        }
        return respVO;
    }

}
