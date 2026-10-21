package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.util.json.databind.TimestampLocalDateTimeDeserializer;
import cn.nus.zknu.framework.common.util.json.databind.TimestampLocalDateTimeSerializer;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordGradeRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordRedoReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordRespVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeworkGradingTimeJsonTest {

    // 与 NusJacksonAutoConfiguration 一致，覆盖实际使用的自定义序列化器。
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new SimpleModule()
            .addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
            .addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE));

    @Test
    void updateTime_roundTripsFromEveryGradingResponseToBothRequests() throws Exception {
        LocalDateTime version = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        EduHomeworkRecordRespVO record = new EduHomeworkRecordRespVO();
        record.setUpdateTime(version);
        EduHomeworkRecordGradeRespVO grade = new EduHomeworkRecordGradeRespVO();
        grade.setUpdateTime(version);
        EduHomeworkRecordQueueItemRespVO queueItem = new EduHomeworkRecordQueueItemRespVO();
        queueItem.setUpdateTime(version);

        for (Object response : new Object[]{record, grade, queueItem}) {
            String serializedVersion = mapper.readTree(mapper.writeValueAsString(response))
                    .get("updateTime").asText();
            assertEquals("2026-10-07 09:30:15.123", serializedVersion);
            String request = "{\"expectedUpdateTime\":\"" + serializedVersion + "\"}";
            assertEquals(version, mapper.readValue(request, EduHomeworkRecordCorrectReqVO.class).getExpectedUpdateTime());
            assertEquals(version, mapper.readValue(request, EduHomeworkRecordRedoReqVO.class).getExpectedUpdateTime());
        }
    }

    @Test
    void expectedUpdateTime_acceptsExistingSecondsIsoAndEpochMillisFormats() throws Exception {
        LocalDateTime version = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        assertEquals(version.withNano(0), mapper.readValue(
                "{\"expectedUpdateTime\":\"2026-10-07 09:30:15\"}",
                EduHomeworkRecordCorrectReqVO.class).getExpectedUpdateTime());
        assertEquals(version, mapper.readValue(
                "{\"expectedUpdateTime\":\"2026-10-07T09:30:15.123\"}",
                EduHomeworkRecordCorrectReqVO.class).getExpectedUpdateTime());
        long epochMillis = version.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(version, mapper.readValue("{\"expectedUpdateTime\":" + epochMillis + "}",
                EduHomeworkRecordCorrectReqVO.class).getExpectedUpdateTime());
    }
}
