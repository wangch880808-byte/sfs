package cn.nus.zknu.module.edu.dal.mysql.student;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentCapacityMapper {

    List<Map<String, Object>> selectFirstIndicatorRates(@Param("studentId") Long studentId,
                                                         @Param("classIds") List<Long> classIds);

    List<Map<String, Object>> selectSecondIndicatorRates(@Param("studentId") Long studentId,
                                                          @Param("classIds") List<Long> classIds,
                                                          @Param("indicatorFirstId") Long indicatorFirstId);

    List<Map<String, Object>> selectHomeworksByIndicatorFirst(@Param("studentId") Long studentId,
                                                              @Param("classIds") List<Long> classIds,
                                                              @Param("indicatorFirstId") Long indicatorFirstId);

    String selectIndicatorName(@Param("indicatorId") Long indicatorId);

    List<Map<String, Object>> selectScoreTrend(@Param("studentId") Long studentId,
                                                @Param("classIds") List<Long> classIds);

    Map<String, Object> selectOverallStats(@Param("studentId") Long studentId,
                                            @Param("classIds") List<Long> classIds);

}
