package cn.nus.zknu.module.edu.service.homework;

/**
 * 学生提交附件的上限（来自 infra_config 的 edu.homework.attachment.*，读取失败或越界时用默认值）。
 *
 * @param maxCount   每次提交最多几个附件
 * @param maxFileMb  单个附件上限（MB）
 * @param maxTotalMb 一次提交的附件合计上限（MB）
 */
public record HomeworkAttachmentLimits(int maxCount, int maxFileMb, int maxTotalMb) {

    private static final long MB = 1024L * 1024;

    public long maxFileBytes() {
        return maxFileMb * MB;
    }

    public long maxTotalBytes() {
        return maxTotalMb * MB;
    }

}
