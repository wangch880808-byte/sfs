package cn.nus.zknu.module.capacity.dal.mysql.course;

import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseMemberDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** 父行串行锁与成员当前读缺一不可，普通 SELECT 会复用外层事务的旧快照。 */
class CapacityCourseMemberLockContractTest {

    @BeforeAll
    static void initTableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CapacityCourseDO.class);
        TableInfoHelper.initTableInfo(assistant, CapacityCourseMemberDO.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacementLocksBothCourseAndMemberReads() {
        CapacityCourseMapper courses = mock(CapacityCourseMapper.class);
        doCallRealMethod().when(courses).selectByIdForUpdate(1L);
        CapacityCourseMemberMapper members = mock(CapacityCourseMemberMapper.class);
        doCallRealMethod().when(members).selectListByCourseIdForUpdate(1L);

        courses.selectByIdForUpdate(1L);
        members.selectListByCourseIdForUpdate(1L);

        ArgumentCaptor<Wrapper<CapacityCourseDO>> courseQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(courses).selectOne(courseQuery.capture());
        assertTrue(courseQuery.getValue().getSqlSegment().contains("id ="));
        assertTrue(courseQuery.getValue().getSqlSegment().endsWith("FOR UPDATE"));
        ArgumentCaptor<Wrapper<CapacityCourseMemberDO>> memberQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(members).selectList(memberQuery.capture());
        assertTrue(memberQuery.getValue().getSqlSegment().contains("course_id ="));
        assertTrue(memberQuery.getValue().getSqlSegment().endsWith("FOR UPDATE"));
    }
}
