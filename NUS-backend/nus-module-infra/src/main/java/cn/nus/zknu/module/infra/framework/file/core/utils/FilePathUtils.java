package cn.nus.zknu.module.infra.framework.file.core.utils;

import cn.hutool.core.util.StrUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件路径工具类
 */
public final class FilePathUtils {

    private FilePathUtils() {
    }

    public static String normalizeRelativePath(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("文件路径不能包含空字符");
        }
        String normalizedInput = path.replace('\\', '/').trim();
        if (normalizedInput.startsWith("/") || normalizedInput.startsWith("./")) {
            throw new IllegalArgumentException("文件路径必须为相对路径");
        }
        if (normalizedInput.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("文件路径不能包含盘符");
        }

        Path normalizedPath = Paths.get(normalizedInput).normalize();
        String normalized = normalizedPath.toString().replace('\\', '/');
        if (StrUtil.isBlank(normalized) || ".".equals(normalized)
                || "..".equals(normalized) || normalized.startsWith("../")) {
            throw new IllegalArgumentException("文件路径不能越界");
        }
        return normalized;
    }
}
