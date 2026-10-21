package cn.nus.zknu.module.edu.controller.admin.message.vo;

import lombok.Data;

import java.util.List;

/** 会话详情，status：0 开启、1 关闭。 */
@Data
public class MessageConversationRespVO {
    private Integer status;
    private String studentName;
    private List<MessageDetailRespVO> messages;
}
