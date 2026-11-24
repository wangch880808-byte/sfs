package cn.nus.zknu.module.edu.dal.mysql.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统计分析 Mapper
 */
@Mapper
public interface StatisticsMapper {

    Map<String, Object> selectTotalPassRate(@Param("teacherId") Long teacherId,
                                            @Param("classIds") List<Long> classIds,
                                            @Param("courseId") Long courseId,
                                            @Param("academicYear") Integer academicYear,
                                            @Param("goodRatio") Double goodRatio,
                                            @Param("excellentRatio") Double excellentRatio);


    List<Map<String, Object>> selectWorkTrend(@Param("courseId") Long courseId,
                                               @Param("classId") Long classId,
                                               @Param("claimTeacherId") Long claimTeacherId);

    default List<Map<String, Object>> selectWorkTrend(Long courseId, Long classId) {
        return selectWorkTrend(courseId, classId, null);
    }






    List<Map<String, Object>> selectTeacherCourses(@Param("teacherId") Long teacherId);

    List<Map<String, Object>> selectCourseClasses(@Param("teacherId") Long teacherId,
                                                  @Param("courseId") Long courseId);



    List<Long> selectClassIdsByDept(@Param("collegeId") Long collegeId,
                                    @Param("majorId") Long majorId);

    List<Map<String, Object>> selectCourseAnalysisAvg(@Param("courseId") Long courseId,
                                                       @Param("classId") Long classId,
                                                       @Param("claimTeacherId") Long claimTeacherId);

    default List<Map<String, Object>> selectCourseAnalysisAvg(Long courseId, Long classId) {
        return selectCourseAnalysisAvg(courseId, classId, null);
    }

    boolean selectStatisticsCourseAccess(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);

    boolean selectAnalysisClassCourseExists(@Param("courseId") Long courseId, @Param("classId") Long classId);

    boolean selectStatisticsClassAccess(@Param("teacherId") Long teacherId, @Param("classId") Long classId);

    List<Map<String, Object>> selectHomeworkTopStudents(@Param("homeworkId") Long homeworkId);

    List<Map<String, Object>> selectHomeworkLastStudents(@Param("homeworkId") Long homeworkId);

    List<Map<String, Object>> selectHomeworkStudentExtremes(@Param("homeworkIds") List<Long> homeworkIds);

    // 看板查询
    Long selectTotalStudents();
    Long selectTotalTeachers();
    Long selectTotalCourses();
    Long selectTotalHomeworks();
    Integer selectPendingPublicAuditCount();
    Long selectTermCount();
    Long selectOfficialCourseCount();
    Long selectClassCourseCount();
    Long selectActiveClassCount();
    List<Map<String, Object>> selectRecentTerms();

    Long selectWeekSubmissions(@Param("teacherId") Long teacherId);
    Long selectPendingCorrections(@Param("teacherId") Long teacherId);
    Long selectAiCorrections();
    Long selectTeacherCourseCount(@Param("teacherId") Long teacherId);
    Long selectUnreadMessages(@Param("teacherId") Long teacherId);
    List<Map<String, Object>> selectRecentHomeworks(@Param("teacherId") Long teacherId);

}
