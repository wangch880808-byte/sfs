package cn.nus.zknu.module.system.service.notify;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.nus.zknu.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO;
import cn.nus.zknu.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.nus.zknu.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.nus.zknu.module.system.dal.mysql.notify.NotifyMessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 站内信 Service 实现类
 *
 * @author hyh
 */
@Service
@Validated
@Slf4j
public class NotifyMessageServiceImpl implements NotifyMessageService {

    @Resource
    private NotifyMessageMapper notifyMessageMapper;

    @Override
    public Long createNotifyMessage(Long userId, Integer userType,
                                    NotifyTemplateDO template, String templateContent, Map<String, Object> templateParams) {
        NotifyMessageDO message = buildMessage(userId, userType, template, templateContent, templateParams);
        notifyMessageMapper.insert(message);
        return message.getId();
    }

    private NotifyMessageDO buildMessage(Long userId, Integer userType, NotifyTemplateDO template,
                                         String templateContent, Map<String, Object> templateParams) {
        return new NotifyMessageDO().setUserId(userId).setUserType(userType)
                .setTemplateId(template.getId()).setTemplateCode(template.getCode())
                .setTemplateType(template.getType()).setTemplateNickname(template.getNickname())
                .setTemplateContent(templateContent).setTemplateParams(templateParams).setReadStatus(false);
    }

    @Override
    public List<Long> createNotifyMessages(Collection<Long> userIds, Integer userType, NotifyTemplateDO template,
                                          String templateContent, Map<String, Object> templateParams) {
        List<Long> recipients = userIds == null ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Long> successful = new ArrayList<>();
        for (List<Long> batch : CollUtil.split(recipients, 500)) {
            List<NotifyMessageDO> messages = batch.stream()
                    .map(id -> buildMessage(id, userType, template, templateContent, templateParams)).toList();
            try {
                // 多值 INSERT 使单个批次整体成功或失败，避免 JDBC 批处理部分成功后无法确定收件人。
                if (Boolean.TRUE.equals(notifyMessageMapper.insertMessagesBatch(messages))) successful.addAll(batch);
            } catch (Exception ex) {
                log.error("[createNotifyMessages][站内信批量发送失败 templateCode={}, 用户数={}]", template.getCode(), batch.size(), ex);
            }
        }
        return successful;
    }

    @Override
    public PageResult<NotifyMessageDO> getNotifyMessagePage(NotifyMessagePageReqVO pageReqVO) {
        return notifyMessageMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<NotifyMessageDO> getMyMyNotifyMessagePage(NotifyMessageMyPageReqVO pageReqVO, Long userId, Integer userType) {
        return notifyMessageMapper.selectPage(pageReqVO, userId, userType);
    }

    @Override
    public NotifyMessageDO getNotifyMessage(Long id) {
        return notifyMessageMapper.selectById(id);
    }

    @Override
    public List<NotifyMessageDO> getUnreadNotifyMessageList(Long userId, Integer userType, Integer size) {
        return notifyMessageMapper.selectUnreadListByUserIdAndUserType(userId, userType, size);
    }

    @Override
    public Long getUnreadNotifyMessageCount(Long userId, Integer userType) {
        return notifyMessageMapper.selectUnreadCountByUserIdAndUserType(userId, userType);
    }

    @Override
    public int updateNotifyMessageRead(Collection<Long> ids, Long userId, Integer userType) {
        return notifyMessageMapper.updateListRead(ids, userId, userType);
    }

    @Override
    public int updateAllNotifyMessageRead(Long userId, Integer userType) {
        return notifyMessageMapper.updateListRead(userId, userType);
    }

}
