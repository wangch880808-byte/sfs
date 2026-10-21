package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkGradingActionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.NOTIFY_PENDING;

@Mapper
public interface EduHomeworkGradingActionMapper extends BaseMapperX<EduHomeworkGradingActionDO> {

    /** 某条提交记录最近一次操作。 */
    default EduHomeworkGradingActionDO selectLatestByRecordId(Long recordId) {
        return selectOne(new LambdaQueryWrapper<EduHomeworkGradingActionDO>()
                .eq(EduHomeworkGradingActionDO::getRecordId, recordId)
                .orderByDesc(EduHomeworkGradingActionDO::getId)
                .last("LIMIT 1"));
    }

    /** 撤销期已过、仍待发送的通知，按先后顺序。 */
    default List<EduHomeworkGradingActionDO> selectDue(LocalDateTime dueBefore, int limit) {
        return selectList(new LambdaQueryWrapper<EduHomeworkGradingActionDO>()
                .eq(EduHomeworkGradingActionDO::getNotifyStatus, NOTIFY_PENDING)
                .le(EduHomeworkGradingActionDO::getUndoDeadline, dueBefore)
                .orderByAsc(EduHomeworkGradingActionDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /**
     * 把待发送的通知改成目标状态（已发送、已撤销）。发送任务与撤销都走这里，只有一方能改成功。
     *
     * @return 1 表示抢到了，0 表示已被另一方处理
     */
    default int transitFromPending(Long id, int targetStatus, LocalDateTime now) {
        EduHomeworkGradingActionDO update = new EduHomeworkGradingActionDO();
        update.setNotifyStatus(targetStatus);
        update.setNotifyTime(now);
        return update(update, new LambdaUpdateWrapper<EduHomeworkGradingActionDO>()
                .eq(EduHomeworkGradingActionDO::getId, id)
                .eq(EduHomeworkGradingActionDO::getNotifyStatus, NOTIFY_PENDING));
    }
}
