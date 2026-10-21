package cn.nus.zknu.module.edu.service.classstudent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentListReqVO;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentBatchMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.dal.mysql.user.UserProfileMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertList;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/**
 * 班级学生关联 Service 实现类
 */
@Service
public class EduClassStudentServiceImpl implements EduClassStudentService {

    @Resource
    private EduDeptScopeService deptScopeService;

    private static final Integer STUDENT_USER_TYPE = 2;

    @Resource
    private EduClassMapper eduClassMapper;
    @Resource
    private EduClassStudentMapper eduClassStudentMapper;
    @Resource
    private EduClassStudentBatchMapper batchMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptService deptService;
    @Resource
    private UserProfileMapper userProfileMapper;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;

    /**
     * 获取可加入班级的学生分页。
     */
    @Override
    public PageResult<AdminUserDO> getAvailableStudentPage(EduClassStudentPageReqVO reqVO) {
        validateClassExists(reqVO.getClassId());
        // 先排除已在班级中的学生，避免重复加入
        Set<Long> assignedStudentIds = getAssignedStudentIds(reqVO.getClassId());
        LambdaQueryWrapperX<AdminUserDO> queryWrapper = buildStudentQueryWrapper(reqVO.getKeyword(),
                reqVO.getDeptId(), reqVO.getGradeYear())
                .eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByDesc(AdminUserDO::getId);
        if (CollUtil.isNotEmpty(assignedStudentIds)) {
            queryWrapper.notIn(AdminUserDO::getId, assignedStudentIds);
        }
        return adminUserMapper.selectPage(reqVO, queryWrapper);
    }

    /**
     * 获取已加入班级的学生列表。
     */
    @Override
    public List<AdminUserDO> getAssignedStudentList(EduClassStudentListReqVO reqVO) {
        validateClassExists(reqVO.getClassId());
        Set<Long> assignedStudentIds = getAssignedStudentIds(reqVO.getClassId());
        if (CollUtil.isEmpty(assignedStudentIds)) {
            return Collections.emptyList();
        }
        return adminUserMapper.selectList(buildStudentQueryWrapper(reqVO.getKeyword(), reqVO.getDeptId(), null)
                .in(AdminUserDO::getId, assignedStudentIds)
                .orderByDesc(AdminUserDO::getId));
    }

