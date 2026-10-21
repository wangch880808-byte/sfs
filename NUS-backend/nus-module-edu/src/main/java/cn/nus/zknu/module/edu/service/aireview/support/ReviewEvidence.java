package cn.nus.zknu.module.edu.service.aireview.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ReviewEvidence {

    private String submissionMode;

    private String transcript;

    private List<String> imageUrls = new ArrayList<>();

    private List<String> audioUrls = new ArrayList<>();

    private List<String> videoUrls = new ArrayList<>();

    private List<String> textUrls = new ArrayList<>();

    private List<String> riskFlags = new ArrayList<>();

    private Map<String, Object> payload;
}
