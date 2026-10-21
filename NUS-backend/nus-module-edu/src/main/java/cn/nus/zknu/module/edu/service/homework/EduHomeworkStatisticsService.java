package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkStatisticsRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 任务统计，包含归档后的只读口径。 */
@Service
public class EduHomeworkStatisticsService {

    @Resource
    private EduCourseStatMapper courseStatMapper;
    @Resource
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Resource
    private EduMetricPolicy metricPolicy;

    public EduHomeworkStatisticsRespVO getHomeworkStatistics(Long homeworkId, EduHomeworkDO homework) {
        boolean readOnly = Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED)
                || !archiveAccessService.isInActiveScope(homework);
        EduCourseStatMapper.HomeworkStat stat = courseStatMapper.selectHomeworkStatistics(
                homeworkId, metricPolicy.passScore(homework));

        EduHomeworkStatisticsRespVO vo = new EduHomeworkStatisticsRespVO();
        vo.setHomeworkId(homeworkId);
        vo.setReadOnly(readOnly);
        vo.setTotalCount(stat.getTotalCount());
        vo.setSubmittedCount(stat.getSubmittedCount());
        vo.setCorrectedCount(stat.getCorrectedCount());
        vo.setRedoCount(stat.getRedoCount());
        vo.setPassCount(stat.getPassCount());
        // 所有非空分数参与统计，与原 Java 算法一致；空集合保留 null。
        vo.setAvgScore(stat.averageScore());
        vo.setMaxScore(stat.getMaxScore());
        vo.setMinScore(stat.getMinScore());
        return vo;
    }
}
