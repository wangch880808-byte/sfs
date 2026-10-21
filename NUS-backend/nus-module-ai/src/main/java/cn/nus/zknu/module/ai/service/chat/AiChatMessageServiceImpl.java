package cn.nus.zknu.module.ai.service.chat;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.ai.enums.model.AiCallBusiness;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiCallContext;
import cn.nus.zknu.module.ai.controller.admin.chat.vo.message.AiChatMessagePageReqVO;
import cn.nus.zknu.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import cn.nus.zknu.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO;
import cn.nus.zknu.module.ai.dal.dataobject.chat.AiChatConversationDO;
import cn.nus.zknu.module.ai.dal.dataobject.chat.AiChatMessageDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiChatRoleDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiToolDO;
import cn.nus.zknu.module.ai.dal.mysql.chat.AiChatMessageMapper;
import cn.nus.zknu.module.ai.enums.ErrorCodeConstants;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.nus.zknu.module.ai.service.model.AiChatRoleService;
import cn.nus.zknu.module.ai.service.model.AiModelService;
import cn.nus.zknu.module.ai.service.model.AiToolService;
import cn.nus.zknu.module.ai.util.AiUtils;
import cn.nus.zknu.module.ai.util.FileTypeUtils;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.pojo.CommonResult.error;
import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertList;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.CHAT_MESSAGE_NOT_EXIST;

/**
 * AI 聊天消息 Service 实现类
 *
 * @author hyh
 */
@Service
@Slf4j
public class AiChatMessageServiceImpl implements AiChatMessageService {

    /**
     * 附件转 ${@link UserMessage} 的内容模版
     */
    @SuppressWarnings("TextBlockMigration")
    private static final String Attachment_USER_MESSAGE_TEMPLATE = "使用 <Attachment></Attachment> 标记用户对话上传的附件内容:\n\n" + "%s\n\n" + // 多个 <Attachment></Attachment> 的拼接
            "回答要求：\n- 避免提及 <Attachment></Attachment> 附件的编码格式。";

    @Resource
    private AiChatMessageMapper chatMessageMapper;

    @Resource
    private AiChatConversationService chatConversationService;
    @Resource
    private AiChatRoleService chatRoleService;
    @Resource
    private AiModelService modalService;
    @Resource
    private AiToolService toolService;

