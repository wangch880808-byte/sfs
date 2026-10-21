package cn.nus.zknu.module.system.dal.dataobject.user;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 用户扩展信息 DO
 */
@TableName(value = "system_user_profile", autoResultMap = true)
@KeySequence("system_user_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfileDO extends BaseDO {

    /**
     * 自增 ID
     */
    @TableId
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 职称
     */
    private Integer professionalTitle;

    /**
     * 入学年份
     */
    private Integer gradeYear;

    /**
     * 班级 ID 数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> classIds;

}
