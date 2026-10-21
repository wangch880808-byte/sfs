package cn.nus.zknu.module.edu.dal.mysql.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 学生课程查询。带 {@code history} 参数的查询：false 为当前口径（班级、课程、班课都在用），
 * true 为历史可见口径（只读，不看是否归档）。
 */
@Mapper
public interface StudentCourseMapper {

    List<Long> selectClassIdsByStudentId(@Param("studentId") Long studentId);

    List<AppStudentCourseRespVO> selectCoursesByClassIds(@Param("classIds") List<Long> classIds);

    AppStudentCourseDetailRespVO selectCourseDetail(@Param("courseId") Long courseId,
                                                    @Param("includeArchived") boolean includeArchived);

    boolean existsStudentCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /** 学生在这门课有历史可见的任务（班级、课程或班课可能已归档） */
    boolean existsStudentCourseHistory(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    List<AppStudentCourseDetailRespVO.Homework> selectCourseHomeworks(
            @Param("studentId") Long studentId, @Param("courseId") Long courseId,
            @Param("history") boolean history);

    /** 等级闯关：分配给学生的初、中、高级任务及本人提交情况 */
    List<StudentLevelTaskRow> selectLevelTasks(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
                                               @Param("history") boolean history);

    /** 等级闯关：学生当前所在班级在这门课已发布的各等级任务数（含没有分配给该学生的，如插班前已截止的任务） */
    List<Map<String, Object>> selectClassLevelTaskCounts(@Param("studentId") Long studentId,
                                                         @Param("courseId") Long courseId);

    Map<String, Object> selectCourseProgress(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
                                             @Param("history") boolean history);

}
