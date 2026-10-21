package cn.nus.zknu.module.system.service.user.classstudent;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.module.system.api.user.StudentClassesChangedEvent;
import cn.nus.zknu.module.system.dal.dataobject.user.EduClassStudentRelDO;
import cn.nus.zknu.module.system.dal.mysql.user.EduClassStudentRelMapper;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertList;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 用户-班级关联 Service 实现类
 */
@Service
public class UserClassStudentServiceImpl implements UserClassStudentService {

    /**
     * 学生 userType（与前端约定）
     */
    private static final Integer STUDENT_USER_TYPE = 2;

    @Resource
    private EduClassStudentRelMapper eduClassStudentRelMapper;
    @Resource
    private UserDeptScopeService deptScopeService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncStudentClasses(Long userId, Integer userType, Set<Long> classIds) {
        if (userId == null) {
            return;
        }
        deptScopeService.validateUserInScope(userId);
        Set<Long> dbClassIds = convertSet(
                eduClassStudentRelMapper.selectListByStudentId(userId),
                EduClassStudentRelDO::getClassId);
        if (!STUDENT_USER_TYPE.equals(userType)) {
            if (deptScopeService.isRestricted()) {
                dbClassIds.forEach(deptScopeService::validateClassInScope);
            }
            eduClassStudentRelMapper.deleteByStudentId(userId);
            publishClassesChanged(userId, Set.of(), dbClassIds);
            return;
        }

        Set<Long> targetClassIds = CollUtil.emptyIfNull(classIds);

        Collection<Long> createClassIds = CollUtil.subtract(targetClassIds, dbClassIds);
        Collection<Long> deleteClassIds = CollUtil.subtract(dbClassIds, targetClassIds);
        // 只检查实际变更的班级；保留原有跨学院关系不会妨碍本院账号资料修改。
        createClassIds.forEach(deptScopeService::validateClassInScope);
        deleteClassIds.forEach(deptScopeService::validateClassInScope);

        if (CollUtil.isNotEmpty(createClassIds)) {
            eduClassStudentRelMapper.insertBatch(convertList(createClassIds, classId -> {
                EduClassStudentRelDO relation = new EduClassStudentRelDO();
                relation.setClassId(classId);
                relation.setStudentId(userId);
                relation.setStatus(CommonStatusEnum.ENABLE.getStatus());
                relation.setJoinTime(LocalDateTime.now());
                return relation;
            }));
        }
        if (CollUtil.isNotEmpty(deleteClassIds)) {
            eduClassStudentRelMapper.deleteByStudentIdAndClassIds(userId, deleteClassIds);
        }
        // 教务快照必须在关系写入后同步处理，异常沿当前事务回滚，避免留下半完成的改班。
        publishClassesChanged(userId, createClassIds, deleteClassIds);
    }

    private void publishClassesChanged(Long studentId, Collection<Long> addedClassIds,
                                       Collection<Long> removedClassIds) {
        if (CollUtil.isEmpty(addedClassIds) && CollUtil.isEmpty(removedClassIds)) {
            return;
        }
        eventPublisher.publishEvent(new StudentClassesChangedEvent(studentId,
                Set.copyOf(addedClassIds), Set.copyOf(removedClassIds)));
    }

    @Override
    public void deleteByStudentId(Long studentId) {
        eduClassStudentRelMapper.deleteByStudentId(studentId);
    }

    @Override
    public void deleteByStudentIds(Collection<Long> studentIds) {
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        eduClassStudentRelMapper.deleteByStudentIds(studentIds);
    }

}
