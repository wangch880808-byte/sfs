package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class EduImportPreviewUtils {

    private EduImportPreviewUtils() {
    }

    public static <T> void markDuplicates(List<EduImportPreviewRow> rows,
                                          Function<EduImportPreviewRow, T> keyExtractor,
                                          String errorMessage) {
        Map<T, List<EduImportPreviewRow>> groups = rows.stream()
                .map(row -> new RowKey<>(row, keyExtractor.apply(row)))
                .filter(entry -> entry.key() != null)
                .collect(java.util.stream.Collectors.groupingBy(RowKey::key, LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(RowKey::row, java.util.stream.Collectors.toList())));
        groups.values().stream()
                .filter(group -> group.size() > 1)
                .flatMap(List::stream)
                .map(EduImportPreviewRow::getErrors)
                .filter(errors -> errors != null)
                .forEach(errors -> {
                    if (!errors.contains(errorMessage)) {
                        errors.add(errorMessage);
                    }
                });
    }

    private record RowKey<T>(EduImportPreviewRow row, T key) {
    }

}
