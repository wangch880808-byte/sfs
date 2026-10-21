package cn.nus.zknu.framework.common.util.json.databind;

import cn.nus.zknu.framework.common.util.date.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 基于时间戳的 LocalDateTime 反序列化器
 *
 * @author hyh
 */
public class TimestampLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    public static final TimestampLocalDateTimeDeserializer INSTANCE = new TimestampLocalDateTimeDeserializer();
    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern(DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND + "[.SSS]");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return fromTimestamp(p.getLongValue());
        }
        String text = p.getValueAsString();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        if (value.matches("^\\d+$")) {
            return fromTimestamp(Long.parseLong(value));
        }
        try {
            return LocalDateTime.parse(value, DEFAULT_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        throw ctxt.weirdStringException(value, LocalDateTime.class,
                "无法解析时间，请使用 yyyy-MM-dd HH:mm:ss[.SSS] 或时间戳");
    }

    private LocalDateTime fromTimestamp(long timestamp) {
        long epochMillis = timestamp < 100_000_000_000L ? timestamp * 1000 : timestamp;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

}