    @Resource
    private ToolCallbackResolver toolCallbackResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatMessageSendRespVO sendMessage(AiChatMessageSendReqVO sendReqVO, Long userId) {
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService.validateChatConversationExists(sendReqVO.getConversationId());
        if (ObjUtil.notEqual(conversation.getUserId(), userId)) {
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        }
        List<AiChatMessageDO> historyMessages = chatMessageMapper.selectListByConversationId(conversation.getId());
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        ChatModel chatModel = modalService.getChatModel(model.getId());

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model, userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(), sendReqVO.getAttachmentUrls());

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model, userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(), sendReqVO.getAttachmentUrls());

        // 4.2 创建 chat 需要的 Prompt
        Prompt prompt = buildPrompt(conversation, historyMessages, model, sendReqVO);
        ChatResponse chatResponse = AiCallContext.call(
                AiCallContext.of(AiCallBusiness.AI_CHAT, String.valueOf(conversation.getId()), userId),
                () -> chatModel.call(prompt));

        // 4.3 更新响应内容
        String newContent = AiUtils.getChatResponseContent(chatResponse);
        String newReasoningContent = AiUtils.getChatResponseReasoningContent(chatResponse);
        chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(newContent).setReasoningContent(newReasoningContent));
        return new AiChatMessageSendRespVO()
                .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                        .setContent(newContent)
                        .setReasoningContent(newReasoningContent));
    }

    @Override
    public Flux<CommonResult<AiChatMessageSendRespVO>> sendChatMessageStream(AiChatMessageSendReqVO sendReqVO, Long userId) {
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService.validateChatConversationExists(sendReqVO.getConversationId());
        if (ObjUtil.notEqual(conversation.getUserId(), userId)) {
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        }
        List<AiChatMessageDO> historyMessages = chatMessageMapper.selectListByConversationId(conversation.getId());
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        StreamingChatModel chatModel = modalService.getChatModel(model.getId());

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model, userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(), sendReqVO.getAttachmentUrls());

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model, userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(), sendReqVO.getAttachmentUrls());

        // 4.2 构建 Prompt，并进行调用
        Prompt prompt = buildPrompt(conversation, historyMessages, model, sendReqVO);
        Flux<ChatResponse> streamResponse = AiCallContext.call(
                AiCallContext.of(AiCallBusiness.AI_CHAT, String.valueOf(conversation.getId()), userId),
                () -> chatModel.stream(prompt));

        // 4.3 流式返回
        StringBuilder contentBuffer = new StringBuilder();
        StringBuilder reasoningContentBuffer = new StringBuilder();
        return streamResponse.map(chunk -> {
            // 响应结果
            String newContent = AiUtils.getChatResponseContent(chunk);
            String newReasoningContent = AiUtils.getChatResponseReasoningContent(chunk);
            if (StrUtil.isNotEmpty(newContent)) {
                contentBuffer.append(newContent);
            }
            if (StrUtil.isNotEmpty(newReasoningContent)) {
                reasoningContentBuffer.append(newReasoningContent);
            }
            return success(new AiChatMessageSendRespVO()
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                    .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                            .setContent(StrUtil.nullToDefault(newContent, ""))
                            .setReasoningContent(StrUtil.nullToDefault(newReasoningContent, ""))));
        }).doOnComplete(() -> {
            chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
        }).doOnError(throwable -> {
            log.error("[sendChatMessageStream][userId({}) sendReqVO({}) 发生异常]", userId, sendReqVO, throwable);

            // 如果有内容，则更新内容
            if (StrUtil.isNotEmpty(contentBuffer)) {
                chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
            } else {
                // 否则，则进行删除
                chatMessageMapper.deleteById(assistantMessage.getId());
            }
        }).doOnCancel(() -> {
            log.info("[sendChatMessageStream][userId({}) sendReqVO({}) 取消请求]", userId, sendReqVO);

            // 如果有内容，则更新内容
            if (StrUtil.isNotEmpty(contentBuffer)) {
                chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId()).setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
            } else {
                // 否则，则进行删除
                chatMessageMapper.deleteById(assistantMessage.getId());
            }
        }).onErrorResume(error -> Flux.just(error(ErrorCodeConstants.CHAT_STREAM_ERROR)));
    }

    private Prompt buildPrompt(AiChatConversationDO conversation, List<AiChatMessageDO> messages,
                               AiModelDO model,
                               AiChatMessageSendReqVO sendReqVO) {
        List<Message> chatMessages = new ArrayList<>();
        // 1.1 System Context 角色设定
        if (StrUtil.isNotBlank(conversation.getSystemMessage())) {
            chatMessages.add(new SystemMessage(conversation.getSystemMessage()));
        }

        // 1.2 历史 history message 历史消息
        List<AiChatMessageDO> contextMessages = filterContextMessages(messages, conversation, sendReqVO);
        contextMessages.forEach(message -> {
            chatMessages.add(AiUtils.buildMessage(message.getType(), message.getContent()));
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(message.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
        });

        // 1.3 当前 user message 新发送消息
        chatMessages.add(new UserMessage(sendReqVO.getContent()));

        // 1.5 附件，通过 UserMessage 实现
        if (CollUtil.isNotEmpty(sendReqVO.getAttachmentUrls())) {
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(sendReqVO.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
        }

        // 2.1 查询 tool 工具
        List<ToolCallback> toolCallbacks = getToolCallbackListByRoleId(conversation.getRoleId());
        Map<String, Object> toolContext = CollUtil.isNotEmpty(toolCallbacks) ? AiUtils.buildCommonToolContext() : Map.of();
        // 2.2 构建 ChatOptions 对象
        AiPlatformEnum platform = AiPlatformEnum.validatePlatform(model.getPlatform());
        ChatOptions chatOptions = AiUtils.buildChatOptions(platform, model.getModel(), conversation.getTemperature(), conversation.getMaxTokens(), toolCallbacks, toolContext);
        return new Prompt(chatMessages, chatOptions);
    }

    private List<ToolCallback> getToolCallbackListByRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }
        AiChatRoleDO chatRole = chatRoleService.getChatRole(roleId);
        if (chatRole == null) {
            return null;
        }
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        // 1. 通过 toolIds
        if (CollUtil.isNotEmpty(chatRole.getToolIds())) {
            Set<String> toolNames = toolService.getToolList(chatRole.getToolIds()).stream()
                    .map(AiToolDO::getName)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toSet());
            toolNames.forEach(toolName -> {
                ToolCallback toolCallback = toolCallbackResolver.resolve(toolName);
                if (toolCallback != null) {
                    toolCallbacks.add(toolCallback);
                }
            });
        }
        return toolCallbacks;
    }

    /**
     * 从历史消息中，获得倒序的 n 组消息作为消息上下文
     * <p>
     * n 组：指的是 user + assistant 形成一组
     *
     * @param messages     消息列表
     * @param conversation 对话
     * @param sendReqVO    发送请求
     * @return 消息上下文
     */
    private List<AiChatMessageDO> filterContextMessages(List<AiChatMessageDO> messages, AiChatConversationDO conversation, AiChatMessageSendReqVO sendReqVO) {
        if (conversation.getMaxContexts() == null || ObjUtil.notEqual(sendReqVO.getUseContext(), Boolean.TRUE)) {
            return Collections.emptyList();
        }
        List<AiChatMessageDO> contextMessages = new ArrayList<>(conversation.getMaxContexts() * 2);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessageDO assistantMessage = CollUtil.get(messages, i);
            if (assistantMessage == null || assistantMessage.getReplyId() == null) {
                continue;
            }
            AiChatMessageDO userMessage = CollUtil.get(messages, i - 1);
            if (userMessage == null || ObjUtil.notEqual(assistantMessage.getReplyId(), userMessage.getId()) || StrUtil.isEmpty(assistantMessage.getContent())) {
                continue;
            }
            // 由于后续要 reverse 反转，所以先添加 assistantMessage
            contextMessages.add(assistantMessage);
            contextMessages.add(userMessage);
            // 超过最大上下文，结束
            if (contextMessages.size() >= conversation.getMaxContexts() * 2) {
                break;
            }
        }
        Collections.reverse(contextMessages);
        return contextMessages;
    }

    private UserMessage buildAttachmentUserMessage(List<String> attachmentUrls) {
        if (CollUtil.isEmpty(attachmentUrls)) {
            return null;
        }
        // 读取文件内容
        Map<String, String> attachmentContents = Maps.newLinkedHashMapWithExpectedSize(attachmentUrls.size());
        for (String attachmentUrl : attachmentUrls) {
            try {
                String name = FileNameUtil.getName(attachmentUrl);
                String mineType = FileTypeUtils.getMineType(name);
                String content;
                if (FileTypeUtils.isImage(mineType)) {
                    // 特殊：图片则转为 Base64
                    byte[] bytes = HttpUtil.downloadBytes(attachmentUrl);
                    content = Base64.encode(bytes);
                } else {
                    if (StrUtil.isBlank(mineType) || StrUtil.startWithIgnoreCase(mineType, "text/")
                            || StrUtil.containsIgnoreCase(mineType, "json")
                            || StrUtil.containsIgnoreCase(mineType, "xml")) {
                        content = HttpUtil.createGet(attachmentUrl).timeout(10_000).execute().body();
                    } else {
                        continue;
                    }
                }
                if (StrUtil.isNotEmpty(content)) {
                    attachmentContents.put(name, content);
                }
            } catch (Exception e) {
                log.error("[buildAttachmentUserMessage][读取附件({}) 发生异常]", attachmentUrl, e);
            }
        }
        if (CollUtil.isEmpty(attachmentContents)) {
            return null;
        }

        // 拼接 UserMessage 消息
        String attachment = attachmentContents.entrySet().stream().map(entry -> "<Attachment name=\"" + entry.getKey() + "\">" + entry.getValue() + "</Attachment>").collect(Collectors.joining("\n\n"));
        return new UserMessage(String.format(Attachment_USER_MESSAGE_TEMPLATE, attachment));
    }

    private AiChatMessageDO createChatMessage(Long conversationId, Long replyId, AiModelDO model, Long userId,
                                              Long roleId, MessageType messageType, String content,
                                              Boolean useContext, List<String> attachmentUrls) {
        AiChatMessageDO message = new AiChatMessageDO()
                .setConversationId(conversationId)
                .setReplyId(replyId)
                .setModel(model.getModel())
                .setModelId(model.getId())
                .setUserId(userId)
                .setRoleId(roleId)
                .setType(messageType.getValue())
                .setContent(content)
                .setUseContext(useContext)
                .setAttachmentUrls(attachmentUrls);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<AiChatMessageDO> getChatMessageListByConversationId(Long conversationId) {
        return chatMessageMapper.selectListByConversationId(conversationId);
    }

    @Override
    public void deleteChatMessage(Long id, Long userId) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null || ObjUtil.notEqual(message.getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public void deleteChatMessageByConversationId(Long conversationId, Long userId) {
        // 1. 校验消息存在
        List<AiChatMessageDO> messages = chatMessageMapper.selectListByConversationId(conversationId);
        if (CollUtil.isEmpty(messages) || ObjUtil.notEqual(messages.get(0).getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteByIds(convertList(messages, AiChatMessageDO::getId));
    }

    @Override
    public void deleteChatMessageByAdmin(Long id) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public Map<Long, Integer> getChatMessageCountMap(Collection<Long> conversationIds) {
        return chatMessageMapper.selectCountMapByConversationId(conversationIds);
    }

    @Override
    public PageResult<AiChatMessageDO> getChatMessagePage(AiChatMessagePageReqVO pageReqVO) {
        return chatMessageMapper.selectPage(pageReqVO);
    }

}
