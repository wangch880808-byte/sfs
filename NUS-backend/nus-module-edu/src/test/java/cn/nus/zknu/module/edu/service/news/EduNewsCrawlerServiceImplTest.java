package cn.nus.zknu.module.edu.service.news;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.framework.xss.core.clean.JsoupXssCleaner;
import cn.nus.zknu.framework.xss.core.clean.XssCleaner;
import cn.nus.zknu.module.edu.dal.dataobject.news.EduNewsArticleDO;
import cn.nus.zknu.module.edu.dal.mysql.news.EduNewsArticleMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EduNewsCrawlerServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private EduNewsArticleMapper newsArticleMapper;

    private ObjectProvider<XssCleaner> xssCleanerProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        xssCleanerProvider = mock(ObjectProvider.class);
        when(xssCleanerProvider.getIfAvailable(any(Supplier.class))).thenReturn(new JsoupXssCleaner());
    }

    @Test
    void crawlAllSourcesReturnsSuccessWhenEveryDocumentIsAvailable() {
        EduNewsCrawlerServiceImpl service = serviceWithDocuments(false, false);

        String result = service.crawlAllSources();

        assertEquals("crawl finished: inserted=4, updated=0, skipped=0, failed=0", result);
        verify(newsArticleMapper, org.mockito.Mockito.times(4)).insert(any(EduNewsArticleDO.class));
    }

    @Test
    void crawlAllSourcesFailsWhenListPagesAreUnavailable() {
        EduNewsCrawlerServiceImpl service = serviceWithDocuments(true, false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::crawlAllSources);

        assertTrue(exception.getMessage().contains("failed=8"));
    }

    @Test
    void crawlAllSourcesFailsWhenAnyArticleIsUnavailable() {
        EduNewsCrawlerServiceImpl service = serviceWithDocuments(false, true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::crawlAllSources);

        assertTrue(exception.getMessage().contains("inserted=2"));
        assertTrue(exception.getMessage().contains("failed=2"));
    }

    private EduNewsCrawlerServiceImpl serviceWithDocuments(boolean failLists, boolean failSecondArticle) {
        EduNewsCrawlerServiceImpl service = new EduNewsCrawlerServiceImpl(xssCleanerProvider) {
            @Override
            protected Document loadDocument(String url) {
                if (url.contains("list")) {
                    if (failLists) {
                        return null;
                    }
                    return Jsoup.parse("""
                            <ul class="news_list">
                              <li class="news"><span class="news_title"><a href="/first/page.htm">First</a></span></li>
                              <li class="news"><span class="news_title"><a href="/second/page.htm">Second</a></span></li>
                            </ul>
                            """, url);
                }
                if (failSecondArticle && url.contains("/second/")) {
                    return null;
                }
                return Jsoup.parse("""
                        <html><head><title>Campus News</title></head><body>
                          <h1 class="arti_title">Campus News</h1>
                          <div class="wp_articlecontent"><p>News content</p></div>
                        </body></html>
                        """, url);
            }
        };
        ReflectionTestUtils.setField(service, "newsArticleMapper", newsArticleMapper);
        return service;
    }
}
