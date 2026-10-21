package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterSaveReqVO;

/**
 * 学期管理 Service
 */
public interface EduSemesterService {

    /**
     * 获取当前学期信息
     */
    EduSemesterRespVO getCurrentSemester();

    /**
     * 保存当前学期信息
     */
    void saveCurrentSemester(EduSemesterSaveReqVO reqVO);

    /**
     * 设置学期自动更新模式；启用时立即按当前日期校准。
     */
    void setAutoEnabled(boolean autoEnabled);

    /**
     * 按当前日期自动更新学期。
     *
     * @return 任务执行结果
     */
    String autoUpdateCurrentSemester();
}
