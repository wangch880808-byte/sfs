package cn.nus.zknu.module.edu.service.semester;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduSemesterUsageDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import cn.nus.zknu.module.edu.dal.redis.RedisKeyConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_CURRENT_NOT_SET;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_NOT_CONFIGURED;

/**
 * 已配置的学期：当前学期（来自学期管理），加上班课关联里已经用过的学期。
 * 手工绑定班课、复制任务、学期收尾都只能从这里选，旧的自由文本学期仍然按原样显示。
 */
@Service
public class EduSemesterOptionService {

    public static final String UNSET_SEMESTER_NAME = "未设置";

    @Resource
    @Lazy
    private EduSemesterService semesterService;
    @Resource
    private EduClassCourseMapper classCourseMapper;

    /** 当前学期排在最前，其余按名称倒序（新学期在前）。 */
    @Cacheable(cacheNames = RedisKeyConstants.SEMESTER_OPTIONS)
    public List<EduSemesterOptionRespVO> listOptions() {
        EduSemesterRespVO current = currentSemester();
        String currentName = current == null ? null : StrUtil.trim(current.getSemesterName());
        List<EduSemesterOptionRespVO> options = new ArrayList<>();
        EduSemesterOptionRespVO currentOption = null;
        if (currentName != null) {
            currentOption = new EduSemesterOptionRespVO();
            currentOption.setSemesterName(currentName);
            currentOption.setSemesterStartDate(current.getSemesterStartDate());
            currentOption.setSemesterEndDate(current.getSemesterEndDate());
            currentOption.setCurrent(true);
            currentOption.setClassCourseCount(0);
            currentOption.setActiveClassCourseCount(0);
            options.add(currentOption);
        }
        List<EduSemesterOptionRespVO> history = new ArrayList<>();
        for (EduSemesterUsageDO usage : classCourseMapper.selectSemesterUsage()) {
            String name = StrUtil.trim(usage.getSemesterName());
            if (StrUtil.isBlank(name) || UNSET_SEMESTER_NAME.equals(name)) {
                continue;
            }
            if (currentOption != null && Objects.equals(name, currentName)) {
                currentOption.setClassCourseCount(nullToZero(usage.getLinkCount()));
                currentOption.setActiveClassCourseCount(nullToZero(usage.getActiveLinkCount()));
                continue;
            }
            EduSemesterOptionRespVO option = new EduSemesterOptionRespVO();
            option.setSemesterName(name);
            option.setSemesterStartDate(usage.getStartDate());
            option.setSemesterEndDate(usage.getEndDate());
            option.setCurrent(false);
            option.setClassCourseCount(nullToZero(usage.getLinkCount()));
            option.setActiveClassCourseCount(nullToZero(usage.getActiveLinkCount()));
            history.add(option);
        }
        history.sort(Comparator.comparing(EduSemesterOptionRespVO::getSemesterName).reversed());
        options.addAll(history);
        return options;
    }

    /**
     * 按名称取已配置的学期；名称为空时取当前学期。
     *
     * @throws cn.nus.zknu.framework.common.exception.ServiceException 当前学期未设置，或名称不在已配置的学期里
     */
    public EduSemesterOptionRespVO resolve(String semesterName) {
        String name = StrUtil.trim(semesterName);
        List<EduSemesterOptionRespVO> options = listOptions();
        if (StrUtil.isBlank(name)) {
            return options.stream().filter(option -> Boolean.TRUE.equals(option.getCurrent())).findFirst()
                    .orElseThrow(() -> exception(SEMESTER_CURRENT_NOT_SET));
        }
        return options.stream().filter(option -> Objects.equals(option.getSemesterName(), name)).findFirst()
                .orElseThrow(() -> exception(SEMESTER_NOT_CONFIGURED, name));
    }

    /** 当前学期名称；未设置时为空。 */
    public String currentSemesterName() {
        EduSemesterRespVO current = currentSemester();
        return current == null ? null : StrUtil.trim(current.getSemesterName());
    }

    private EduSemesterRespVO currentSemester() {
        EduSemesterRespVO semester = semesterService.getCurrentSemester();
        if (semester == null || StrUtil.isBlank(semester.getSemesterName())
                || UNSET_SEMESTER_NAME.equals(StrUtil.trim(semester.getSemesterName()))) {
            return null;
        }
        return semester;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
