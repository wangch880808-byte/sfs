package cn.nus.zknu.module.edu.enums.news;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 校园资讯来源枚举（写死）
 */
@Getter
@AllArgsConstructor
public enum EduNewsSourceEnum {

    ZSXW("zsxw", "周师要闻", "https://www.zknu.edu.cn/zsxw/list.htm"),
    XLXY("xlxy", "特别关注", "https://www.zknu.edu.cn/xlxy/list.htm");

    /**
     * 来源编码
     */
    private final String code;
    /**
     * 来源名称
     */
    private final String name;
    /**
     * 列表页 URL
     */
    private final String listUrl;

}
