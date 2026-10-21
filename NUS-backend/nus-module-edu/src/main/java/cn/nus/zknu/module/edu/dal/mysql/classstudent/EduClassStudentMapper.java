package cn.nus.zknu.module.edu.dal.mysql.classstudent;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface EduClassStudentMapper extends BaseMapperX<EduClassStudentDO> {

    default List<EduClassStudentDO> selectListByClassId(Long classId) {
        return selectList(EduClassStudentDO::getClassId, classId);
    }

    default List<EduClassStudentDO> selectListByClassIds(Collection<Long> classIds) {
        if (CollUtil.isEmpty(classIds)) {
            return Collections.emptyList();
        }
        return selectList(EduClassStudentDO::getClassId, classIds);
    }

    default List<EduClassStudentDO> selectListByClassIdAndStudentIds(Long classId, Collection<Long> studentIds) {
        if (classId == null || CollUtil.isEmpty(studentIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<EduClassStudentDO>()
                .eq(EduClassStudentDO::getClassId, classId)
                .in(EduClassStudentDO::getStudentId, studentIds));
    }

    @Select("<script>SELECT * FROM edu_class_student WHERE class_id = #{classId} "
            + "AND student_id IN "
            + "<foreach collection='studentIds' item='studentId' open='(' separator=',' close=')'>"
            + "#{studentId}</foreach></script>")
    List<EduClassStudentDO> selectListIncludingDeleted(@Param("classId") Long classId,
                                                       @Param("studentIds") Collection<Long> studentIds);

    @Select("<script>SELECT * FROM edu_class_student WHERE class_id IN "
            + "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " AND student_id IN "
            + "<foreach collection='studentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<EduClassStudentDO> selectImportRelationsIncludingDeleted(@Param("classIds") Collection<Long> classIds,
                                                               @Param("studentIds") Collection<Long> studentIds);

    @Update("UPDATE edu_class_student SET deleted = 0, status = #{status}, join_time = #{joinTime}, "
            + "remark = #{remark}, update_time = NOW() WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id, @Param("status") Integer status,
                    @Param("joinTime") LocalDateTime joinTime, @Param("remark") String remark);

    default List<EduClassStudentDO> selectListByStudentIds(Collection<Long> studentIds) {
        if (CollUtil.isEmpty(studentIds)) {
            return Collections.emptyList();
        }
        return selectList(EduClassStudentDO::getStudentId, studentIds);
    }

    default void deleteByClassIdAndStudentIds(Long classId, Collection<Long> studentIds) {
        if (classId == null || CollUtil.isEmpty(studentIds)) {
            return;
        }
        delete(new LambdaQueryWrapperX<EduClassStudentDO>()
                .eq(EduClassStudentDO::getClassId, classId)
                .in(EduClassStudentDO::getStudentId, studentIds));
    }

    default void deleteByClassId(Long classId) {
        if (classId == null) {
            return;
        }
        delete(EduClassStudentDO::getClassId, classId);
    }

    default void deleteByClassIds(Collection<Long> classIds) {
        if (CollUtil.isEmpty(classIds)) {
            return;
        }
        deleteBatch(EduClassStudentDO::getClassId, classIds);
    }

}
