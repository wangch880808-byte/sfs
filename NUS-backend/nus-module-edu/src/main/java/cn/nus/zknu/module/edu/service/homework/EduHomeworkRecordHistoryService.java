package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordHistoryRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;

import java.time.LocalDateTime;
import java.util.List;

public interface EduHomeworkRecordHistoryService {
    /**
     * 追加一条轨迹。
     *
     * @return 新轨迹的 ID
     */
    Long append(EduHomeworkRecordDO record, String eventType, Integer toStatus,
                Integer score, String comment, LocalDateTime redoDeadline, Long aiReviewId);

    List<EduHomeworkRecordHistoryRespVO> listByRecordId(Long recordId);
}
