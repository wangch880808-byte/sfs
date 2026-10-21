package cn.nus.zknu.module.edu.service.courseresource;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourcePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/**
 * 课程资源 Service 实现类
 */
@Service
@Slf4j
public class EduCourseResourceServiceImpl implements EduCourseResourceService {

    /**
     * 文件类型短码白名单
     */
    private static final List<String> ALLOWED_FILE_TYPE_CODES = List.of(
            "pdf", "word", "ppt", "excel", "archive",
            "text", "image", "audio", "video", "other"
    );

    /**
     * 兼容旧版 MIME 到短码的映射
     */
    private static final Map<String, String> MIME_TYPE_CODE_MAP = new LinkedHashMap<>();

    static {
        MIME_TYPE_CODE_MAP.put("application/pdf", "pdf");
        MIME_TYPE_CODE_MAP.put("application/msword", "word");
        MIME_TYPE_CODE_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "word");
        MIME_TYPE_CODE_MAP.put("application/vnd.ms-excel", "excel");
        MIME_TYPE_CODE_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel");
        MIME_TYPE_CODE_MAP.put("application/vnd.ms-powerpoint", "ppt");
        MIME_TYPE_CODE_MAP.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "ppt");
        MIME_TYPE_CODE_MAP.put("application/zip", "archive");
        MIME_TYPE_CODE_MAP.put("application/x-rar-compressed", "archive");
        MIME_TYPE_CODE_MAP.put("text/plain", "text");
        MIME_TYPE_CODE_MAP.put("text/csv", "text");
        MIME_TYPE_CODE_MAP.put("image/jpeg", "image");
        MIME_TYPE_CODE_MAP.put("image/png", "image");
        MIME_TYPE_CODE_MAP.put("image/gif", "image");
        MIME_TYPE_CODE_MAP.put("audio/mpeg", "audio");
        MIME_TYPE_CODE_MAP.put("audio/wav", "audio");
        MIME_TYPE_CODE_MAP.put("audio/ogg", "audio");
        MIME_TYPE_CODE_MAP.put("audio/mp4", "audio");
        MIME_TYPE_CODE_MAP.put("audio/x-m4a", "audio");
        MIME_TYPE_CODE_MAP.put("video/mp4", "video");
        MIME_TYPE_CODE_MAP.put("video/avi", "video");
        MIME_TYPE_CODE_MAP.put("video/x-msvideo", "video");
        MIME_TYPE_CODE_MAP.put("video/quicktime", "video");
        MIME_TYPE_CODE_MAP.put("video/x-matroska", "video");
        MIME_TYPE_CODE_MAP.put("video/webm", "video");
        MIME_TYPE_CODE_MAP.put("video/x-m4v", "video");
        MIME_TYPE_CODE_MAP.put("video/mpeg", "video");
        MIME_TYPE_CODE_MAP.put("video/3gpp", "video");
    }

    /** 文件大小上限：500MB */
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    /** 预览文本提取时读取的文件大小上限：50MB */
    static final long PREVIEW_MAX_FILE_SIZE = 50L * 1024 * 1024;

    @Resource
    private EduCourseResourceMapper eduCourseResourceMapper;
    @Resource
    private FileApi fileApi;

    @Override
    public Long createResource(EduCourseResourceSaveReqVO createReqVO) {
        String normalizedFileType = normalizeFileType(createReqVO.getFileType());
        // 文件类型白名单校验
        if (!ALLOWED_FILE_TYPE_CODES.contains(normalizedFileType)) {
            throw exception(COURSE_RESOURCE_FILE_TYPE_NOT_ALLOWED);
        }
        // 资源文件必须是本人上传到当前课程资源目录的已登记平台文件
        FileContentRespDTO file = validateResourceFile(createReqVO.getCourseId(), createReqVO.getFileUrl());
        // 文件大小以平台记录为准，不信任客户端传入值
        long fileSize = file.getSize() != null ? file.getSize() : 0L;
        if (fileSize > MAX_FILE_SIZE) {
            throw exception(COURSE_RESOURCE_FILE_SIZE_EXCEED);
        }
        EduCourseResourceDO resource = BeanUtils.toBean(createReqVO, EduCourseResourceDO.class);
        resource.setFileUrl(StrUtil.blankToDefault(file.getUrl(), createReqVO.getFileUrl()));
        resource.setFileSize(fileSize);
        resource.setFileType(normalizedFileType);
        eduCourseResourceMapper.insert(resource);
        return resource.getId();
    }

    @Override
    public void deleteResource(Long id) {
        validateResourceExists(id);
        eduCourseResourceMapper.deleteById(id);
    }

    @Override
    public EduCourseResourceDO getResource(Long id) {
        return eduCourseResourceMapper.selectById(id);
    }

    @Override
    public PageResult<EduCourseResourceDO> getResourcePage(EduCourseResourcePageReqVO reqVO) {
        return eduCourseResourceMapper.selectPage(reqVO, new LambdaQueryWrapperX<EduCourseResourceDO>()
                .eqIfPresent(EduCourseResourceDO::getCourseId, reqVO.getCourseId())
                .likeIfPresent(EduCourseResourceDO::getResourceName, reqVO.getResourceName())
                .eqIfPresent(EduCourseResourceDO::getFileType, reqVO.getFileType())
                .orderByDesc(EduCourseResourceDO::getId));
    }

    @Override
    public String getResourcePreviewText(Long id) {
        EduCourseResourceDO resource = validateResourceExists(id);
        if (resource == null || resource.getFileUrl() == null || resource.getFileUrl().isBlank()) {
            return "";
        }
        String normalizedType = normalizeFileType(resource.getFileType());
        if (!"word".equals(normalizedType) && !"ppt".equals(normalizedType)) {
            return "";
        }
        try {
            // 只读取已登记的平台文件，不访问任意外部地址
            byte[] content = fileApi.getFileContent(resource.getFileUrl(), PREVIEW_MAX_FILE_SIZE).getContent();
            if (content == null || content.length == 0) {
                return "";
            }
            String lowerUrl = resource.getFileUrl().toLowerCase();
            if ("word".equals(normalizedType) && lowerUrl.contains(".docx")) {
                return extractDocxText(content);
            }
            if ("ppt".equals(normalizedType) && lowerUrl.contains(".pptx")) {
                return extractPptxText(content);
            }
            return "";
        } catch (Exception ex) {
            log.warn("[getResourcePreviewText][id({}) fileUrl({}) 预览提取失败]", id, resource.getFileUrl(), ex);
            return "";
        }
    }

    private FileContentRespDTO validateResourceFile(Long courseId, String fileUrl) {
        FileContentRespDTO file;
        try {
            file = fileApi.getFile(fileUrl);
        } catch (Exception ex) {
            throw exception(COURSE_RESOURCE_FILE_INVALID, "文件不是已登记的平台文件");
        }
        Long loginUserId = getLoginUserId();
        if (loginUserId == null || !String.valueOf(loginUserId).equals(file.getCreator())) {
            throw exception(COURSE_RESOURCE_FILE_INVALID, "只能使用本人上传的文件");
        }
        String expectedPathPrefix = "edu/course-resource/" + courseId + "/";
        if (StrUtil.isBlank(file.getPath()) || !file.getPath().startsWith(expectedPathPrefix)) {
            throw exception(COURSE_RESOURCE_FILE_INVALID, "文件不属于当前课程的资源目录");
        }
        return file;
    }

    private EduCourseResourceDO validateResourceExists(Long id) {
        if (id == null) {
            return null;
        }
        EduCourseResourceDO resource = eduCourseResourceMapper.selectById(id);
        if (resource == null) {
            throw exception(COURSE_RESOURCE_NOT_EXISTS);
        }
        return resource;
    }

    private String normalizeFileType(String rawFileType) {
        String value = rawFileType == null ? "" : rawFileType.trim().toLowerCase();
        if (value.isEmpty()) {
            return "";
        }
        if (ALLOWED_FILE_TYPE_CODES.contains(value)) {
            return value;
        }
        if ("powerpoint".equals(value)) {
            return "ppt";
        }
        String direct = MIME_TYPE_CODE_MAP.get(value);
        if (direct != null) {
            return direct;
        }
        if (value.startsWith("video/")) {
            return "video";
        }
        if (value.startsWith("audio/")) {
            return "audio";
        }
        if (value.startsWith("image/")) {
            return "image";
        }
        if (value.contains("pdf")) {
            return "pdf";
        }
        if (value.contains("word") || value.contains("document")) {
            return "word";
        }
        if (value.contains("presentation") || value.contains("powerpoint")) {
            return "ppt";
        }
        if (value.contains("sheet") || value.contains("excel")) {
            return "excel";
        }
        if (value.startsWith("text/") || value.contains("csv") || value.contains("json")) {
            return "text";
        }
        if (value.contains("zip") || value.contains("rar") || value.contains("7z") || value.contains("tar")) {
            return "archive";
        }
        return "other";
    }

    private String extractDocxText(byte[] content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.joining("\n\n"));
        }
    }

    private String extractPptxText(byte[] content) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            StringBuilder builder = new StringBuilder();
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                List<String> texts = slide.getShapes().stream()
                        .filter(shape -> shape instanceof XSLFTextShape)
                        .map(shape -> ((XSLFTextShape) shape).getText())
                        .map(text -> text == null ? "" : text.trim())
                        .filter(text -> !text.isEmpty())
                        .toList();
                if (texts.isEmpty()) {
                    index++;
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("第 ").append(index).append(" 页").append("\n\n");
                builder.append(String.join("\n", texts));
                index++;
            }
            return builder.toString();
        }
    }

}
