package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordGradeRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordHistoryRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 任务提交记录 Service 接口
 */
public interface EduHomeworkRecordService {

    /**
     * 提交任务
     *
     * @param submitReqVO 提交信息
     * @param studentId   学生 ID
     * @return 记录 ID
     */
    Long submitRecord(EduHomeworkRecordSubmitReqVO submitReqVO, Long studentId);

    /**
     * 更新任务
     *
     * @param updateReqVO 更新信息
     * @param studentId   学生 ID
     */
    void updateRecord(EduHomeworkRecordUpdateReqVO updateReqVO, Long studentId);

    /**
     * 获取提交详情
     *
     * @param id 记录 ID
     * @return 记录
     */
    EduHomeworkRecordDO getRecord(Long id);

    List<EduHomeworkRecordHistoryRespVO> getHistory(Long recordId);

    List<EduHomeworkRecordHistoryRespVO> getStudentHistory(Long homeworkId, Long studentId);

    /**
     * 分页查询任务记录
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduHomeworkRecordDO> getRecordPage(EduHomeworkRecordPageReqVO reqVO);

    /**
     * 待批改列表（状态=已提交待批改 或 已重新提交）
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduHomeworkRecordDO> getCorrectPage(EduHomeworkRecordPageReqVO reqVO);

    /**
     * 批改队列：任务下教师可批改的全部提交（不含未提交），不分页、不截断，只带列表需要的字段
     *
     * @param homeworkId 任务 ID
     * @return 队列
     */
    EduHomeworkRecordQueueRespVO getGradingQueue(Long homeworkId);

    /**
     * 批改任务。状态或更新时间在读取后变化时拒绝；学生通知在撤销期过后发出
     *
     * @param correctReqVO 批改信息
     * @return 批改后的记录与撤销凭据
     */
    EduHomeworkRecordGradeRespVO correctRecord(EduHomeworkRecordCorrectReqVO correctReqVO);

    /**
     * 打回重做（单个）。已打回时只调整重做期限
     *
     * @param id                 记录 ID
     * @param redoDeadline       重做截止时间，任务已截止时必填
     * @param expectedUpdateTime 读取时的更新时间，可为空
     * @return 打回后的记录与撤销凭据
     */
    EduHomeworkRecordGradeRespVO redoRecord(Long id, LocalDateTime redoDeadline, LocalDateTime expectedUpdateTime);

    /**
     * 批量打回重做，任一记录越权时整体失败
     *
     * @param ids          记录 ID 列表
     * @param redoDeadline 重做截止时间，任务已截止时必填
     */
    void redoRecordBatch(Collection<Long> ids, LocalDateTime redoDeadline);

    /**
     * 撤销期内撤销自己的批改或打回：恢复操作前的状态、分数、评语与重做期限，取消待发的学生通知
     *
     * @param undoToken 批改、打回返回的撤销凭据
     * @return 恢复后的记录
     */
    EduHomeworkRecordGradeRespVO undoGrading(Long undoToken);

    /**
     * 根据任务 ID 删除所有记录（级联删除用）
     *
     * @param homeworkId 任务 ID
     */
    void deleteByHomeworkId(Long homeworkId);

}
