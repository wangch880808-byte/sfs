package cn.nus.zknu.module.edu.service.news;

import cn.nus.zknu.framework.xss.core.clean.JsoupXssCleaner;
import cn.nus.zknu.framework.xss.core.clean.XssCleaner;
import cn.nus.zknu.module.edu.dal.dataobject.news.EduNewsArticleDO;
import cn.nus.zknu.module.edu.dal.mysql.news.EduNewsArticleMapper;
import cn.nus.zknu.module.edu.enums.news.EduNewsSourceEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校园资讯抓取 Service 实现
 */
@Service
@Slf4j
public class EduNewsCrawlerServiceImpl implements EduNewsCrawlerService {

    private static final int MAX_LIST_PAGES = 3;
    private static final int REQUEST_TIMEOUT_MS = 15_000;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_AUTHOR_LENGTH = 128;
    private static final int MAX_URL_LENGTH = 1024;
    private static final int MAX_RAW_META_LENGTH = 1000;
    private static final int MAX_CONTENT_TEXT_LENGTH = 60_000;
    private static final int MAX_CONTENT_HTML_LENGTH = 60_000;

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4}[年\\-/.]\\d{1,2}[月\\-/.]\\d{1,2}日?)");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";

    @Resource
    private EduNewsArticleMapper newsArticleMapper;
    private final XssCleaner xssCleaner;

    public EduNewsCrawlerServiceImpl(ObjectProvider<XssCleaner> xssCleanerProvider) {
        this.xssCleaner = xssCleanerProvider.getIfAvailable(JsoupXssCleaner::new);
    }

    @Override
    public String crawlAllSources() {
        CrawlStats total = new CrawlStats();
        for (EduNewsSourceEnum source : EduNewsSourceEnum.values()) {
            CrawlStats sourceStats = crawlOneSource(source);
            total.merge(sourceStats);
            log.info("[crawlAllSources][source={}, inserted={}, updated={}, skipped={}, failed={}]",
                    source.getCode(), sourceStats.inserted, sourceStats.updated,
                    sourceStats.skipped, sourceStats.failed);
        }
        if (total.hasFailures()) {
            throw new IllegalStateException("campus news crawl completed with failures: " + total.summary());
        }
        return total.summary();
    }

    private CrawlStats crawlOneSource(EduNewsSourceEnum source) {
        CrawlStats stats = new CrawlStats();
        Map<String, ListItem> linkMap = collectListItems(source, stats);
        if (linkMap.isEmpty()) {
            stats.failed++;
            log.warn("[crawlOneSource][source={} 未发现任何资讯链接]", source.getCode());
        }
        for (ListItem item : linkMap.values()) {
            try {
                crawlDetailAndUpsert(source, item, stats);
            } catch (Exception ex) {
                stats.failed++;
                log.warn("[crawlOneSource][source={}, articleUrl={} 抓取失败]", source.getCode(), item.articleUrl, ex);
            }
        }
        return stats;
    }

    private Map<String, ListItem> collectListItems(EduNewsSourceEnum source, CrawlStats stats) {
        Map<String, ListItem> linkMap = new LinkedHashMap<>();
        for (int pageNo = 1; pageNo <= MAX_LIST_PAGES; pageNo++) {
            String listUrl = buildListPageUrl(source.getListUrl(), pageNo);
            Document document = loadDocument(listUrl);
            if (document == null) {
                stats.failed++;
                continue;
            }
            Elements items = document.select("ul.news_list li.news");
            for (Element item : items) {
                Element linkElement = item.selectFirst(".news_title a[href]");
                if (linkElement == null) {
                    continue;
                }
                String articleUrl = absolutizeUrl(listUrl, linkElement.attr("href"));
                if (!StringUtils.hasText(articleUrl) || !articleUrl.contains("/page.htm")) {
                    continue;
                }
                String title = normalizeText(firstNonBlank(linkElement.attr("title"), linkElement.text()));
                String publishDateText = normalizeText(item.selectFirst(".news_meta") != null
                        ? item.selectFirst(".news_meta").text() : null);
                LocalDate publishDate = parseDate(publishDateText);
                linkMap.putIfAbsent(articleUrl, new ListItem(articleUrl, title, publishDate, publishDateText));
            }
        }
        return linkMap;
    }

    private void crawlDetailAndUpsert(EduNewsSourceEnum source, ListItem item, CrawlStats stats) {
        Document document = loadDocument(item.articleUrl);
        if (document == null) {
            stats.failed++;
            return;
        }

        String title = normalizeText(textOf(document.selectFirst("h1.arti_title")));
        if (!StringUtils.hasText(title)) {
            title = normalizeText(document.title());
        }
        if (!StringUtils.hasText(title)) {
            title = item.listTitle;
        }

        String author = normalizeText(textOf(document.selectFirst(".arti_metas .arti_publisher")));
        if (author.startsWith("作者：")) {
            author = author.substring("作者：".length()).trim();
        }

        LocalDate publishDate = item.listPublishDate;
        if (publishDate == null) {
            String publishText = normalizeText(textOf(document.selectFirst(".arti_metas .arti_update")));
            publishDate = parseDate(publishText);
        }

        Element contentElement = firstElement(document,
                ".wp_articlecontent",
                ".entry .read",
                ".entry",
                "article",
                ".article");
        if (contentElement == null) {
            stats.failed++;
            return;
        }

        Element normalizedContent = contentElement.clone();
        normalizedContent.select("script,style").remove();
        rewriteRelativeUrl(normalizedContent, document.location());

        String sanitizedContentHtml = xssCleaner.clean(normalizedContent.html());
        String contentHtml = limit(sanitizedContentHtml, MAX_CONTENT_HTML_LENGTH);
        String contentText = limit(Jsoup.parseBodyFragment(sanitizedContentHtml).text(), MAX_CONTENT_TEXT_LENGTH);
        if (!StringUtils.hasText(contentHtml) && !StringUtils.hasText(contentText)) {
            stats.failed++;
            return;
        }

        String articleUrl = limit(item.articleUrl, MAX_URL_LENGTH);
        String urlMd5 = md5(articleUrl);
        String contentHash = md5(firstNonBlank(contentText, contentHtml));
        String rawMeta = buildRawMeta(item, document);

        EduNewsArticleDO existing = newsArticleMapper.selectBySourceCodeAndUrlMd5(source.getCode(), urlMd5);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            EduNewsArticleDO insertObj = new EduNewsArticleDO();
            fillArticle(insertObj, source, articleUrl, urlMd5, title, author, publishDate,
                    rawMeta, contentHash, contentText, contentHtml);
            insertObj.setFirstCrawledAt(now);
            insertObj.setLastCrawledAt(now);
            newsArticleMapper.insert(insertObj);
            stats.inserted++;
            return;
        }

        boolean changed = !Objects.equals(existing.getContentHash(), contentHash)
                || !Objects.equals(existing.getTitle(), title)
                || !Objects.equals(existing.getAuthor(), author)
                || !Objects.equals(existing.getPublishDate(), publishDate);
        fillArticle(existing, source, articleUrl, urlMd5, title, author, publishDate,
                rawMeta, contentHash, contentText, contentHtml);
        existing.setLastCrawledAt(now);
        newsArticleMapper.updateById(existing);
        if (changed) {
            stats.updated++;
        } else {
            stats.skipped++;
        }
    }

    private void fillArticle(EduNewsArticleDO article,
                             EduNewsSourceEnum source,
                             String articleUrl,
                             String urlMd5,
                             String title,
                             String author,
                             LocalDate publishDate,
                             String rawMeta,
                             String contentHash,
                             String contentText,
                             String contentHtml) {
        article.setSourceCode(source.getCode());
        article.setSourceName(source.getName());
        article.setArticleUrl(articleUrl);
        article.setUrlMd5(urlMd5);
        article.setTitle(limit(title, MAX_TITLE_LENGTH));
        article.setAuthor(limit(author, MAX_AUTHOR_LENGTH));
        article.setPublishDate(publishDate);
        article.setRawMeta(limit(rawMeta, MAX_RAW_META_LENGTH));
        article.setContentHash(contentHash);
        article.setContentText(contentText);
        article.setContentHtml(contentHtml);
    }

    private String buildRawMeta(ListItem item, Document document) {
        String metaText = normalizeText(textOf(document.selectFirst(".arti_metas")));
        return String.format("listDate=%s;detailMeta=%s",
                firstNonBlank(item.listPublishDateText, ""),
                firstNonBlank(metaText, ""));
    }

    protected Document loadDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .get();
        } catch (Exception ex) {
            log.warn("[loadDocument][url={} 抓取失败]", url, ex);
            return null;
        }
    }

    private String buildListPageUrl(String baseUrl, int pageNo) {
        if (pageNo <= 1) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/list.htm")) {
            return baseUrl.replace("/list.htm", "/list" + pageNo + ".htm");
        }
        if (baseUrl.endsWith("list.htm")) {
            return baseUrl.replace("list.htm", "list" + pageNo + ".htm");
        }
        return baseUrl;
    }

    private void rewriteRelativeUrl(Element root, String baseUrl) {
        root.select("img[src]").forEach(img -> {
            String abs = absolutizeUrl(baseUrl, img.attr("src"));
            if (StringUtils.hasText(abs)) {
                img.attr("src", abs);
            }
        });
        root.select("a[href]").forEach(anchor -> {
            String abs = absolutizeUrl(baseUrl, anchor.attr("href"));
            if (StringUtils.hasText(abs)) {
                anchor.attr("href", abs);
            }
        });
    }

    private LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String normalized = matcher.group(1)
                .replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("/", "-")
                .replace(".", "-");
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Element firstElement(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private String textOf(Element element) {
        return element == null ? null : element.text();
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String absolutizeUrl(String baseUrl, String href) {
        if (!StringUtils.hasText(href)) {
            return "";
        }
        String raw = href.trim();
        if (raw.startsWith("//")) {
            return "https:" + raw;
        }
        try {
            String resolved = URI.create(baseUrl).resolve(raw).toString();
            int fragmentIndex = resolved.indexOf('#');
            return fragmentIndex >= 0 ? resolved.substring(0, fragmentIndex) : resolved;
        } catch (Exception ex) {
            return "";
        }
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] md5 = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : md5) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("md5 algorithm not found", ex);
        }
    }

    private static class ListItem {
        private final String articleUrl;
        private final String listTitle;
        private final LocalDate listPublishDate;
        private final String listPublishDateText;

        private ListItem(String articleUrl, String listTitle, LocalDate listPublishDate, String listPublishDateText) {
            this.articleUrl = articleUrl;
            this.listTitle = listTitle;
            this.listPublishDate = listPublishDate;
            this.listPublishDateText = listPublishDateText;
        }
    }

    private static class CrawlStats {
        private int inserted;
        private int updated;
        private int skipped;
        private int failed;

        private void merge(CrawlStats other) {
            this.inserted += other.inserted;
            this.updated += other.updated;
            this.skipped += other.skipped;
            this.failed += other.failed;
        }

        private String summary() {
            return String.format("crawl finished: inserted=%d, updated=%d, skipped=%d, failed=%d",
                    inserted, updated, skipped, failed);
        }

        private boolean hasFailures() {
            return failed > 0;
        }
    }

}
