package cn.nus.zknu.module.edu.dal.mysql.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiCalibrationSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiCalibrationSampleMapper {

    @Select("""
            SELECT feedback_type, deviation, full_score FROM (
              SELECT f.feedback_type, NULL AS deviation, COALESCE(h.score, 100) AS full_score,
                     f.create_time AS sampled_at, f.id AS sample_id
              FROM ai_edu_feedback f
              JOIN edu_homework_record r ON r.id = f.homework_record_id AND r.deleted = 0
              JOIN edu_homework h ON h.id = r.homework_id AND h.deleted = 0
              WHERE f.deleted = 0 AND f.teacher_id = #{teacherId} AND h.course_id = #{courseId}
                AND (#{taskOnly} = 0 OR h.id = #{homeworkId}) AND f.create_time >= #{since}
                AND f.feedback_type IN ('strict', 'normal', 'loose')
              UNION ALL
              SELECT NULL AS feedback_type, ar.teacher_score - ar.ai_score AS deviation,
                     COALESCE(h.score, 100) AS full_score, ar.update_time AS sampled_at,
                     ar.id AS sample_id
              FROM edu_homework_ai_review ar
              JOIN edu_homework_record r ON r.id = ar.homework_record_id AND r.deleted = 0
              JOIN edu_homework h ON h.id = r.homework_id AND h.deleted = 0
              WHERE ar.deleted = 0 AND h.teacher_id = #{teacherId} AND h.course_id = #{courseId}
                AND (#{taskOnly} = 0 OR h.id = #{homeworkId}) AND ar.update_time >= #{since}
                AND ar.ai_score IS NOT NULL AND ar.teacher_score IS NOT NULL
                AND ar.id = (SELECT MAX(x.id) FROM edu_homework_ai_review x
                             WHERE x.homework_record_id = ar.homework_record_id
                               AND x.deleted = 0 AND x.ai_score IS NOT NULL)
            ) samples ORDER BY sampled_at DESC, sample_id DESC LIMIT 50
            """)
    @Results({
            @Result(column = "feedback_type", property = "feedbackType"),
            @Result(column = "deviation", property = "deviation"),
            @Result(column = "full_score", property = "fullScore")
    })
    List<AiCalibrationSample> selectRecent(@Param("teacherId") Long teacherId,
                                           @Param("courseId") Long courseId,
                                           @Param("homeworkId") Long homeworkId,
                                           @Param("taskOnly") boolean taskOnly,
                                           @Param("since") LocalDateTime since);
}