    /**
     * 批量加入班级。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStudents(Long classId, Collection<Long> studentIds, String remark) {
        EduClassDO targetClass = validateAndGetClass(classId);
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        validateStudents(studentIds);
        // 跨院系或跨年级加入时强制备注，便于后续管理审计
        if (isAddRemarkRequired(classId, studentIds) && StrUtil.isBlank(remark)) {
            throw exception(CLASS_STUDENT_REMARK_REQUIRED);
        }
        String relationRemark = StrUtil.trim(remark);
        if (StrUtil.isBlank(relationRemark)) {
            relationRemark = null;
        }
        final String finalRemark = relationRemark;

        List<EduClassStudentDO> existingRelations = eduClassStudentMapper
                .selectListIncludingDeleted(classId, studentIds);
        Set<Long> existingStudentIds = convertSet(existingRelations, EduClassStudentDO::getStudentId);
        Set<Long> activeStudentIds = convertSet(existingRelations.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted())).toList(), EduClassStudentDO::getStudentId);
        Map<Long, EduClassStudentDO> deletedRelationMap = new LinkedHashMap<>();
        Set<Long> addedStudentIds = new LinkedHashSet<>();
        existingRelations.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDeleted()))
                .forEach(item -> deletedRelationMap.putIfAbsent(item.getStudentId(), item));
        LocalDateTime joinTime = LocalDateTime.now();
        List<Long> restoreIds = deletedRelationMap.entrySet().stream()
                .filter(entry -> !activeStudentIds.contains(entry.getKey()))
                .map(entry -> entry.getValue().getId()).toList();
        for (List<Long> ids : CollUtil.split(restoreIds, 500)) {
            int restored = batchMapper.restoreRelations(ids, CommonStatusEnum.ENABLE.getStatus(), joinTime, finalRemark);
            // 任一关系未恢复仍须拒绝整次变更，与逐条受影响行校验一致。
            deptScopeService.validateAffectedRows(restored == ids.size() ? restored : 0);
        }
        deletedRelationMap.keySet().stream().filter(id -> !activeStudentIds.contains(id)).forEach(addedStudentIds::add);
        // 仅新增历史上从未建立的关系，已逻辑删除的关系原位恢复
        List<EduClassStudentDO> createList = convertList(CollUtil.subtractToList(studentIds, existingStudentIds),
                studentId -> {
                    EduClassStudentDO relation = new EduClassStudentDO();
                    relation.setClassId(classId);
                    relation.setStudentId(studentId);
                    relation.setStatus(CommonStatusEnum.ENABLE.getStatus());
                    relation.setJoinTime(joinTime);
                    relation.setRemark(finalRemark);
                    return relation;
                });
        if (CollUtil.isNotEmpty(createList)) {
            for (List<EduClassStudentDO> rows : CollUtil.split(createList, 500)) {
                batchMapper.insertRelations(rows, operator());
            }
            addedStudentIds.addAll(convertSet(createList, EduClassStudentDO::getStudentId));
        }
        syncOpenHomeworkAssignees(classId, addedStudentIds, true);
        // 关系变更后回写 user_profile.class_ids，保持学生画像和关系表一致
        syncUserProfileClassIds(studentIds, targetClass.getGradeYear());
        sendClassJoinedNotification(targetClass, addedStudentIds);
    }

    /**
     * 判断当前批量加入操作是否必须填写备注。
     */
    @Override
    public boolean isAddRemarkRequired(Long classId, Collection<Long> studentIds) {
        if (CollUtil.isEmpty(studentIds)) {
            return false;
        }
        EduClassDO clazz = validateAndGetClass(classId);
        Map<Long, AdminUserDO> studentMap = new HashMap<>();
        adminUserMapper.selectByIds(studentIds).forEach(item -> studentMap.put(item.getId(), item));
        Map<Long, UserProfileDO> profileMap = new HashMap<>();
        userProfileMapper.selectListByUserIds(studentIds)
                .forEach(item -> profileMap.put(item.getUserId(), item));
        for (Long studentId : studentIds) {
            AdminUserDO student = studentMap.get(studentId);
            if (student == null) {
                continue;
            }
            UserProfileDO profile = profileMap.get(studentId);
            Integer studentGradeYear = profile == null ? null : profile.getGradeYear();
            // 与班级年级或院系不一致时，要求备注说明加入原因
            boolean deptMismatch = clazz.getDeptId() != null
                    && !Objects.equals(student.getDeptId(), clazz.getDeptId());
            boolean gradeMismatch = clazz.getGradeYear() != null
                    && !Objects.equals(studentGradeYear, clazz.getGradeYear());
            if (deptMismatch || gradeMismatch) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量移出班级。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudents(Long classId, Collection<Long> studentIds) {
        validateClassExists(classId);
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        validateStudentScopes(studentIds);
        eduClassStudentMapper.deleteByClassIdAndStudentIds(classId, studentIds);
        syncOpenHomeworkAssignees(classId, studentIds, false);
        // 从班级移除后同步学生画像中的班级集合
        syncUserProfileClassIds(studentIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAssigneesForMembershipChange(Long classId, Collection<Long> studentIds, boolean joining) {
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        syncOpenHomeworkAssignees(classId, studentIds, joining);
        // 用户管理已按请求保存画像，只补齐教务独有的附带动作，避免重复回写。
        if (joining) {
            sendClassJoinedNotification(eduClassMapper.selectById(classId), studentIds);
        }
    }

    private void sendClassJoinedNotification(EduClassDO targetClass, Collection<Long> studentIds) {
        businessNotifyService.sendToUsers(studentIds, EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", StrUtil.blankToDefault(targetClass == null ? null : targetClass.getClassName(),
                        "未命名班级")), "/student/course");
    }

    private void syncOpenHomeworkAssignees(Long classId, Collection<Long> studentIds, boolean joining) {
        if (CollUtil.isEmpty(studentIds)) return;
        List<EduHomeworkDO> openHomeworks = homeworkMapper.selectList(new LambdaQueryWrapper<EduHomeworkDO>()
                .eq(EduHomeworkDO::getClassId, classId)
                .eq(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED)
                .and(scope -> scope.isNull(EduHomeworkDO::getDeadline)
                        .or().gt(EduHomeworkDO::getDeadline, LocalDateTime.now())));
        if (CollUtil.isEmpty(openHomeworks)) return;
        Set<Long> homeworkIds = convertSet(openHomeworks, EduHomeworkDO::getId);
        List<EduHomeworkAssigneeDO> existing = batchMapper.selectAssigneesIncludingDeleted(homeworkIds, studentIds);
        if (joining) {
            Set<AssignmentKey> active = new HashSet<>();
            Set<AssignmentKey> deleted = new HashSet<>();
            existing.forEach(row -> (Boolean.TRUE.equals(row.getDeleted()) ? deleted : active)
                    .add(new AssignmentKey(row.getHomeworkId(), row.getStudentId())));
            List<Long> restoreIds = existing.stream().filter(row -> Boolean.TRUE.equals(row.getDeleted())
                            && !active.contains(new AssignmentKey(row.getHomeworkId(), row.getStudentId())))
                    .map(EduHomeworkAssigneeDO::getId).toList();
            for (List<Long> ids : CollUtil.split(restoreIds, 500)) batchMapper.restoreAssignees(ids);
            List<EduHomeworkAssigneeDO> inserts = new ArrayList<>();
            for (EduHomeworkDO homework : openHomeworks) {
                for (Long studentId : studentIds) {
                    AssignmentKey key = new AssignmentKey(homework.getId(), studentId);
                    if (active.contains(key) || deleted.contains(key)) continue;
                    EduHomeworkAssigneeDO row = new EduHomeworkAssigneeDO();
                    row.setHomeworkId(homework.getId());
                    row.setClassId(classId);
                    row.setStudentId(studentId);
                    row.setAssignedTime(LocalDateTime.now());
                    inserts.add(row);
                }
            }
            for (List<EduHomeworkAssigneeDO> rows : CollUtil.split(inserts, 500)) batchMapper.insertAssignees(rows, operator());
        } else {
            // 有任何未删除记录（包括未提交状态）都保留分配，不能只看提交状态。
            Set<AssignmentKey> recorded = new HashSet<>();
            recordMapper.selectList(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                            .select(EduHomeworkRecordDO::getHomeworkId, EduHomeworkRecordDO::getStudentId)
                            .in(EduHomeworkRecordDO::getHomeworkId, homeworkIds)
                            .in(EduHomeworkRecordDO::getStudentId, studentIds))
                    .forEach(row -> recorded.add(new AssignmentKey(row.getHomeworkId(), row.getStudentId())));
            List<Long> removeIds = existing.stream().filter(row -> !Boolean.TRUE.equals(row.getDeleted())
                            && !recorded.contains(new AssignmentKey(row.getHomeworkId(), row.getStudentId())))
                    .map(EduHomeworkAssigneeDO::getId).toList();
            for (List<Long> ids : CollUtil.split(removeIds, 500)) batchMapper.removeAssignees(ids);
        }
    }

    private record AssignmentKey(Long homeworkId, Long studentId) { }

    private static String operator() {
        return Objects.toString(getLoginUserId(), null);
    }

    /**
     * 按班级集合删除班级-学生关系。
     */
    @Override
    public void deleteByClassIds(Collection<Long> classIds) {
        if (CollUtil.isEmpty(classIds)) {
            return;
        }
        classIds.forEach(deptScopeService::validateClassInScope);
        Set<Long> studentIds = convertSet(eduClassStudentMapper.selectListByClassIds(classIds), EduClassStudentDO::getStudentId);
        eduClassStudentMapper.deleteByClassIds(classIds);
        // 批量删班级后，统一刷新受影响学生的 class_ids
        syncUserProfileClassIds(studentIds);
    }

    /**
     * 构建学生查询条件（关键字、院系、年级）。
     */
    private LambdaQueryWrapperX<AdminUserDO> buildStudentQueryWrapper(String keyword, Long deptId, Integer gradeYear) {
        LambdaQueryWrapperX<AdminUserDO> queryWrapper = new LambdaQueryWrapperX<AdminUserDO>()
                .eq(AdminUserDO::getUserType, STUDENT_USER_TYPE)
                .inIfPresent(AdminUserDO::getDeptId, getDeptCondition(deptId));
        if (gradeYear != null) {
            queryWrapper.apply("EXISTS (SELECT 1 FROM system_user_profile p WHERE p.user_id = system_users.id "
                    + "AND p.user_type = {0} AND p.grade_year = {1} AND p.deleted = 0)", STUDENT_USER_TYPE, gradeYear);
        }
        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(AdminUserDO::getUsername, keyword)
                    .or()
                    .like(AdminUserDO::getNickname, keyword)
                    .or()
                    .like(AdminUserDO::getMobile, keyword));
        }
        return queryWrapper;
    }

