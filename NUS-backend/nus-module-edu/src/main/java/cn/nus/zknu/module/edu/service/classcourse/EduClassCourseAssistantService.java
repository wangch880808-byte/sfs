package cn.nus.zknu.module.edu.service.classcourse;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseAssistantDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseAssistantMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班课助教（edu_class_course_assistant）的读写。访问判断、留言接收人、详情展示都从这里读，
 * 不再解析 edu_class_course.assistant_teacher_ids 逗号串（该列只在保存时同步写入，留作回退）。
 */
@Service
@Slf4j
public class EduClassCourseAssistantService {

    @Resource
    private EduClassCourseAssistantMapper assistantMapper;

    /** 班课助教的用户 ID，按加入顺序。 */
    public List<Long> getTeacherIds(Long classCourseId) {
        if (classCourseId == null) {
            return Collections.emptyList();
        }
        return assistantMapper.selectListByClassCourseId(classCourseId).stream()
                .map(EduClassCourseAssistantDO::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 多个班课的助教用户 ID；没有助教的班课不出现在结果里。 */
    public Map<Long, List<Long>> getTeacherIdsMap(Collection<Long> classCourseIds) {
        if (CollUtil.isEmpty(classCourseIds)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (EduClassCourseAssistantDO row : assistantMapper.selectListByClassCourseIds(new LinkedHashSet<>(classCourseIds))) {
            if (row.getClassCourseId() == null || row.getTeacherId() == null) {
                continue;
            }
            List<Long> ids = result.computeIfAbsent(row.getClassCourseId(), ignored -> new ArrayList<>());
            if (!ids.contains(row.getTeacherId())) {
                ids.add(row.getTeacherId());
            }
        }
        return result;
    }

    /** 教师是否为任一给定班课的助教。 */
    public boolean isAssistant(Collection<Long> classCourseIds, Long teacherId) {
        return assistantMapper.existsByClassCourseIdsAndTeacherId(
                classCourseIds == null ? null : classCourseIds.stream().filter(Objects::nonNull).toList(), teacherId);
    }

    /**
     * 把班课助教替换为给定教师：新增缺少的、逻辑删除多余的，已有的保持不动。
     */
    public void replaceAssistants(Long classCourseId, Collection<Long> teacherIds) {
        if (classCourseId == null) {
            return;
        }
        Set<Long> target = teacherIds == null ? Collections.emptySet() : teacherIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> kept = new HashSet<>();
        List<Long> removeIds = new ArrayList<>();
        for (EduClassCourseAssistantDO row : assistantMapper.selectListByClassCourseId(classCourseId)) {
            if (target.contains(row.getTeacherId()) && kept.add(row.getTeacherId())) {
                continue;
            }
            removeIds.add(row.getId());
        }
        if (!removeIds.isEmpty()) {
            assistantMapper.deleteByIds(removeIds);
        }
        for (Long teacherId : target) {
            if (kept.contains(teacherId)) {
                continue;
            }
            EduClassCourseAssistantDO row = new EduClassCourseAssistantDO();
            row.setClassCourseId(classCourseId);
            row.setTeacherId(teacherId);
            try {
                assistantMapper.insert(row);
            } catch (DuplicateKeyException ex) {
                log.info("[replaceAssistants][班课({}) 助教({}) 已存在，跳过]", classCourseId, teacherId);
            }
        }
    }

    /** 逗号串（与前端约定一致，半角逗号、无空格）；没有助教时返回 null。 */
    public static String toCsv(Collection<Long> ids) {
        return CollUtil.isEmpty(ids) ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

}
