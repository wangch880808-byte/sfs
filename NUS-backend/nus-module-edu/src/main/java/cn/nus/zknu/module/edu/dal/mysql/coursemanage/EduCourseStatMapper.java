package cn.nus.zknu.module.edu.dal.mysql.coursemanage;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 详情页只读取统计值和成绩分析所需列，避免传输提交正文。 */
@Mapper
public interface EduCourseStatMapper {

    List<HomeworkCount> selectAssigneeCounts(@Param("homeworkIds") Collection<Long> homeworkIds);

    List<HomeworkStat> selectRecordStats(@Param("homeworkIds") Collection<Long> homeworkIds);

    List<ClassCount> selectClassStudentCounts(@Param("classIds") Collection<Long> classIds);

    List<EduHomeworkRecordDO> selectAssignedRecords(@Param("homeworkIds") Collection<Long> homeworkIds);

    HomeworkStat selectHomeworkStatistics(@Param("homeworkId") Long homeworkId, @Param("passScore") int passScore);

    @Data
    class HomeworkCount {
        private Long homeworkId;
        private long studentCount;
    }

    @Data
    class ClassCount {
        private Long classId;
        private int studentCount;
    }

    @Data
    class HomeworkStat {
        private Long homeworkId;
        private int totalCount;
        private int submittedCount;
        private int correctedCount;
        private int redoCount;
        private int passCount;
        private Long scoreTotal;
        private long scoreCount;
        private Integer maxScore;
        private Integer minScore;

        /** 保留 Java 整数求平均的精度，避免数据库 AVG 的小数位截断改变返回值。 */
        public Double averageScore() {
            return scoreCount == 0 ? null : scoreTotal.doubleValue() / scoreCount;
        }
    }
}
