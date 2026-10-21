package cn.nus.zknu.module.edu.enums;

import cn.nus.zknu.framework.common.exception.ErrorCode;

/**
 * EDU 错误码枚举类
 * <p>
 * edu 系统，使用 1-050-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 班级模块 1-050-000-000 ==========
    ErrorCode CLASS_NOT_EXISTS = new ErrorCode(1_050_000_000, "班级不存在");
    ErrorCode CLASS_STUDENT_NOT_EXISTS = new ErrorCode(1_050_000_001, "学生不存在");
    ErrorCode CLASS_STUDENT_NOT_STUDENT = new ErrorCode(1_050_000_002, "存在非学生用户，无法加入班级");
    ErrorCode CLASS_STUDENT_IS_DISABLED = new ErrorCode(1_050_000_003, "学生「{}」已被禁用，无法加入班级");
    ErrorCode CLASS_STUDENT_REMARK_REQUIRED = new ErrorCode(1_050_000_004, "当前学生与班级信息不一致，请填写备注说明");

    // ========== 班级课程关联模块 1-050-001-000 ==========
    ErrorCode CLASS_COURSE_NOT_EXISTS = new ErrorCode(1_050_001_000, "班级课程关联不存在");
    ErrorCode CLASS_COURSE_EXISTS = new ErrorCode(1_050_001_001, "班级课程关联已存在");
    ErrorCode CLASS_COURSE_BIND_PARAM_INVALID = new ErrorCode(1_050_001_002, "班级课程绑定参数不完整");
    ErrorCode CLASS_COURSE_CLAIM_OFFLINE = new ErrorCode(1_050_001_007, "只有已上线的公共课可以认领班级");
    ErrorCode CLASS_COURSE_CLAIM_TAKEN = new ErrorCode(1_050_001_008, "班级「{}」已被{}老师认领");
    ErrorCode CLASS_COURSE_CLAIM_TEACHER_REQUIRED = new ErrorCode(1_050_001_006, "只有教师或管理员可以认领班级");
    ErrorCode CLASS_COURSE_ARCHIVED = new ErrorCode(1_050_001_003, "班课已封存，不能布置任务");
    ErrorCode COURSE_ARCHIVED = new ErrorCode(1_050_001_004, "课程已封存，不能布置任务");
    ErrorCode CLASS_ARCHIVED = new ErrorCode(1_050_001_005, "班级已封存，不能布置任务");

    // ========== 课程资源模块 1-050-003-000 ==========
    ErrorCode COURSE_NOT_EXISTS = new ErrorCode(1_050_002_000, "课程不存在");
    ErrorCode COURSE_RESOURCE_NOT_EXISTS = new ErrorCode(1_050_003_000, "课程资源不存在");
    ErrorCode COURSE_RESOURCE_FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_050_003_001, "不支持的文件类型");
    ErrorCode COURSE_RESOURCE_FILE_SIZE_EXCEED = new ErrorCode(1_050_003_002, "文件大小超过 500 MB 限制");
    ErrorCode COURSE_RESOURCE_FILE_INVALID = new ErrorCode(1_050_003_003, "课程资源文件不合法：{}");

    // ========== 实训任务模块 1-050-004-000 ==========
    ErrorCode HOMEWORK_NOT_EXISTS = new ErrorCode(1_050_004_000, "实训任务不存在");
    ErrorCode HOMEWORK_SCORE_INVALID = new ErrorCode(1_050_004_001, "分数配置不合法，及格线不能超过满分值");
    ErrorCode HOMEWORK_AI_CREATE_PREVIEW_NOT_EXISTS = new ErrorCode(1_050_004_002, "AI 新建任务预览不存在");
    ErrorCode HOMEWORK_AI_CREATE_PREVIEW_EXPIRED = new ErrorCode(1_050_004_003, "AI 新建任务预览已过期，请重新生成");
    ErrorCode HOMEWORK_AI_CREATE_PREVIEW_CONFIRMED = new ErrorCode(1_050_004_004, "AI 新建任务预览已确认，请勿重复新建");
    ErrorCode HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID = new ErrorCode(1_050_004_005, "AI 新建任务预览题目不合法");
    ErrorCode HOMEWORK_ASSIGNEE_SCOPE_LOCKED = new ErrorCode(1_050_004_006, "任务发布后不允许修改课程或班级范围");
    ErrorCode HOMEWORK_AI_CREATE_TASK_NOT_EXISTS = new ErrorCode(1_050_004_007, "AI 新建任务不存在");
    ErrorCode HOMEWORK_AI_CREATE_TASK_NOT_READY = new ErrorCode(1_050_004_008, "AI 新建任务预览尚未生成完成");
    ErrorCode HOMEWORK_STATUS_DRAFT_FORBIDDEN = new ErrorCode(1_050_004_009, "已发布任务不能改回草稿");
    ErrorCode HOMEWORK_STATUS_INVALID = new ErrorCode(1_050_004_010, "任务状态不允许这样修改");
    ErrorCode HOMEWORK_ARCHIVED = new ErrorCode(1_050_004_011, "任务已封存，请先恢复");
    ErrorCode HOMEWORK_DRAFT_FORBIDDEN = new ErrorCode(1_050_004_012, "任务不能保存为草稿");
    ErrorCode HOMEWORK_CLASS_COURSE_MISMATCH = new ErrorCode(1_050_004_013, "所选班级与课程不匹配，请刷新后重试");
    ErrorCode HOMEWORK_DEADLINE_REQUIRED = new ErrorCode(1_050_004_014, "截止时间不能为空");
    ErrorCode HOMEWORK_REMOVE_IDS_INVALID = new ErrorCode(1_050_004_015, "请选择要删除的任务，一次最多删除 100 个");
    // 个别延期与催交 1-050-004-100 ~ 149
    ErrorCode HOMEWORK_EXTENSION_STATUS_INVALID = new ErrorCode(1_050_004_100,
            "设置个别延期没有成功：只有已发布的任务可以延期。请先发布任务，或恢复已封存的任务");
    ErrorCode HOMEWORK_EXTENSION_NO_DEADLINE = new ErrorCode(1_050_004_101,
            "设置个别延期没有成功：这个任务没有设置截止时间，学生随时可以提交，不需要延期");
    ErrorCode HOMEWORK_EXTENSION_DEADLINE_INVALID = new ErrorCode(1_050_004_102,
            "设置个别延期没有成功：延期后的截止时间要晚于任务截止时间 {} 和当前时间。请重新选择时间");
    ErrorCode HOMEWORK_EXTENSION_STUDENT_NOT_ASSIGNED = new ErrorCode(1_050_004_103,
            "设置个别延期没有成功：有 {} 名学生不在这个任务的学生名单里。请刷新名单后重新选择");
    ErrorCode HOMEWORK_EXTENSION_STUDENT_SUBMITTED = new ErrorCode(1_050_004_104,
            "设置个别延期没有成功：有 {} 名学生已经提交，不需要延期；需要重做时请在批改页打回并设置重做截止时间");
    ErrorCode HOMEWORK_EXTENSION_NOT_EXISTS = new ErrorCode(1_050_004_105,
            "移除个别延期没有成功：这条延期已经不存在，可能已被移除。请刷新后查看");
    ErrorCode HOMEWORK_REMIND_STATUS_INVALID = new ErrorCode(1_050_004_106,
            "催交没有发出：只有已发布、学生还能提交的任务可以催交");
    ErrorCode HOMEWORK_REMIND_TOO_FREQUENT = new ErrorCode(1_050_004_107,
            "催交没有发出：这个任务 {} 分钟内已经催交过。请在 {} 之后再试");

    // ========== 任务记录模块 1-050-005-000 ==========
    ErrorCode HOMEWORK_RECORD_NOT_EXISTS = new ErrorCode(1_050_005_000, "任务记录不存在");
    ErrorCode HOMEWORK_RECORD_DEADLINE_EXCEEDED = new ErrorCode(1_050_005_001, "已超过截止时间，无法提交");
    ErrorCode HOMEWORK_RECORD_ALREADY_SUBMITTED = new ErrorCode(1_050_005_002, "该任务已提交，请勿重复提交");
    ErrorCode HOMEWORK_RECORD_SCORE_INVALID = new ErrorCode(1_050_005_003, "评分范围为 0-100");
    ErrorCode HOMEWORK_RECORD_UPDATE_FORBIDDEN = new ErrorCode(1_050_005_004, "仅打回重做的任务允许重新提交");
    ErrorCode HOMEWORK_ATTACHMENT_INVALID = new ErrorCode(1_050_005_005, "提交附件不合法：{}");
    ErrorCode HOMEWORK_ATTACHMENT_REQUIRED = new ErrorCode(1_050_005_006, "请上传任务要求的附件");
    ErrorCode HOMEWORK_ATTACHMENT_TYPE_INVALID = new ErrorCode(1_050_005_007, "附件类型不符合当前任务要求");
    ErrorCode HOMEWORK_ATTACHMENT_SIZE_EXCEEDED = new ErrorCode(1_050_005_008, "附件大小超过限制");
    ErrorCode HOMEWORK_RECORD_REDO_DEADLINE_REQUIRED = new ErrorCode(1_050_005_009, "任务已截止，打回时需设置重做截止时间");
    ErrorCode HOMEWORK_RECORD_REDO_DEADLINE_INVALID = new ErrorCode(1_050_005_010, "重做截止时间必须晚于当前时间");
    ErrorCode HOMEWORK_RECORD_CORRECT_STATUS_INVALID = new ErrorCode(1_050_005_011, "当前提交状态不允许批改");
    ErrorCode HOMEWORK_RECORD_REDO_STATUS_INVALID = new ErrorCode(1_050_005_012, "当前提交状态不允许打回");
    // 批改可靠性（V1.0.1.6 第二轮）：条件更新、乐观校验与撤销
    ErrorCode HOMEWORK_RECORD_STATE_CHANGED = new ErrorCode(1_050_005_100, "状态已变化，请刷新后再试");
    ErrorCode HOMEWORK_RECORD_CONFLICT = new ErrorCode(1_050_005_101, "这份提交已被他人更新，请刷新后再看");
    ErrorCode HOMEWORK_GRADING_UNDO_NOT_EXISTS = new ErrorCode(1_050_005_102,
            "撤销没有完成：找不到这次操作，请刷新后查看当前状态");
    ErrorCode HOMEWORK_GRADING_UNDO_EXPIRED = new ErrorCode(1_050_005_103,
            "撤销没有完成：已超过可撤销时间，通知已发给学生。需要更改请重新批改或打回");
    ErrorCode HOMEWORK_GRADING_UNDO_CHANGED = new ErrorCode(1_050_005_104,
            "撤销没有完成：这份提交在你保存后又有变化，请刷新后再看");

    // ========== 学生通知模块 1-050-006-000 ==========
    ErrorCode NOTIFICATION_NOT_EXISTS = new ErrorCode(1_050_006_000, "通知不存在");

    // ========== 学生课程模块 1-050-007-000 ==========
    ErrorCode STUDENT_COURSE_NOT_EXISTS = new ErrorCode(1_050_007_000, "课程不存在或未分配");
    ErrorCode STUDENT_COURSE_LEVEL_LOCKED = new ErrorCode(1_050_007_001, "该等级尚未解锁，请先完成上一等级任务");
    ErrorCode STUDENT_IDENTITY_REQUIRED = new ErrorCode(1_050_007_002, "当前账号不是学生账号");
    ErrorCode STUDENT_COURSE_RESOURCE_NOT_EXISTS = new ErrorCode(1_050_007_003, "课程资源不存在或无权访问");
    // 等级闯关与归档只读（1-050-007-100 ~ 199）
    ErrorCode HOMEWORK_LEVEL_LOCKED_AFTER_SUBMISSION = new ErrorCode(1_050_007_100,
            "没有保存：已有学生提交这个任务，改等级会打乱等级闯关进度。如需换等级，请新建一个任务");
    ErrorCode HOMEWORK_REDO_SCOPE_ARCHIVED = new ErrorCode(1_050_007_101,
            "没有打回：任务所在的{}已封存，封存后的成绩只能查看。如需打回，请先恢复{}");
    ErrorCode MESSAGE_SCOPE_ARCHIVED = new ErrorCode(1_050_007_102,
            "没有发送：这条留言所在的{}已封存，只能查看。如需继续回复，请先恢复{}");

    // ========== 学生任务模块 1-050-008-000 ==========
    ErrorCode STUDENT_HOMEWORK_NOT_EXISTS = new ErrorCode(1_050_008_000, "任务不存在");
    ErrorCode STUDENT_HOMEWORK_NOT_IN_CLASS = new ErrorCode(1_050_008_001, "你不属于该任务所在班级");
    ErrorCode STUDENT_HOMEWORK_NOT_ASSIGNED = new ErrorCode(1_050_008_002, "你未被分配该任务");
    // V1.0.1.7 S8：附件上限改为可配置（edu.homework.attachment.*），提示里写明当前上限
    ErrorCode STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE = new ErrorCode(1_050_008_100,
            "附件没有通过检查：单个附件不能超过 {} MB。请压缩后重新上传");
    ErrorCode STUDENT_HOMEWORK_ATTACHMENT_TOTAL_TOO_LARGE = new ErrorCode(1_050_008_101,
            "任务没有提交成功：附件合计不能超过 {} MB。请删除或压缩部分附件后再提交");
    ErrorCode STUDENT_HOMEWORK_ATTACHMENT_TOO_MANY = new ErrorCode(1_050_008_102,
            "任务没有提交成功：附件不能超过 {} 个。请删除多余的附件后再提交");
    ErrorCode STUDENT_HOMEWORK_ATTACHMENT_EMPTY = new ErrorCode(1_050_008_103,
            "附件没有上传成功：文件是空的。请检查文件后重新选择");

    // ========== 学生个人中心 1-050-009-000 ==========
    ErrorCode STUDENT_PASSWORD_OLD_INCORRECT = new ErrorCode(1_050_009_000, "原密码不正确");
    ErrorCode STUDENT_PASSWORD_NEW_INVALID = new ErrorCode(1_050_009_001, "新密码长度至少 8 位，需包含字母和数字");

    // ========== AI评阅模块 1-050-010-000 ==========
    ErrorCode AI_REVIEW_RECORD_NOT_EXISTS = new ErrorCode(1_050_010_000, "任务记录不存在");
    ErrorCode AI_REVIEW_ALREADY_CORRECTED = new ErrorCode(1_050_010_001, "该任务已批改");
    ErrorCode AI_REVIEW_TASK_NOT_EXISTS = new ErrorCode(1_050_010_002, "批改任务不存在");
    ErrorCode AI_REVIEW_TIMEOUT = new ErrorCode(1_050_010_003, "AI 初评超时");
    ErrorCode AI_REVIEW_MODEL_ERROR = new ErrorCode(1_050_010_004, "AI 模型调用失败");
    ErrorCode AI_REVIEW_ANALYSIS_NO_STUDENTS = new ErrorCode(1_050_010_005, "当前班级暂无学生，无法生成 AI 研判");
    ErrorCode AI_REVIEW_DIRECT_APPLY_DISABLED = new ErrorCode(1_050_010_007, "AI 初评仅作为草案，请确认后通过保存批改形成最终成绩");
    ErrorCode AI_REVIEW_STATUS_INVALID = new ErrorCode(1_050_010_008, "当前任务状态不允许生成 AI 初评");
    ErrorCode AI_REVIEW_FEEDBACK_INVALID = new ErrorCode(1_050_010_009, "AI 评分反馈类型不合法");
    ErrorCode HOMEWORK_SCORE_PERCENT_ONLY = new ErrorCode(1_050_010_010, "当前版本仅支持百分制任务，满分值必须为 100");
    ErrorCode AI_REVIEW_BUSY = new ErrorCode(1_050_010_011, "AI 初评任务队列已满，请稍后重试");
    ErrorCode AI_REVIEW_DISABLED = new ErrorCode(1_050_010_012, "AI 初评未启用");
    ErrorCode AI_ANALYSIS_SCOPE_INVALID = new ErrorCode(1_050_010_013, "所选课程、班级、学生或任务不在当前教师的数据范围内");
    ErrorCode AI_REVIEW_FEEDBACK_NO_DRAFT = new ErrorCode(1_050_010_014, "该提交尚无 AI 初评草案，无法反馈");
    // S2b：AI 新建任务单题重新生成 1_050_010_150–199
    ErrorCode HOMEWORK_AI_CREATE_REGENERATE_BUSY = new ErrorCode(1_050_010_150,
            "重新生成没有开始：AI 任务排队已满。稍后再试");
    ErrorCode HOMEWORK_AI_CREATE_REGENERATE_CONFLICT = new ErrorCode(1_050_010_151,
            "重新生成没有开始：预览刚被其他操作修改。刷新后再试");

    // ========== 统计分析模块 1-050-011-000 ==========
    ErrorCode STATISTICS_REPORT_EXPORT_FAILED = new ErrorCode(1_050_011_004, "生成教学报表失败");
    ErrorCode STATISTICS_REPORT_FONT_NOT_FOUND = new ErrorCode(1_050_011_006, "服务器缺少中文字体，无法生成 PDF 报表");

    // ========== 题库模块 1-050-012-000 ==========
    ErrorCode QUESTION_BANK_IMPORT_ERROR = new ErrorCode(1_050_012_000, "题库导入失败");
    ErrorCode QUESTION_BANK_NOT_ENOUGH = new ErrorCode(1_050_012_001, "题库题目数量不足，当前条件下仅剩 {} 题");
    ErrorCode QUESTION_BANK_NOT_EXISTS = new ErrorCode(1_050_012_002, "题库题目不存在");
    ErrorCode QUESTION_BANK_INDICATOR_MISMATCH = new ErrorCode(1_050_012_003, "所选二级能力指标不属于当前课程的一级指标");

    // ========== 学生导入 1-050-013-000 ==========
    ErrorCode STUDENT_IMPORT_ERROR = new ErrorCode(1_050_013_000, "学生导入失败");
    ErrorCode STUDENT_DUPLICATE = new ErrorCode(1_050_013_001, "学号已存在");

    // ========== 留言模块 1-050-014-000 ==========
    ErrorCode MESSAGE_NOT_EXISTS = new ErrorCode(1_050_014_000, "留言不存在");
    ErrorCode MESSAGE_CONVERSATION_CLOSED = new ErrorCode(1_050_014_001, "留言已关闭，请联系教师重新开启");
    ErrorCode MESSAGE_STUDENT_COURSE_FORBIDDEN = new ErrorCode(1_050_014_002, "无权访问该班课留言");
    ErrorCode MESSAGE_CONTENT_INVALID = new ErrorCode(1_050_014_003, "留言内容长度须为 1 至 1000 字");

    // ========== 校园资讯模块 1-050-015-000 ==========
    ErrorCode NEWS_NOT_EXISTS = new ErrorCode(1_050_015_000, "资讯不存在");

    // ========== 学期管理模块 1-050-016-000 ==========
    ErrorCode SEMESTER_DATE_RANGE_INVALID = new ErrorCode(1_050_016_000, "学期起止日期不合法，开始日期不能晚于结束日期");
    ErrorCode SEMESTER_CONFIG_SAVE_FAILED = new ErrorCode(1_050_016_001, "保存当前学期配置失败");

    // ========== 数据导入批次 1-050-017-000 ==========
    ErrorCode IMPORT_FILE_REQUIRED = new ErrorCode(1_050_017_000, "请选择要导入的 Excel 文件");
    ErrorCode IMPORT_FILE_TYPE_INVALID = new ErrorCode(1_050_017_001, "仅支持 .xlsx 导入文件");
    ErrorCode IMPORT_FILE_SIZE_EXCEEDED = new ErrorCode(1_050_017_002, "导入文件不能超过 20 MB");
    ErrorCode IMPORT_ROW_LIMIT_EXCEEDED = new ErrorCode(1_050_017_003, "单次导入不能超过 10000 行");
    ErrorCode IMPORT_HANDLER_NOT_FOUND = new ErrorCode(1_050_017_004, "业务类型「{}」或模板版本「{}」不受支持");
    ErrorCode IMPORT_BATCH_NOT_FOUND = new ErrorCode(1_050_017_005, "导入批次不存在");
    ErrorCode IMPORT_BATCH_HAS_ERRORS = new ErrorCode(1_050_017_006, "预检存在错误，修正后请重新上传");
    ErrorCode IMPORT_BATCH_EXPIRED = new ErrorCode(1_050_017_007, "导入批次已过期，请重新上传预检");
    ErrorCode IMPORT_BATCH_STATUS_INVALID = new ErrorCode(1_050_017_008, "导入批次当前状态不允许确认");
    ErrorCode IMPORT_OPTIONS_INVALID = new ErrorCode(1_050_017_009, "导入选项不是合法的 JSON 对象");
    ErrorCode IMPORT_PREVIEW_INVALID = new ErrorCode(1_050_017_010, "导入预检结果格式不合法");
    ErrorCode IMPORT_ERROR_FILE_NOT_FOUND = new ErrorCode(1_050_017_011, "当前批次没有错误回执");
    ErrorCode IMPORT_FILE_EMPTY = new ErrorCode(1_050_017_012, "导入文件没有可处理的数据行");
    ErrorCode IMPORT_DATA_CHANGED = new ErrorCode(1_050_017_013, "业务数据已在预检后发生变化，请重新上传预检");
    ErrorCode IMPORT_INITIAL_PASSWORD_INVALID = new ErrorCode(1_050_017_014, "新建账号初始密码长度必须为 4-16 位");
    ErrorCode IMPORT_EXECUTOR_BUSY = new ErrorCode(1_050_017_015, "当前导入任务较多，请稍后再试");
    // S5（V1.0.1.6 第二轮）成绩册：1-050-017-100 ~ 149
    ErrorCode GRADEBOOK_HEADER_INVALID = new ErrorCode(1_050_017_100,
            "成绩册没有读取成功：表头缺少「{}」。从任务的「导出成绩册」重新下载后再填写");
    ErrorCode GRADEBOOK_RECORD_CHANGED = new ErrorCode(1_050_017_101,
            "这批成绩没有导入：第 {} 行学生的提交在预检后有了变化（可能已被批改或重新提交）。重新导出成绩册后再导入");

    // ========== 学期选择与学期收尾 1-050-018-000 ==========
    ErrorCode SEMESTER_CURRENT_NOT_SET = new ErrorCode(1_050_018_000,
            "操作没有完成：当前学期还没有设置。先在「学期管理」里设置当前学期。");
    ErrorCode SEMESTER_NOT_CONFIGURED = new ErrorCode(1_050_018_001,
            "操作没有完成：学期「{}」不在已配置的学期里。从学期列表里重新选择。");
    ErrorCode SEMESTER_WRAP_UP_EMPTY = new ErrorCode(1_050_018_010,
            "学期收尾没有开始：没有选择要封存的班级课程或班级。");
    ErrorCode SEMESTER_WRAP_UP_CLASS_COURSE_INVALID = new ErrorCode(1_050_018_011,
            "学期收尾没有完成：有班级课程不属于学期「{}」或已经封存。刷新预览后重新选择。");
    ErrorCode SEMESTER_WRAP_UP_CLASS_INVALID = new ErrorCode(1_050_018_012,
            "学期收尾没有完成：有班级在其他学期还有班级课程，不能整体封存。刷新预览后重新选择。");

}
