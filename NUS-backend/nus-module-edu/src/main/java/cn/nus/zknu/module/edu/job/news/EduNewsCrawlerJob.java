package cn.nus.zknu.module.edu.job.news;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.edu.service.news.EduNewsCrawlerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校园资讯抓取 Job（由任务中心触发，不使用 @Scheduled）
 */
@Component
@Slf4j
public class EduNewsCrawlerJob implements JobHandler {

    @Resource
    private EduNewsCrawlerService newsCrawlerService;

    @Override
    public String execute(String param) {
        String result = newsCrawlerService.crawlAllSources();
        log.info("[execute][校园资讯抓取完成] {}", result);
        return result;
    }

}
