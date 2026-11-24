package cn.nus.zknu.module.edu.service.term;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.term.EduTermDO;
import cn.nus.zknu.module.edu.dal.mysql.term.EduTermMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 学期 Service 实现类
 */
@Service
public class EduTermServiceImpl implements EduTermService {

    @Resource
    private EduTermMapper eduTermMapper;

    /**
     * 创建学期。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTerm(EduTermSaveReqVO createReqVO) {
        EduTermDO term = BeanUtils.toBean(createReqVO, EduTermDO.class);
        eduTermMapper.insert(term);
        return term.getId();
    }

    /**
     * 更新学期。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTerm(EduTermSaveReqVO updateReqVO) {
        validateTermExists(updateReqVO.getId());
        EduTermDO updateObj = BeanUtils.toBean(updateReqVO, EduTermDO.class);
        eduTermMapper.updateById(updateObj);
    }

    /**
     * 归档学期。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveTerm(Long id) {
        validateTermExists(id);
        EduTermDO update = new EduTermDO();
        update.setId(id);
        update.setStatus(1);
        eduTermMapper.updateById(update);
    }

    /**
     * 启用学期。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableTerm(Long id) {
        validateTermExists(id);
        EduTermDO update = new EduTermDO();
        update.setId(id);
        update.setStatus(0);
        eduTermMapper.updateById(update);
    }

    /**
     * 获取学期详情。
     */
    @Override
    public EduTermDO getTerm(Long id) {
        return eduTermMapper.selectById(id);
    }

    /**
     * 分页查询学期。
     */
    @Override
    public PageResult<EduTermDO> getTermPage(EduTermPageReqVO reqVO) {
        return eduTermMapper.selectPage(reqVO);
    }

    /**
     * 校验学期存在。
     */
    private void validateTermExists(Long id) {
        if (id == null || eduTermMapper.selectById(id) == null) {
            throw exception(NOT_FOUND);
        }
    }

}
