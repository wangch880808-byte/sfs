package cn.nus.zknu.module.edu.service.message;

import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageStudentListRespVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageConversationRespVO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.StudentConversationRespVO;

import java.util.List;

/**
 * 留言互动 Service 接口
 */
public interface EduMessageService {

    List<MessageStudentListRespVO> getStudentList(Long classId, Long courseId, String name);

    MessageConversationRespVO getDetail(Long classId, Long courseId, Long studentId);

    List<StudentConversationRespVO> getMyConversations();

    MessageConversationRespVO getMyDetail(Long classId, Long courseId);

    void markMyRead(Long classId, Long courseId);

    void reply(Long classId, Long courseId, Long studentId, String content);

    void studentAdd(Long classId, Long courseId, String content);

    void closeConversation(Long classId, Long courseId, Long studentId);

    void reopenConversation(Long classId, Long courseId, Long studentId);

}
