package cn.nus.zknu.module.capacity.service.course;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseMemberDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMemberMapper;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CapacityCourseMemberServiceImpl implements CapacityCourseMemberService {

    @Resource
    private CapacityCourseMemberMapper memberMapper;
    @Resource
    private CapacityCourseMapper courseMapper;

    @Override
    public List<Long> getMemberUserIds(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return memberMapper.selectListByCourseId(courseId).stream()
                .map(CapacityCourseMemberDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<Long>> getMemberUserIdsMap(Collection<Long> courseIds) {
        if (CollUtil.isEmpty(courseIds)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (CapacityCourseMemberDO member : memberMapper.selectListByCourseIds(new LinkedHashSet<>(courseIds))) {
            if (member.getCourseId() == null || member.getUserId() == null) {
                continue;
            }
            List<Long> ids = result.computeIfAbsent(member.getCourseId(), ignored -> new ArrayList<>());
            if (!ids.contains(member.getUserId())) {
                ids.add(member.getUserId());
            }
        }
        return result;
    }

    @Override
    public boolean isMember(Long courseId, Long userId) {
        return courseId != null && userId != null && memberMapper.existsByCourseIdAndUserId(courseId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceMembers(Long courseId, Collection<Long> userIds) {
        if (courseId == null) {
            return;
        }
        Set<Long> target = userIds == null ? Collections.emptySet() : userIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 同课程替换串行执行；成员也用当前读，避免外层事务已建立的旧快照留下被移除成员。
        courseMapper.selectByIdForUpdate(courseId);
        List<CapacityCourseMemberDO> existing = memberMapper.selectListByCourseIdForUpdate(courseId);
        Set<Long> kept = new HashSet<>();
        List<Long> removeIds = new ArrayList<>();
        for (CapacityCourseMemberDO member : existing) {
            // 目标里没有的、以及同一用户的重复行都移除
            if (target.contains(member.getUserId()) && kept.add(member.getUserId())) {
                continue;
            }
            removeIds.add(member.getId());
        }
        if (!removeIds.isEmpty()) {
            memberMapper.deleteByIds(removeIds);
        }
        for (Long userId : target) {
            if (kept.contains(userId)) {
                continue;
            }
            CapacityCourseMemberDO member = new CapacityCourseMemberDO();
            member.setCourseId(courseId);
            member.setUserId(userId);
            member.setRoleType(CapacityCourseMemberDO.ROLE_TEAM);
            try {
                memberMapper.insert(member);
            } catch (DuplicateKeyException ex) {
                // 并发保存同一课程时另一请求已写入该成员，结果一致，忽略
                log.info("[replaceMembers][课程({}) 成员({}) 已存在，跳过]", courseId, userId);
            }
        }
    }

}
