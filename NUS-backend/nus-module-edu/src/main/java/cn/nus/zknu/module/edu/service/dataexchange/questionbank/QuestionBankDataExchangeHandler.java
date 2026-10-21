package cn.nus.zknu.module.edu.service.dataexchange.questionbank;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import jakarta.annotation.Resource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID;

@Component
public class QuestionBankDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    public static final String BIZ_TYPE = "QUESTION_BANK";
    public static final String VERSION = "V2";
    private static final String SHEET_NAME = "题库数据";
    private static final List<String> HEADERS = List.of(
            "一级能力指标*", "二级能力指标*", "任务等级*", "任务标题*", "任务要求*");
    private static final Map<String, Integer> LEVEL_MAP = Map.ofEntries(
            Map.entry("专业", 0), Map.entry("初级", 1), Map.entry("中级", 2), Map.entry("高级", 3),
            Map.entry("0", 0), Map.entry("1", 1), Map.entry("2", 2), Map.entry("3", 3));
    private static final Map<Integer, String> LEVEL_NAMES = Map.of(0, "专业", 1, "初级", 2, "中级", 3, "高级");
    private static final Pattern INDICATOR_OPTION_PATTERN = Pattern.compile("^(.*?) \\[ID:(\\d+)]$");

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private CapacityIndicatorApi capacityIndicatorApi;
    @Resource
    private EduQuestionBankMapper questionBankMapper;

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public String getTemplateVersion() {
        return VERSION;
    }

    @Override
    public String getFileName() {
        return "题库导入模板_V2.xlsx";
    }

    @Override
    public void validatePermission() {
        if (!securityFrameworkService.hasPermission("edu:question-bank:manage")) {
            throw exception(FORBIDDEN);
        }
    }

    @Override
    public byte[] generate() {
        IndicatorIndex indicators = loadIndicators();
        List<CapacityIndicatorRespDTO> firstIndicators = indicators.active().stream()
                .filter(item -> Objects.equals(item.getIndicatorLevel(), 1))
                .toList();
        Map<String, List<String>> secondOptions = new LinkedHashMap<>();
        for (CapacityIndicatorRespDTO first : firstIndicators) {
            List<String> children = indicators.active().stream()
                    .filter(item -> Objects.equals(item.getIndicatorLevel(), 2)
                            && Objects.equals(item.getParentIndicatorId(), first.getId()))
                    .map(this::indicatorOption)
                    .toList();
            secondOptions.put(indicatorOption(first), children);
        }
        Map<String, List<String>> dropdownOptions = new LinkedHashMap<>();
        dropdownOptions.put("一级能力指标", firstIndicators.stream().map(this::indicatorOption).toList());
        dropdownOptions.put("任务等级", List.of("专业", "初级", "中级", "高级"));
        return EduXlsxWorkbookUtils.createTemplate(BIZ_TYPE, VERSION, List.of(
                        "带 * 的列为必填项；请勿修改工作表名称和表头。",
                        "一级、二级能力指标请使用下拉框选择；二级选项会根据同一行的一级指标联动更新。",
                        "任务等级请使用下拉框选择；兼容旧模板中的 0、1、2、3。",
                        "与当前教师题库中完全相同的记录会标记为跳过，不会重复写入。"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec(SHEET_NAME, HEADERS, Set.of(),
                        List.of("专业核心能力", "Java 应用开发", "中级", "完成学生管理模块",
                                "实现增删改查、参数校验和必要的单元测试。"))),
                List.of(new EduXlsxWorkbookUtils.DictionaryEntry(
                        "任务等级", "专业 / 初级 / 中级 / 高级", "旧模板也支持 0 / 1 / 2 / 3")),
                dropdownOptions,
                List.of(new EduXlsxWorkbookUtils.DependentDropdownSpec(
                        "一级能力指标", "二级能力指标", secondOptions)));
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            List<EduXlsxWorkbookUtils.RowData> sourceRows = EduXlsxWorkbookUtils.readSheet(
                    workbook, SHEET_NAME, HEADERS);
            IndicatorIndex indicators = loadIndicators();
            Map<String, EduQuestionBankDO> existing = loadExisting(getLoginUserId());
            List<Candidate> candidates = sourceRows.stream()
                    .map(row -> parse(row, indicators, existing))
                    .collect(Collectors.toCollection(ArrayList::new));
            Map<String, List<Candidate>> fileGroups = candidates.stream()
                    .filter(candidate -> candidate.businessKey() != null)
                    .collect(Collectors.groupingBy(Candidate::businessKey, LinkedHashMap::new, Collectors.toList()));
            fileGroups.values().stream().filter(group -> group.size() > 1)
                    .forEach(group -> group.forEach(candidate -> candidate.preview().getErrors().add("文件内题目重复")));
            return candidates.stream().map(Candidate::preview).toList();
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        int inserted = 0;
        int skipped = 0;
        Long teacherId = getLoginUserId();
        Map<String, EduQuestionBankDO> existing = loadExisting(teacherId);
        for (EduImportBatchItemDO item : validItems) {
            QuestionBankImportRow row = JSONUtil.toBean(item.getNormalizedData(), QuestionBankImportRow.class);
            String businessKey = key(row.getIndicatorFirstId(), row.getIndicatorSecondId(), row.getHomeworkLevel(),
                    row.getTitle(), row.getRequirement());
            EduQuestionBankDO current = existing.get(businessKey);
            if (!EduImportSnapshotUtils.matches(item.getBeforeData(), snapshot(current))) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            CapacityIndicatorRespDTO first = capacityIndicatorApi.getIndicator(row.getIndicatorFirstId());
            CapacityIndicatorRespDTO second = capacityIndicatorApi.getIndicator(row.getIndicatorSecondId());
            if (!validIndicatorPair(first, second)) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            EduQuestionBankDO entity = new EduQuestionBankDO();
            entity.setTeacherId(teacherId);
            entity.setIndicatorFirstId(row.getIndicatorFirstId());
            entity.setIndicatorSecondId(row.getIndicatorSecondId());
            entity.setHomeworkLevel(row.getHomeworkLevel());
            entity.setTitle(row.getTitle());
            entity.setRequirement(row.getRequirement());
            questionBankMapper.insert(entity);
            existing.put(businessKey, entity);
            inserted++;
        }
        return new EduImportCommitResult(inserted, 0, skipped);
    }

    private Candidate parse(EduXlsxWorkbookUtils.RowData source, IndicatorIndex indicators,
                            Map<String, EduQuestionBankDO> existing) {
        List<String> errors = new ArrayList<>();
        String firstName = nullable(source.get("一级能力指标*"));
        String secondName = nullable(source.get("二级能力指标*"));
        String levelName = nullable(source.get("任务等级*"));
        String title = nullable(source.get("任务标题*"));
        String requirement = nullable(source.get("任务要求*"));
        IndicatorResolution firstResolution = firstName == null ? IndicatorResolution.empty()
                : resolveFirstIndicator(firstName, indicators);
        CapacityIndicatorRespDTO first = firstResolution.indicator();
        IndicatorResolution secondResolution = first == null || secondName == null ? IndicatorResolution.empty()
                : resolveSecondIndicator(secondName, first.getId(), indicators);
        CapacityIndicatorRespDTO second = secondResolution.indicator();
        Integer level = levelName == null ? null : LEVEL_MAP.get(levelName);
        if (firstName == null) errors.add("一级能力指标不能为空");
        else if (first == null) errors.add(firstResolution.error());
        if (secondName == null) errors.add("二级能力指标不能为空");
        else if (first == null) errors.add("二级能力指标无法校验，请先修正一级能力指标");
        else if (second == null) errors.add(secondResolution.error());
        if (levelName == null) errors.add("任务等级不能为空");
        else if (level == null) errors.add("任务等级只支持专业、初级、中级、高级或 0 至 3");
        if (title == null) errors.add("任务标题不能为空");
        if (requirement == null) errors.add("任务要求不能为空");

        QuestionBankImportRow row = new QuestionBankImportRow();
        row.setIndicatorFirstId(first == null ? null : first.getId());
        row.setIndicatorSecondId(second == null ? null : second.getId());
        row.setIndicatorFirstName(first == null ? firstName : first.getIndicatorName());
        row.setIndicatorSecondName(second == null ? secondName : second.getIndicatorName());
        row.setHomeworkLevel(level);
        row.setLevelName(level == null ? levelName : LEVEL_NAMES.get(level));
        row.setTitle(title);
        row.setRequirement(requirement);
        String businessKey = first == null || second == null || level == null || title == null || requirement == null
                ? null : key(first.getId(), second.getId(), level, title, requirement);
        EduQuestionBankDO current = businessKey == null ? null : existing.get(businessKey);
        EduImportRowActionEnum action = current == null ? EduImportRowActionEnum.INSERT : EduImportRowActionEnum.SKIP;
        Map<String, Object> after = snapshot(row, getLoginUserId());
        EduImportPreviewRow preview = EduImportPreviewRow.builder()
                .sheetName(SHEET_NAME)
                .rowNumber(source.rowNumber())
                .businessKey(businessKey == null ? title : title + " / " + row.getIndicatorSecondName())
                .normalizedData(JSONUtil.toJsonStr(row))
                .action(action)
                .errors(errors)
                .beforeData(current == null ? null : JSONUtil.toJsonStr(snapshot(current)))
                .afterData(JSONUtil.toJsonStr(after))
                .build();
        return new Candidate(businessKey, preview);
    }

    private IndicatorIndex loadIndicators() {
        List<CapacityIndicatorRespDTO> active = capacityIndicatorApi.getIndicatorTreeList().stream()
                .filter(item -> Objects.equals(item.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .toList();
        Map<Long, CapacityIndicatorRespDTO> byId = active.stream()
                .collect(Collectors.toMap(CapacityIndicatorRespDTO::getId, Function.identity(), (left, right) -> left));
        Map<String, List<CapacityIndicatorRespDTO>> first = active.stream()
                .filter(item -> Objects.equals(item.getIndicatorLevel(), 1))
                .collect(Collectors.groupingBy(item -> normalize(item.getIndicatorName()), LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, Map<String, List<CapacityIndicatorRespDTO>>> second = active.stream()
                .filter(item -> Objects.equals(item.getIndicatorLevel(), 2) && item.getParentIndicatorId() != null)
                .collect(Collectors.groupingBy(CapacityIndicatorRespDTO::getParentIndicatorId,
                        LinkedHashMap::new, Collectors.groupingBy(item -> normalize(item.getIndicatorName()),
                                LinkedHashMap::new, Collectors.toList())));
        return new IndicatorIndex(active, byId, first, second);
    }

    private IndicatorResolution resolveFirstIndicator(String value, IndicatorIndex indicators) {
        ParsedIndicatorOption option = parseIndicatorOption(value);
        if (option.id() != null) {
            CapacityIndicatorRespDTO indicator = indicators.byId().get(option.id());
            if (indicator != null && Objects.equals(indicator.getIndicatorLevel(), 1)
                    && Objects.equals(normalize(indicator.getIndicatorName()), normalize(option.name()))) {
                return IndicatorResolution.resolved(indicator);
            }
            return IndicatorResolution.failed("一级能力指标不存在、已停用或模板数据已过期");
        }
        List<CapacityIndicatorRespDTO> matches = indicators.firstByName()
                .getOrDefault(normalize(value), List.of());
        if (matches.size() > 1) {
            return IndicatorResolution.failed("一级能力指标名称重复，请重新下载模板并选择带 ID 的选项");
        }
        return matches.isEmpty() ? IndicatorResolution.failed("一级能力指标不存在或已停用")
                : IndicatorResolution.resolved(matches.get(0));
    }

    private IndicatorResolution resolveSecondIndicator(String value, Long parentId, IndicatorIndex indicators) {
        ParsedIndicatorOption option = parseIndicatorOption(value);
        if (option.id() != null) {
            CapacityIndicatorRespDTO indicator = indicators.byId().get(option.id());
            if (indicator != null && Objects.equals(indicator.getIndicatorLevel(), 2)
                    && Objects.equals(indicator.getParentIndicatorId(), parentId)
                    && Objects.equals(normalize(indicator.getIndicatorName()), normalize(option.name()))) {
                return IndicatorResolution.resolved(indicator);
            }
            return IndicatorResolution.failed("二级能力指标不存在、已停用、不属于当前一级指标或模板数据已过期");
        }
        List<CapacityIndicatorRespDTO> matches = Optional.ofNullable(indicators.secondByParent().get(parentId))
                .map(map -> map.getOrDefault(normalize(value), List.of())).orElseGet(List::of);
        if (matches.size() > 1) {
            return IndicatorResolution.failed("二级能力指标名称重复，请重新下载模板并选择带 ID 的选项");
        }
        return matches.isEmpty() ? IndicatorResolution.failed("二级能力指标不存在、已停用或不属于当前一级指标")
                : IndicatorResolution.resolved(matches.get(0));
    }

    private ParsedIndicatorOption parseIndicatorOption(String value) {
        Matcher matcher = INDICATOR_OPTION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return new ParsedIndicatorOption(value, null);
        }
        try {
            return new ParsedIndicatorOption(matcher.group(1), Long.parseLong(matcher.group(2)));
        } catch (NumberFormatException ex) {
            return new ParsedIndicatorOption(value, null);
        }
    }

    private String indicatorOption(CapacityIndicatorRespDTO indicator) {
        return indicator.getIndicatorName() + " [ID:" + indicator.getId() + "]";
    }

    private Map<String, EduQuestionBankDO> loadExisting(Long teacherId) {
        return questionBankMapper.selectImportByTeacherId(teacherId).stream()
                .collect(Collectors.toMap(item -> key(item.getIndicatorFirstId(), item.getIndicatorSecondId(),
                        item.getHomeworkLevel(), item.getTitle(), item.getRequirement()), Function.identity(),
                        (left, right) -> left));
    }

    private boolean validIndicatorPair(CapacityIndicatorRespDTO first, CapacityIndicatorRespDTO second) {
        return first != null && second != null
                && Objects.equals(first.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                && Objects.equals(second.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                && Objects.equals(second.getParentIndicatorId(), first.getId());
    }

    private Map<String, Object> snapshot(EduQuestionBankDO entity) {
        if (entity == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("teacherId", entity.getTeacherId());
        map.put("indicatorFirstId", entity.getIndicatorFirstId());
        map.put("indicatorSecondId", entity.getIndicatorSecondId());
        map.put("homeworkLevel", entity.getHomeworkLevel());
        map.put("title", entity.getTitle());
        map.put("requirement", entity.getRequirement());
        return map;
    }

    private Map<String, Object> snapshot(QuestionBankImportRow row, Long teacherId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("teacherId", teacherId);
        map.put("indicatorFirstId", row.getIndicatorFirstId());
        map.put("indicatorSecondId", row.getIndicatorSecondId());
        map.put("homeworkLevel", row.getHomeworkLevel());
        map.put("title", row.getTitle());
        map.put("requirement", row.getRequirement());
        return map;
    }

    private String key(Long firstId, Long secondId, Integer level, String title, String requirement) {
        return firstId + "|" + secondId + "|" + level + "|" + normalize(title) + "|" + normalize(requirement);
    }

    private String normalize(String value) {
        return StrUtil.blankToDefault(StrUtil.trim(value), "").toLowerCase(Locale.ROOT);
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private record IndicatorIndex(List<CapacityIndicatorRespDTO> active,
                                  Map<Long, CapacityIndicatorRespDTO> byId,
                                  Map<String, List<CapacityIndicatorRespDTO>> firstByName,
                                  Map<Long, Map<String, List<CapacityIndicatorRespDTO>>> secondByParent) {
    }

    private record ParsedIndicatorOption(String name, Long id) {
    }

    private record IndicatorResolution(CapacityIndicatorRespDTO indicator, String error) {
        private static IndicatorResolution empty() {
            return new IndicatorResolution(null, null);
        }

        private static IndicatorResolution resolved(CapacityIndicatorRespDTO indicator) {
            return new IndicatorResolution(indicator, null);
        }

        private static IndicatorResolution failed(String error) {
            return new IndicatorResolution(null, error);
        }
    }

    private record Candidate(String businessKey, EduImportPreviewRow preview) {
    }

}
