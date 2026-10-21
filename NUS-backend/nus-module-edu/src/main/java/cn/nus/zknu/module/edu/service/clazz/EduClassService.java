package cn.nus.zknu.module.edu.service.clazz;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;

import java.util.List;
import java.util.Collection;

/**
 * 班级 Service 接口
 */
public interface EduClassService {

    /**
     * 创建班级
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createClass(EduClassSaveReqVO createReqVO);

    /**
     * 更新班级
     *
     * @param updateReqVO 更新信息
     */
    void updateClass(EduClassSaveReqVO updateReqVO);

    /**
     * 归档班级
     *
     * @param id 编号
     */
    void deleteClass(Long id);

    /**
     * 批量归档班级
     *
     * @param ids 编号列表
     */
    void deleteClassList(List<Long> ids);

    void restoreClass(Long id);

    void restoreClassList(List<Long> ids);

    /**
     * 获得班级详情
     *
     * @param id 编号
     * @return 班级
     */
    EduClassDO getClass(Long id);

    List<EduClassDO> getClassList(Collection<Long> ids);

    /**
     * 获得班级分页
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduClassDO> getClassPage(EduClassPageReqVO reqVO);

    List<EduClassDO> getClassList(EduClassPageReqVO reqVO);

    /**
     * 获得班级精简列表
     *
     * @param deptId    组织 ID
     * @param gradeYear 入学年份
     * @return 班级列表
     */
    List<EduClassDO> getSimpleClassList(Long deptId, Integer gradeYear);

}
