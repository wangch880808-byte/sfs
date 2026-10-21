package cn.nus.zknu.module.edu.enums.homework;

/**
 * 实训任务状态（edu_homework.status）。
 */
public interface EduHomeworkStatusEnum {

    int DRAFT = 0;
    int PUBLISHED = 1;
    /** 已截止：字典与前端保留此值，后端目前不写入，逾期按截止时间判断。 */
    int CLOSED = 2;
    int ARCHIVED = 3;
}
