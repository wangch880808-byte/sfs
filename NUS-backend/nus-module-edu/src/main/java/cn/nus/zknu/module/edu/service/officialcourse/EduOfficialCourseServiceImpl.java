package cn.nus.zknu.module.edu.service.officialcourse;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCoursePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCourseSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.officialcourse.EduOfficialCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.officialcourse.EduOfficialCourseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 正式课程 Service 实现类
 */
@Service
public class EduOfficialCourseServiceImpl implements EduOfficialCourseService {

    @Resource
    private EduOfficialCourseMapper eduOfficialCourseMapper;

    /**
     * 创建正式课程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOfficialCourse(EduOfficialCourseSaveReqVO createReqVO) {
        EduOfficialCourseDO course = BeanUtils.toBean(createReqVO, EduOfficialCourseDO.class);
        eduOfficialCourseMapper.insert(course);
        return course.getId();
    }

    /**
     * 更新正式课程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOfficialCourse(EduOfficialCourseSaveReqVO updateReqVO) {
        validateOfficialCourseExists(updateReqVO.getId());
        EduOfficialCourseDO updateObj = BeanUtils.toBean(updateReqVO, EduOfficialCourseDO.class);
        eduOfficialCourseMapper.updateById(updateObj);
    }

    /**
     * 归档正式课程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveOfficialCourse(Long id) {
        validateOfficialCourseExists(id);
        EduOfficialCourseDO update = new EduOfficialCourseDO();
        update.setId(id);
        update.setStatus(1);
        eduOfficialCourseMapper.updateById(update);
    }

    /**
     * 获取正式课程详情。
     */
    @Override
    public EduOfficialCourseDO getOfficialCourse(Long id) {
        return eduOfficialCourseMapper.selectById(id);
    }

    /**
     * 分页查询正式课程。
     */
    @Override
    public PageResult<EduOfficialCourseDO> getOfficialCoursePage(EduOfficialCoursePageReqVO reqVO) {
        return eduOfficialCourseMapper.selectPage(reqVO);
    }

    /**
     * 校验正式课程存在。
     */
    private void validateOfficialCourseExists(Long id) {
        if (id == null || eduOfficialCourseMapper.selectById(id) == null) {
            throw exception(NOT_FOUND);
        }
    }

}
