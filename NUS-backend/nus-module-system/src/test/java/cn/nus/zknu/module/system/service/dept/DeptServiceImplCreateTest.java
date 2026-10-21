package cn.nus.zknu.module.system.service.dept;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeptServiceImplCreateTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DeptServiceImpl deptService;

    @Mock
    private DeptMapper deptMapper;

    @Test
    void createDept_withoutCode_insertsTemporaryCodeThenStableCode() {
        mockInsertedId(300L);

        assertEquals(300L, deptService.createDept(request(null)));

        // dept_code 非空且唯一：插入时不能为空，拿到 ID 后改为 ORG-{id}
        verify(deptMapper).insert(argThat((DeptDO dept) -> dept.getDeptCode().startsWith("TMP-")));
        verify(deptMapper).updateById(argThat((DeptDO dept) -> dept.getId().equals(300L)
                && "ORG-300".equals(dept.getDeptCode())));
    }

    @Test
    void createDept_legacyCodeTaken_appendsRandomSuffix() {
        mockInsertedId(300L);
        when(deptMapper.selectByDeptCode("ORG-300")).thenReturn(new DeptDO());

        deptService.createDept(request(null));

        verify(deptMapper).updateById(argThat((DeptDO dept) -> dept.getDeptCode().startsWith("ORG-300-")));
    }

    @Test
    void createDept_withCode_keepsGivenCode() {
        mockInsertedId(301L);

        deptService.createDept(request("COLLEGE-E2E"));

        verify(deptMapper).insert(argThat((DeptDO dept) -> "COLLEGE-E2E".equals(dept.getDeptCode())));
        verify(deptMapper, never()).updateById(any(DeptDO.class));
    }

    private void mockInsertedId(Long id) {
        doAnswer(invocation -> {
            invocation.<DeptDO>getArgument(0).setId(id);
            return 1;
        }).when(deptMapper).insert(any(DeptDO.class));
    }

    private static DeptSaveReqVO request(String deptCode) {
        DeptSaveReqVO reqVO = new DeptSaveReqVO();
        reqVO.setName("测试学院");
        reqVO.setDeptCode(deptCode);
        reqVO.setSort(1);
        reqVO.setStatus(0);
        return reqVO;
    }

}
