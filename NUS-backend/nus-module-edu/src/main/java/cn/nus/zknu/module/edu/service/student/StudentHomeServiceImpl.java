package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeOverviewRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生首页 Service 实现类
 */
@Service
public class StudentHomeServiceImpl implements StudentHomeService {

    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private EduClassStudentMapper classStudentMapper;
    @Resource
    private EduHomeworkMapper homeworkMapper;

    @Override
    public AppStudentHomeOverviewRespVO getOverview(Long studentId) {
        AppStudentHomeOverviewRespVO resp = new AppStudentHomeOverviewRespVO();

        List<Long> classIds = getStudentClassIds(studentId);
        if (classIds.isEmpty()) {
            resp.setPendingTaskCount(0);
            resp.setPendingCount(0);
            resp.setSubmittedCount(0);
            resp.setAverageScore(0D);
            resp.setRecentScores(Collections.emptyList());
            return resp;
        }

        List<EduHomeworkDO> publishedHomeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkDO>()
                        .select(EduHomeworkDO::getId)
                        .in(EduHomeworkDO::getClassId, classIds)
                        .eq(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED));
        Set<Long> homeworkIds = publishedHomeworks.stream()
                .map(EduHomeworkDO::getId)
                .collect(Collectors.toSet());

        Map<Long, EduHomeworkRecordDO> recordMap = Collections.emptyMap();
        if (!homeworkIds.isEmpty()) {
            recordMap = homeworkRecordMapper.selectList(
                            new LambdaQueryWrapper<EduHomeworkRecordDO>()
                                    .select(EduHomeworkRecordDO::getHomeworkId, EduHomeworkRecordDO::getStatus)
                                    .eq(EduHomeworkRecordDO::getStudentId, studentId)
                                    .in(EduHomeworkRecordDO::getHomeworkId, homeworkIds))
                    .stream()
                    .collect(Collectors.toMap(EduHomeworkRecordDO::getHomeworkId, record -> record, (a, b) -> a));
        }

        int pendingCount = 0;
        int submittedCount = 0;
        for (EduHomeworkDO homework : publishedHomeworks) {
            EduHomeworkRecordDO record = recordMap.get(homework.getId());
            if (record == null || Arrays.asList(EduHomeworkRecordStatusEnum.NOT_SUBMITTED, EduHomeworkRecordStatusEnum.REDO).contains(record.getStatus())) {
                pendingCount++;
            } else if (record.getStatus() != null && EduHomeworkRecordStatusEnum.CORRECTABLE.contains(record.getStatus())) {
                submittedCount++;
            }
        }
        resp.setPendingTaskCount(pendingCount);
        resp.setPendingCount(pendingCount);
        resp.setSubmittedCount(submittedCount);

        // 近期5条成绩：已批改的记录
        List<EduHomeworkRecordDO> recentRecords = homeworkRecordMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .select(EduHomeworkRecordDO::getHomeworkId, EduHomeworkRecordDO::getScore,
                                EduHomeworkRecordDO::getStatus, EduHomeworkRecordDO::getCorrectTime)
                        .eq(EduHomeworkRecordDO::getStudentId, studentId)
                        .eq(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.CORRECTED) // 已批改
                        .orderByDesc(EduHomeworkRecordDO::getCorrectTime)
                        .last("LIMIT 5"));
        Map<Long, EduHomeworkDO> recentHomeworks = recentRecords.isEmpty() ? Collections.emptyMap()
                : homeworkMapper.selectList(new LambdaQueryWrapper<EduHomeworkDO>()
                        .select(EduHomeworkDO::getId, EduHomeworkDO::getHomeworkName)
                        .in(EduHomeworkDO::getId, recentRecords.stream()
                                .map(EduHomeworkRecordDO::getHomeworkId).distinct().toList()))
                        .stream().collect(Collectors.toMap(EduHomeworkDO::getId, homework -> homework));
        List<AppStudentHomeOverviewRespVO.RecentScore> recentScores = new ArrayList<>();
        for (EduHomeworkRecordDO record : recentRecords) {
            AppStudentHomeOverviewRespVO.RecentScore score = new AppStudentHomeOverviewRespVO.RecentScore();
            score.setHomeworkId(record.getHomeworkId());
            score.setScore(record.getScore());
            score.setStatus(record.getStatus());
            var homework = recentHomeworks.get(record.getHomeworkId());
            if (homework != null) {
                score.setHomeworkName(homework.getHomeworkName());
            }
            score.setCorrectTime(record.getCorrectTime());
            recentScores.add(score);
        }
        resp.setRecentScores(recentScores);

        double averageScore = recentRecords.stream()
                .map(EduHomeworkRecordDO::getScore)
                .filter(score -> score != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);
        resp.setAverageScore(Math.round(averageScore * 10) / 10.0);

        return resp;
    }

    private List<Long> getStudentClassIds(Long studentId) {
        List<EduClassStudentDO> classStudents = classStudentMapper.selectListByStudentIds(
                Collections.singletonList(studentId));
        return classStudents.stream()
                .map(EduClassStudentDO::getClassId)
                .collect(Collectors.toList());
    }

}
