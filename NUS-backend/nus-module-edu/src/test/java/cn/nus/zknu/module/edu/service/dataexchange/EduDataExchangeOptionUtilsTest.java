package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EduDataExchangeOptionUtilsTest {

    @Test
    void departmentOptions_excludesFirstLevelAndDisplaysPathFromSecondLevel() {
        DeptDO school = department(1L, 0L, "SCHOOL", "ZKNU", "周口师范学院");
        DeptDO college = department(2L, 1L, "COLLEGE", "CS", "计算机学院");
        DeptDO major = department(3L, 2L, "MAJOR", "SE", "软件工程");

        assertEquals(List.of("计算机学院 [CS]", "计算机学院 / 软件工程 [SE]"),
                EduDataExchangeOptionUtils.departmentOptions(List.of(school, college, major), null));
        assertEquals(List.of("计算机学院 / 软件工程 [SE]"),
                EduDataExchangeOptionUtils.departmentOptions(List.of(school, college, major), "MAJOR"));
    }

    @Test
    void parseSelectedCode_acceptsDropdownLabelAndLegacyRawCode() {
        assertEquals("SE", EduDataExchangeOptionUtils.parseSelectedCode(
                "计算机学院 / 软件工程 [SE]"));
        assertEquals("SE", EduDataExchangeOptionUtils.parseSelectedCode("SE"));
    }

    private DeptDO department(Long id, Long parentId, String type, String code, String name) {
        DeptDO department = new DeptDO();
        department.setId(id);
        department.setParentId(parentId);
        department.setDeptType(type);
        department.setDeptCode(code);
        department.setName(name);
        return department;
    }

}
