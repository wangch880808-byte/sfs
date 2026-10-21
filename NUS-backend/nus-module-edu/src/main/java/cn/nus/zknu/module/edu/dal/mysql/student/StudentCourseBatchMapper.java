package cn.nus.zknu.module.edu.dal.mysql.student;

import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseHomeworkRow;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseProgressRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StudentCourseBatchMapper {
    List<StudentCourseProgressRow> selectCourseProgressBatch(@Param("studentId") Long studentId,
            @Param("courseIds") Collection<Long> courseIds);

    List<StudentCourseHomeworkRow> selectCourseHomeworksBatch(@Param("studentId") Long studentId,
            @Param("courseIds") Collection<Long> courseIds);
}