    /**
     * 查询班级已分配学生 ID。
     */
    private Set<Long> getAssignedStudentIds(Long classId) {
        return convertSet(eduClassStudentMapper.selectListByClassId(classId), EduClassStudentDO::getStudentId);
    }

    /**
     * 校验班级存在。
     */
    private void validateClassExists(Long classId) {
        validateAndGetClass(classId);
    }

    /**
     * 校验并返回班级实体。
     */
    private EduClassDO validateAndGetClass(Long classId) {
        deptScopeService.validateClassInScope(classId);
        EduClassDO clazz = classId == null ? null : eduClassMapper.selectById(classId);
        if (clazz == null) {
            throw exception(FORBIDDEN);
        }
        deptScopeService.validateDeptInScope(clazz.getDeptId());
        return clazz;
    }

    /**
     * 校验学生账号有效且为启用状态。
     */
    private void validateStudents(Collection<Long> studentIds) {
        Map<Long, AdminUserDO> studentMap = validateStudentScopes(studentIds);
        studentIds.forEach(studentId -> {
            AdminUserDO student = studentMap.get(studentId);
            if (student == null) {
                if (deptScopeService.isRestricted()) {
                    throw exception(FORBIDDEN);
                }
                throw exception(CLASS_STUDENT_NOT_EXISTS);
            }
            deptScopeService.validateDeptInScope(student.getDeptId());
            if (!Objects.equals(student.getUserType(), STUDENT_USER_TYPE)) {
                throw exception(CLASS_STUDENT_NOT_STUDENT);
            }
            if (!Objects.equals(student.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
                throw exception(CLASS_STUDENT_IS_DISABLED, student.getNickname());
            }
        });
    }

    private Map<Long, AdminUserDO> validateStudentScopes(Collection<Long> studentIds) {
        Map<Long, AdminUserDO> users = new HashMap<>();
        adminUserMapper.selectByIds(studentIds).forEach(user -> users.put(user.getId(), user));
        if (deptScopeService.isRestricted()) {
            for (Long id : studentIds) {
                AdminUserDO user = users.get(id);
                if (user == null) throw exception(FORBIDDEN);
                deptScopeService.validateDeptInScope(user.getDeptId());
            }
        }
        return users;
    }

    /**
     * 获取院系过滤条件（当前院系 + 子院系）。
     */
    private Set<Long> getDeptCondition(Long deptId) {
        if (deptId == null) {
            return Collections.emptySet();
        }
        // 按“当前院系 + 子院系”做学生筛选
        Set<Long> deptIds = new LinkedHashSet<>(deptService.getChildDeptIdListFromCache(deptId));
        deptIds.add(deptId);
        return deptIds;
    }

    /**
     * 同步维护 user_profile.class_ids，保持画像与关系表一致。
     */
    private void syncUserProfileClassIds(Collection<Long> studentIds) {
        syncUserProfileClassIds(studentIds, null);
    }

    private void syncUserProfileClassIds(Collection<Long> studentIds, Integer defaultGradeYear) {
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        List<UserProfileDO> profileList = userProfileMapper.selectListByUserIds(studentIds);
        Map<Long, UserProfileDO> profileMap = new HashMap<>();
        profileList.forEach(item -> profileMap.put(item.getUserId(), item));

        Map<Long, Set<Long>> studentClassIdsMap = new HashMap<>();
        eduClassStudentMapper.selectListByStudentIds(studentIds).forEach(item -> {
            studentClassIdsMap.computeIfAbsent(item.getStudentId(), key -> new LinkedHashSet<>()).add(item.getClassId());
        });

        List<UserProfileDO> inserts = new ArrayList<>();
        List<UserProfileDO> updates = new ArrayList<>();
        for (Long studentId : studentIds) {
            Set<Long> classIds = studentClassIdsMap.getOrDefault(studentId, Collections.emptySet());
            UserProfileDO profile = profileMap.get(studentId);
            if (profile == null) {
                if (CollUtil.isEmpty(classIds)) {
                    continue;
                }
                // 历史无画像但已有班级关系时，补建画像记录
                UserProfileDO createObj = new UserProfileDO();
                createObj.setUserId(studentId);
                createObj.setUserType(STUDENT_USER_TYPE);
                createObj.setGradeYear(defaultGradeYear);
                createObj.setClassIds(classIds);
                inserts.add(createObj);
                continue;
            }
            // 已有画像记录时，仅更新班级 id 集合
            UserProfileDO updateObj = new UserProfileDO();
            updateObj.setId(profile.getId());
            updateObj.setClassIds(new LinkedHashSet<>(classIds));
            updates.add(updateObj);
        }
        for (List<UserProfileDO> rows : CollUtil.split(inserts, 500)) batchMapper.insertProfiles(rows, operator());
        for (List<UserProfileDO> rows : CollUtil.split(updates, 500)) batchMapper.updateProfiles(rows, operator());
    }

}
