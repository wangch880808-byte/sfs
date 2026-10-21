package cn.nus.zknu.module.edu.service.news;

/**
 * 校园资讯抓取 Service
 */
public interface EduNewsCrawlerService {

    /**
     * 抓取全部新闻来源
     *
     * @return 执行摘要
     */
    String crawlAllSources();

}
