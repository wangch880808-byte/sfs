package cn.nus.zknu.module.edu.service.aireview.support;

public interface HomeworkAiReviewStatusEnum {

    int QUEUED = 0;
    int PROCESSING = 1;
    int READY = 2;
    int FAILED = 3;
    int ACCEPTED = 4;
    int REJECTED = 5;
    int SKIPPED = 6;
}
