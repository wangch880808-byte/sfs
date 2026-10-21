package cn.nus.zknu.module.edu.service.clazz;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.system.api.dept.DeptApi;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 班级 Service 实现类
 */
@Service
@Slf4j
public class EduClassServiceImpl implements EduClassService {

    @Resource
    private EduClassMapper eduClassMapper;

    @Resource
    private DeptApi deptApi;
    @Resource
    private EduDeptScopeService deptScopeService;
    @Resource
    private EduPublicCourseSyncScheduler publicCourseSyncScheduler;

    /**
     * 创建班级。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClass(EduClassSaveReqVO createReqVO) {
        // 创建前校验专业/院系节点存在，避免脏外键
        deptScopeService.validateDeptInScope(createReqVO.getDeptId());
        validateDeptExists(createReqVO.getDeptId());
        EduClassDO eduClass = BeanUtils.toBean(createReqVO, EduClassDO.class);
        eduClassMapper.insert(eduClass);
        syncClassAfterCommit(eduClass.getId());
        return eduClass.getId();
    }

    /**
     * 更新班级。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClass(EduClassSaveReqVO updateReqVO) {
        validateClassExists(updateReqVO.getId());
        // 更新时同样校验院系有效性
        deptScopeService.validateDeptInScope(updateReqVO.getDeptId());
        validateDeptExists(updateReqVO.getDeptId());
        EduClassDO updateObj = BeanUtils.toBean(updateReqVO, EduClassDO.class);
        deptScopeService.validateAffectedRows(eduClassMapper.updateById(updateObj));
        syncClassAfterCommit(updateReqVO.getId());
    }

    /** 归档班级，保留学生关系。 */
    @Override
    public void deleteClass(Long id) {
        validateClassExists(id);
        EduClassDO update = new EduClassDO();
        update.setId(id);
        update.setStatus(1);
        deptScopeService.validateAffectedRows(eduClassMapper.updateById(update));
    }

    /** 批量归档班级。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClassList(List<Long> ids) {
        if (ids != null) {
            ids.forEach(this::deleteClass);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreClass(Long id) {
        validateClassExists(id);
        EduClassDO update = new EduClassDO();
        update.setId(id);
        update.setStatus(0);
        deptScopeService.validateAffectedRows(eduClassMapper.updateById(update));
        syncClassAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreClassList(List<Long> ids) {
        if (ids != null) {
            ids.forEach(this::restoreClass);
        }
    }

    /**
     * 获取班级详情。
     */
    @Override
    public EduClassDO getClass(Long id) {
        return eduClassMapper.selectById(id);
    }

    @Override
    public List<EduClassDO> getClassList(java.util.Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? Collections.emptyList() : eduClassMapper.selectByIds(ids);
    }

    /**
     * 分页查询班级。
     */
    @Override
    public PageResult<EduClassDO> getClassPage(EduClassPageReqVO reqVO) {
        return eduClassMapper.selectPage(reqVO);
    }

    @Override
    public List<EduClassDO> getClassList(EduClassPageReqVO reqVO) {
        return eduClassMapper.selectList(reqVO);
    }

    /**
     * 获取班级精简列表（用于下拉选择）。
     */
    @Override
    public List<EduClassDO> getSimpleClassList(Long deptId, Integer gradeYear) {
        return eduClassMapper.selectSimpleList(CommonStatusEnum.ENABLE.getStatus(), deptId, gradeYear);
    }

    /**
     * 校验班级存在（测试可见）。
     */
    @VisibleForTesting
    public void validateClassExists(Long id) {
        if (id == null) {
            throw exception(FORBIDDEN);
        }
        // 先校验范围，避免被部门规则隐藏的数据误报为不存在。
        deptScopeService.validateClassInScope(id);
        EduClassDO eduClass = eduClassMapper.selectById(id);
        if (eduClass == null) {
            throw exception(FORBIDDEN);
        }
        deptScopeService.validateDeptInScope(eduClass.getDeptId());
    }

    /**
     * 校验院系节点存在。
     */
    private void validateDeptExists(Long deptId) {
        if (deptId == null) {
            return;
        }
        deptApi.validateDeptList(Collections.singleton(deptId));
    }

    private void syncClassAfterCommit(Long classId) {
        publicCourseSyncScheduler.requestClass(classId);
    }

}
