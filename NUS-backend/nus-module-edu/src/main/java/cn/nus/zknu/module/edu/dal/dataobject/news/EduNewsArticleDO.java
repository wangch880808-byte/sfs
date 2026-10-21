package cn.nus.zknu.module.edu.dal.dataobject.news;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 校园资讯文章 DO
 */
@TableName("edu_news_article")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduNewsArticleDO extends BaseDO {

    /**
     * 主键
     */
    private Long id;
    /**
     * 来源编码（zsxw / xlxy）
     */
    private String sourceCode;
    /**
     * 来源名称
     */
    private String sourceName;
    /**
     * 原文 URL
     */
    private String articleUrl;
    /**
     * URL md5 摘要（去重键）
     */
    private String urlMd5;
    /**
     * 标题
     */
    private String title;
    /**
     * 作者
     */
    private String author;
    /**
     * 发布日期
     */
    private LocalDate publishDate;
    /**
     * 原始元信息
     */
    private String rawMeta;
    /**
     * 正文摘要（内容 hash）
     */
    private String contentHash;
    /**
     * 正文纯文本
     */
    private String contentText;
    /**
     * 正文 HTML
     */
    private String contentHtml;
    /**
     * 首次抓取时间
     */
    private LocalDateTime firstCrawledAt;
    /**
     * 最近抓取时间
     */
    private LocalDateTime lastCrawledAt;

}
