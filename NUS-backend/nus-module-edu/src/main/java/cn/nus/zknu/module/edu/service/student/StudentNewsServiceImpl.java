package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.xss.core.clean.JsoupXssCleaner;
import cn.nus.zknu.framework.xss.core.clean.XssCleaner;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsSourceRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.news.EduNewsArticleDO;
import cn.nus.zknu.module.edu.dal.mysql.news.EduNewsArticleMapper;
import cn.nus.zknu.module.edu.enums.news.EduNewsSourceEnum;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.NEWS_NOT_EXISTS;

/**
 * 学生校园资讯 Service 实现
 */
@Service
public class StudentNewsServiceImpl implements StudentNewsService {

    private static final int IMAGE_MAX_SIZE = 5;
    private static final int REQUEST_TIMEOUT_MS = 12_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";

    @Resource
    private EduNewsArticleMapper newsArticleMapper;
    private final XssCleaner xssCleaner;

    public StudentNewsServiceImpl(ObjectProvider<XssCleaner> xssCleanerProvider) {
        this.xssCleaner = xssCleanerProvider.getIfAvailable(JsoupXssCleaner::new);
    }

    @Override
    public List<AppStudentNewsSourceRespVO> getNewsSources() {
        return Arrays.stream(EduNewsSourceEnum.values()).map(item -> {
            AppStudentNewsSourceRespVO respVO = new AppStudentNewsSourceRespVO();
            respVO.setSourceCode(item.getCode());
            respVO.setSourceName(item.getName());
            return respVO;
        }).toList();
    }

    @Override
    public PageResult<AppStudentNewsRespVO> getNewsPage(AppStudentNewsPageReqVO reqVO) {
        PageResult<EduNewsArticleDO> pageResult = newsArticleMapper.selectPage(reqVO);
        List<AppStudentNewsRespVO> list = pageResult.getList().stream()
                .map(this::convertNewsItem)
                .toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public AppStudentNewsDetailRespVO getNews(Long id) {
        EduNewsArticleDO newsArticle = newsArticleMapper.selectById(id);
        if (newsArticle == null) {
            throw exception(NEWS_NOT_EXISTS);
        }
        AppStudentNewsDetailRespVO respVO = BeanUtils.toBean(newsArticle, AppStudentNewsDetailRespVO.class);
        respVO.setContentHtml(sanitizeHtml(respVO.getContentHtml()));
        return respVO;
    }

    private AppStudentNewsRespVO convertNewsItem(EduNewsArticleDO newsArticle) {
        AppStudentNewsRespVO respVO = BeanUtils.toBean(newsArticle, AppStudentNewsRespVO.class);
        respVO.setImageUrls(extractImageUrls(sanitizeHtml(newsArticle.getContentHtml()), newsArticle.getArticleUrl()));
        return respVO;
    }

    private String sanitizeHtml(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return contentHtml;
        }
        return xssCleaner.clean(contentHtml);
    }

    private List<String> extractImageUrls(String contentHtml, String articleUrl) {
        List<String> imageUrls = parseImageUrlsByHtml(contentHtml);
        if (!imageUrls.isEmpty()) {
            return imageUrls;
        }
        return parseImageUrlsByArticleUrl(articleUrl);
    }

    private List<String> parseImageUrlsByHtml(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return Collections.emptyList();
        }
        return Jsoup.parseBodyFragment(contentHtml)
                .select("img[src]")
                .stream()
                .map(item -> item.attr("src"))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(IMAGE_MAX_SIZE)
                .toList();
    }

    private List<String> parseImageUrlsByArticleUrl(String articleUrl) {
        if (!StringUtils.hasText(articleUrl)) {
            return Collections.emptyList();
        }
        try {
            Document document = Jsoup.connect(articleUrl)
                    .userAgent(USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .get();
            return document.select(".wp_articlecontent img[src], .entry img[src], article img[src]")
                    .stream()
                    .map(item -> {
                        String absSrc = item.absUrl("src");
                        if (StringUtils.hasText(absSrc)) {
                            return absSrc;
                        }
                        return item.attr("src");
                    })
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(IMAGE_MAX_SIZE)
                    .toList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

}
