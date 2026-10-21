package cn.nus.zknu.module.capacity.service.course;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.capacity.api.course.PublicCourseChangedEvent;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseAuditReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCoursePageReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import cn.nus.zknu.module.capacity.dal.mysql.indicator.CapacityIndicatorMapper;
import cn.nus.zknu.module.capacity.enums.CapacityCoursePublicAuditStatusEnum;
import cn.nus.zknu.module.capacity.enums.CapacityCourseTypeConstants;
import cn.nus.zknu.module.capacity.enums.CapacityPermissionConstants;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.api.permission.PermissionApi;
import cn.nus.zknu.module.system.api.notify.NotifyMessageSendApi;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.capacity.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class CapacityCourseServiceImpl implements CapacityCourseService {

    private static final String PUBLIC_COURSE_PENDING = "edu.admin.public-course.pending";
    private static final String PUBLIC_COURSE_AUDIT_RESULT = "edu.teacher.public-course.audit-result";

    /**
     * 课程封面上传目录，只有该目录下的平台文件才允许随课程清理
     */
    static final String COURSE_IMAGE_DIRECTORY = "capacity/course";
    /**
     * 前端内置的默认封面（静态资源，不是平台文件），允许直接选用
     */
    private static final Pattern DEFAULT_COURSE_COVER_URL = Pattern.compile(
            "^/static/imgs/course-covers/[0-9a-f]{32}\\.jpg$");

    @Resource
    private CapacityCourseMapper capacityCourseMapper;
    @Resource
    private CapacityIndicatorMapper capacityIndicatorMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private CapacityCourseMemberService memberService;

    /**
     * 创建课程（支持同请求携带封面文件）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCourse(CapacityCourseSaveReqVO createReqVO, MultipartFile courseImageFile) throws Exception {
        // 课程必须绑定有效指标，冗余存储指标名称用于列表直出
        CapacityIndicatorDO indicator = validateIndicatorExists(createReqVO.getIndicatorId());
        // 所属教师为空时默认当前登录人
        Long teacherId = resolveTeacherId(createReqVO.getTeacherId());
        String teachAssistantIds = normalizeAssistantIds(createReqVO.getTeachAssistantIds(), teacherId);
        validateUserIds(teacherId, teachAssistantIds);

        CapacityCourseDO course = BeanUtils.toBean(createReqVO, CapacityCourseDO.class);
        course.setIndicatorName(indicator.getIndicatorName());
        course.setTeacherId(teacherId);
        if (course.getStatus() == null) {
            course.setStatus(0);
        }
        applyPublicAuditState(course);
        // 图片上传统一走 infra 的 FileApi，capacity 不直接处理存储细节；新建时忽略客户端传入的非默认封面地址
        course.setCourseImageUrl(resolveCourseImageUrl(isDefaultCourseCover(createReqVO.getCourseImageUrl())
                ? createReqVO.getCourseImageUrl() : null, courseImageFile));
        // 课程团队以 capacity_course_member 为准；V1.0.1.7 起逗号串列仍同步写入一个版本，便于回退
        course.setTeachAssistantIds(teachAssistantIds);
        capacityCourseMapper.insert(course);
        memberService.replaceMembers(course.getId(), parseAssistantIds(teachAssistantIds));
        publishPublicCourseChanged(course);
        if (Objects.equals(course.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.PENDING.getStatus())) {
            notifyPublicCoursePending(course);
        }
        return course.getId();
    }

    /**
     * 更新课程（支持保留旧封面或替换新封面）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(CapacityCourseSaveReqVO updateReqVO, MultipartFile courseImageFile) throws Exception {
        CapacityCourseDO oldCourse = validateCourseExists(updateReqVO.getId());
        validateCourseManageAccess(oldCourse);
        CapacityIndicatorDO indicator = validateIndicatorExists(updateReqVO.getIndicatorId());
        Long teacherId = resolveTeacherId(updateReqVO.getTeacherId());
        String teachAssistantIds = normalizeAssistantIds(updateReqVO.getTeachAssistantIds(), teacherId);
        validateUserIds(teacherId, teachAssistantIds);

        CapacityCourseDO updateObj = BeanUtils.toBean(updateReqVO, CapacityCourseDO.class);
        updateObj.setIndicatorName(indicator.getIndicatorName());
        updateObj.setTeacherId(teacherId);
        if (updateObj.getStatus() == null) {
            updateObj.setStatus(oldCourse.getStatus());
        }
        applyPublicAuditState(updateObj);
        // 新图会覆盖旧图地址，旧图在更新后执行清理；未上传新图时只允许清空、保留原图或选用默认封面
        validateCourseImageUrl(updateReqVO.getCourseImageUrl(), oldCourse.getCourseImageUrl(), courseImageFile);
        // 空值表示清除封面，写入空串保证数据库同步清空（null 字段不会被 updateById 更新）
        updateObj.setCourseImageUrl(StrUtil.nullToEmpty(
                resolveCourseImageUrl(updateReqVO.getCourseImageUrl(), courseImageFile)));
        // 课程团队以 capacity_course_member 为准；逗号串列同步写入（清空时写空串，null 不会被 updateById 更新）
        updateObj.setTeachAssistantIds(StrUtil.nullToEmpty(teachAssistantIds));
        capacityCourseMapper.updateById(updateObj);
        memberService.replaceMembers(updateObj.getId(), parseAssistantIds(teachAssistantIds));
        publishPublicCourseChanged(updateObj);
        if (!Objects.equals(oldCourse.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.PENDING.getStatus())
                && Objects.equals(updateObj.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.PENDING.getStatus())) {
            notifyPublicCoursePending(updateObj);
        }
        // 图片变更时回收旧文件，避免本地/对象存储遗留垃圾文件
        if (StrUtil.isNotBlank(oldCourse.getCourseImageUrl())
                && !StrUtil.equals(oldCourse.getCourseImageUrl(), updateObj.getCourseImageUrl())) {
            deleteCourseImageFile(oldCourse.getCourseImageUrl());
        }
    }

    /** 归档课程，保留封面文件。 */
    @Override
    public void deleteCourse(Long id) {
        CapacityCourseDO course = validateCourseExists(id);
        validateCourseManageAccess(course);
        CapacityCourseDO update = new CapacityCourseDO();
        update.setId(id);
        update.setStatus(1);
        capacityCourseMapper.updateById(update);
    }

    /** 批量归档课程。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<CapacityCourseDO> courses = capacityCourseMapper.selectByIds(ids);
        courses.forEach(this::validateCourseManageAccess);
        for (CapacityCourseDO course : courses) {
            CapacityCourseDO update = new CapacityCourseDO();
            update.setId(course.getId());
            update.setStatus(1);
            capacityCourseMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreCourse(Long id) {
        CapacityCourseDO course = validateCourseExists(id);
        validateCourseManageAccess(course);
        CapacityCourseDO update = new CapacityCourseDO();
        update.setId(id);
        update.setStatus(0);
        capacityCourseMapper.updateById(update);
        course.setStatus(0);
        publishPublicCourseChanged(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreCourseList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        ids.forEach(this::restoreCourse);
    }

    /**
     * 获取课程详情（不存在会抛异常）。
     */
    @Override
    public CapacityCourseDO getCourse(Long id) {
        CapacityCourseDO course = validateCourseExists(id);
        validateCourseAccess(course);
        fillTeachAssistantIds(List.of(course));
        return course;
    }

    /**
     * 分页查询课程列表。
     */
    @Override
    public PageResult<CapacityCourseDO> getCoursePage(CapacityCoursePageReqVO reqVO) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        boolean canApprove = canApprovePublicCourse(loginUserId);
        LambdaQueryWrapperX<CapacityCourseDO> queryWrapper = new LambdaQueryWrapperX<CapacityCourseDO>()
                .likeIfPresent(CapacityCourseDO::getCourseName, reqVO.getCourseName())
                .eqIfPresent(CapacityCourseDO::getIndicatorId, reqVO.getIndicatorId())
                .eqIfPresent(CapacityCourseDO::getIsPublicCourse, reqVO.getIsPublicCourse())
                .eqIfPresent(CapacityCourseDO::getPublicAuditStatus, reqVO.getPublicAuditStatus())
                .eqIfPresent(CapacityCourseDO::getStatus, reqVO.getStatus())
                .eq(!Boolean.TRUE.equals(reqVO.getIncludeArchived()), CapacityCourseDO::getStatus, 0)
                .betweenIfPresent(CapacityCourseDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CapacityCourseDO::getId);
        if (canApprove) {
            queryWrapper.eqIfPresent(CapacityCourseDO::getTeacherId, reqVO.getTeacherId());
            return fillTeachAssistantIds(capacityCourseMapper.selectPage(reqVO, queryWrapper));
        }
        queryWrapper.and(wrapper -> wrapper
                .eq(CapacityCourseDO::getTeacherId, loginUserId)
                // 课程团队成员：capacity_course_member 中未删除的成员行
                .or(item -> item.apply("id IN (SELECT m.course_id FROM capacity_course_member m"
                        + " WHERE m.deleted = 0 AND m.user_id = {0})", loginUserId))
                // 审批通过后的公共课向所有教师开放；未通过的公共课仍只对创建人与协作教师可见
                .or(canViewOnlinePublicCourse(loginUserId), item -> item
                        .eq(CapacityCourseDO::getIsPublicCourse, CapacityCourseTypeConstants.PUBLIC_COURSE)
                        .eq(CapacityCourseDO::getPublicAuditStatus,
                                CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus())
                        .eq(CapacityCourseDO::getStatus, 0)));
        return fillTeachAssistantIds(capacityCourseMapper.selectPage(reqVO, queryWrapper));
    }

    /**
     * 返回给前端的 teachAssistantIds 取自 capacity_course_member（逗号串，按加入顺序），不读旧列。
     */
    private PageResult<CapacityCourseDO> fillTeachAssistantIds(PageResult<CapacityCourseDO> page) {
        if (page != null) {
            fillTeachAssistantIds(page.getList());
        }
        return page;
    }

    private void fillTeachAssistantIds(List<CapacityCourseDO> courses) {
        if (CollUtil.isEmpty(courses)) {
            return;
        }
        Map<Long, List<Long>> members = memberService.getMemberUserIdsMap(
                courses.stream().map(CapacityCourseDO::getId).filter(Objects::nonNull).toList());
        for (CapacityCourseDO course : courses) {
            List<Long> ids = members.get(course.getId());
            course.setTeachAssistantIds(CollUtil.isEmpty(ids) ? null : StrUtil.join(",", ids));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditPublicCourse(CapacityCourseAuditReqVO reqVO) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (!canApprovePublicCourse(loginUserId)) {
            throw exception(COURSE_PUBLIC_AUDIT_FORBIDDEN);
        }
        CapacityCourseDO course = validateCourseExists(reqVO.getId());
        if (!Objects.equals(course.getIsPublicCourse(), CapacityCourseTypeConstants.PUBLIC_COURSE)
                || !Objects.equals(course.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.PENDING.getStatus())) {
            throw exception(COURSE_PUBLIC_AUDIT_PENDING);
        }
        course.setPublicAuditStatus(Boolean.TRUE.equals(reqVO.getApproved())
                ? CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus()
                : CapacityCoursePublicAuditStatusEnum.REJECTED.getStatus());
        course.setPublicAuditRemark(StrUtil.blankToDefault(StrUtil.trim(reqVO.getRemark()), null));
        course.setPublicAuditTime(LocalDateTime.now());
        course.setPublicAuditUserId(loginUserId);
        capacityCourseMapper.updateById(course);
        publishPublicCourseChanged(course);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("courseName", course.getCourseName());
        params.put("auditResult", Boolean.TRUE.equals(reqVO.getApproved()) ? "审批通过" : "审批驳回");
        params.put("auditRemark", StrUtil.blankToDefault(course.getPublicAuditRemark(), "无"));
        params.put("targetUrl", "/capacity/course/detail/" + course.getId());
        sendNotifyQuietly(course.getTeacherId(), PUBLIC_COURSE_AUDIT_RESULT, params);
    }

    /**
     * 解析所属教师：仅管理员可替其他教师建课或转移课程。
     */
    private Long resolveTeacherId(Long teacherId) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        Assert.notNull(loginUserId, "当前登录用户不能为空");
        if (canApprovePublicCourse(loginUserId) && teacherId != null) {
            return teacherId;
        }
        return loginUserId;
    }

    /**
     * 解析课程封面地址：有新文件则上传并返回新地址，否则沿用原地址。
     */
    private String resolveCourseImageUrl(String courseImageUrl, MultipartFile courseImageFile) throws Exception {
        // 未上传新图片时，沿用前端传入的现有地址（新增时通常为空，编辑时保留旧图）
        if (courseImageFile == null || courseImageFile.isEmpty()) {
            return courseImageUrl;
        }
        return fileApi.createFile(
                IoUtil.readBytes(courseImageFile.getInputStream()),
                courseImageFile.getOriginalFilename(),
                COURSE_IMAGE_DIRECTORY,
                courseImageFile.getContentType()
        );
    }

    /**
     * 更新课程时，未上传新图片则封面地址只允许为空、等于原地址或内置默认封面，避免引用他人文件后被随课程删除。
     */
    private void validateCourseImageUrl(String courseImageUrl, String oldCourseImageUrl, MultipartFile courseImageFile) {
        if (courseImageFile != null && !courseImageFile.isEmpty()) {
            return;
        }
        if (StrUtil.isBlank(courseImageUrl) || StrUtil.equals(courseImageUrl, oldCourseImageUrl)
                || isDefaultCourseCover(courseImageUrl)) {
            return;
        }
        throw exception(COURSE_IMAGE_URL_INVALID);
    }

    private static boolean isDefaultCourseCover(String courseImageUrl) {
        return courseImageUrl != null && DEFAULT_COURSE_COVER_URL.matcher(courseImageUrl).matches();
    }

    /**
     * 清理课程封面文件：只删除课程封面目录下已登记的平台文件，其它地址（默认封面、历史外链等）一律跳过。
     */
    private void deleteCourseImageFile(String courseImageUrl) {
        if (StrUtil.isBlank(courseImageUrl) || isDefaultCourseCover(courseImageUrl)) {
            return;
        }
        FileContentRespDTO file;
        try {
            file = fileApi.getFile(courseImageUrl);
        } catch (Exception ex) {
            log.warn("[deleteCourseImageFile][封面({}) 不是已登记的平台文件，跳过清理]", courseImageUrl);
            return;
        }
        if (file == null || !StrUtil.startWith(file.getPath(), COURSE_IMAGE_DIRECTORY + "/")) {
            log.warn("[deleteCourseImageFile][封面({}) 不在课程封面目录，跳过清理]", courseImageUrl);
            return;
        }
        fileApi.deleteFile(courseImageUrl);
    }

    /**
     * 校验课程存在并返回实体。
     */
    private CapacityCourseDO validateCourseExists(Long id) {
        CapacityCourseDO course = capacityCourseMapper.selectById(id);
        if (course == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
        return course;
    }

    /**
     * 公共课在教师端不是“勾选即开放”，而是进入审批流。
     */
    private void applyPublicAuditState(CapacityCourseDO course) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (!Objects.equals(course.getIsPublicCourse(), CapacityCourseTypeConstants.PUBLIC_COURSE)) {
            course.setPublicAuditStatus(CapacityCoursePublicAuditStatusEnum.NONE.getStatus());
            course.setPublicAuditRemark(null);
            course.setPublicAuditTime(null);
            course.setPublicAuditUserId(null);
            return;
        }
        if (canApprovePublicCourse(loginUserId)) {
            course.setPublicAuditStatus(CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus());
            course.setPublicAuditRemark("管理员新建或更新公共课，自动生效");
            course.setPublicAuditTime(LocalDateTime.now());
            course.setPublicAuditUserId(loginUserId);
            return;
        }
        course.setPublicAuditStatus(CapacityCoursePublicAuditStatusEnum.PENDING.getStatus());
        course.setPublicAuditRemark(null);
        course.setPublicAuditTime(null);
        course.setPublicAuditUserId(null);
    }

    private void validateCourseAccess(CapacityCourseDO course) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (loginUserId == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
        if (canApprovePublicCourse(loginUserId)) {
            return;
        }
        if (Objects.equals(course.getTeacherId(), loginUserId)) {
            return;
        }
        if (memberService.isMember(course.getId(), loginUserId)) {
            return;
        }
        if (canViewOnlinePublicCourse(loginUserId)
                && Objects.equals(course.getIsPublicCourse(), CapacityCourseTypeConstants.PUBLIC_COURSE)
                && Objects.equals(course.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus())
                && Objects.equals(course.getStatus(), 0)) {
            return;
        }
        throw exception(COURSE_NOT_EXISTS);
    }

    private void validateCourseManageAccess(CapacityCourseDO course) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (loginUserId == null || (!canApprovePublicCourse(loginUserId)
                && !Objects.equals(course.getTeacherId(), loginUserId))) {
            throw exception(COURSE_NOT_EXISTS);
        }
    }

    /** 持有公共课审批权限的人同时是课程管理员：看全部课程、管理任意课程、新建或修改公共课直接生效 */
    private boolean canApprovePublicCourse(Long userId) {
        return userId != null && permissionApi.hasAnyPermissions(userId, CapacityPermissionConstants.COURSE_AUDIT);
    }

    private boolean canViewOnlinePublicCourse(Long userId) {
        return eduRoleApi.isTeacher(userId);
    }

    private void publishPublicCourseChanged(CapacityCourseDO course) {
        if (Objects.equals(course.getIsPublicCourse(), CapacityCourseTypeConstants.PUBLIC_COURSE)
                && Objects.equals(course.getPublicAuditStatus(), CapacityCoursePublicAuditStatusEnum.APPROVED.getStatus())
                && Objects.equals(course.getStatus(), 0)) {
            eventPublisher.publishEvent(new PublicCourseChangedEvent(course.getId()));
        }
    }

    private void notifyPublicCoursePending(CapacityCourseDO course) {
        var teacher = adminUserApi.getUser(course.getTeacherId());
        String teacherName = teacher == null || StrUtil.isBlank(teacher.getNickname())
                ? "教师" + course.getTeacherId() : teacher.getNickname();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("courseName", course.getCourseName());
        params.put("teacherName", teacherName);
        params.put("targetUrl", "/capacity/course_manager/course");
        // 发给持有审批权限、账号开启的人（含超级管理员）
        permissionApi.getEnableUserIdListByPermission(CapacityPermissionConstants.COURSE_AUDIT)
                .forEach(userId -> sendNotifyQuietly(userId, PUBLIC_COURSE_PENDING, params));
    }

    private void sendNotifyQuietly(Long userId, String templateCode, Map<String, Object> params) {
        if (userId == null) {
            return;
        }
        try {
            NotifySendSingleToUserReqDTO reqDTO = new NotifySendSingleToUserReqDTO();
            reqDTO.setUserId(userId);
            reqDTO.setTemplateCode(templateCode);
            reqDTO.setTemplateParams(params);
            notifyMessageSendApi.sendSingleMessageToAdmin(reqDTO);
        } catch (Exception ex) {
            log.error("[sendNotifyQuietly][公共课站内信发送失败 userId={}, templateCode={}]",
                    userId, templateCode, ex);
        }
    }

    /**
     * 校验关联指标存在并返回实体。
     */
    private CapacityIndicatorDO validateIndicatorExists(Long indicatorId) {
        CapacityIndicatorDO indicator = capacityIndicatorMapper.selectById(indicatorId);
        if (indicator == null) {
            throw exception(INDICATOR_NOT_EXISTS);
        }
        return indicator;
    }

    /**
     * 校验教师及助教账号有效性。
     */
    private void validateUserIds(Long teacherId, String teachAssistantIds) {
        adminUserApi.validateUser(teacherId);
        List<Long> assistantIds = parseAssistantIds(teachAssistantIds);
        if (CollUtil.isNotEmpty(assistantIds)) {
            adminUserApi.validateUserList(assistantIds);
        }
    }

    /**
     * 规范化助教 id 串（去重后逗号分隔）。
     */
    private String normalizeAssistantIds(String teachAssistantIds, Long teacherId) {
        List<Long> assistantIds = parseAssistantIds(teachAssistantIds);
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        assistantIds.removeIf(id -> Objects.equals(id, loginUserId) || Objects.equals(id, teacherId));
        if (CollUtil.isEmpty(assistantIds)) {
            return null;
        }
        // 去重后的助教 id 使用逗号串落库，保持和前端约定一致
        return StrUtil.join(",", assistantIds);
    }

    /**
     * 解析助教 id 串为去重后的 Long 列表。
     */
    private List<Long> parseAssistantIds(String teachAssistantIds) {
        if (StrUtil.isBlank(teachAssistantIds)) {
            return new ArrayList<>();
        }
        List<String> splitIds = StrUtil.splitTrim(teachAssistantIds, ',');
        if (CollUtil.isEmpty(splitIds)) {
            return new ArrayList<>();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String splitId : splitIds) {
            if (StrUtil.isBlank(splitId)) {
                continue;
            }
            uniqueIds.add(Long.valueOf(splitId));
        }
        return new ArrayList<>(uniqueIds);
    }

}
