package cn.nus.zknu.module.edu.dal.mysql.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkRespVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentHomeworkMapper {

    IPage<AppStudentHomeworkRespVO> selectHomeworkPage(
            Page<AppStudentHomeworkRespVO> page,
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            @Param("status") Integer status,
            @Param("history") boolean history);
}
