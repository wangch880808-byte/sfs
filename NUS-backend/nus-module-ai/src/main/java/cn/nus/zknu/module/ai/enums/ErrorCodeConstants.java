package cn.nus.zknu.module.ai.enums;

import cn.nus.zknu.framework.common.exception.ErrorCode;

/**
 * AI 错误码枚举类
 * <p>
 * ai 系统，使用 1-040-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== API 密钥 1-040-000-000 ==========
    ErrorCode API_KEY_NOT_EXISTS = new ErrorCode(1_040_000_000, "API 密钥不存在");
    ErrorCode API_KEY_DISABLE = new ErrorCode(1_040_000_001, "API 密钥已禁用");
    ErrorCode API_KEY_REQUIRED = new ErrorCode(1_040_000_002, "API 密钥不能为空");
    ErrorCode API_KEY_IN_USE = new ErrorCode(1_040_000_003, "API 密钥已被模型引用，请先删除或调整关联模型");

    // ========== API 模型 1-040-001-000 ==========
    ErrorCode MODEL_NOT_EXISTS = new ErrorCode(1_040_001_000, "模型不存在");
    ErrorCode MODEL_DISABLE = new ErrorCode(1_040_001_001, "模型「{}」已禁用");
    ErrorCode MODEL_DEFAULT_NOT_EXISTS = new ErrorCode(1_040_001_002, "找不到默认模型");
    ErrorCode MODEL_USE_TYPE_ERROR = new ErrorCode(1_040_001_003, "该模型的模型类型不正确");
    ErrorCode MODEL_PLATFORM_MISMATCH = new ErrorCode(1_040_001_004, "模型平台必须与 API 密钥平台一致");
    ErrorCode BUSINESS_MODEL_NOT_CONFIGURED = new ErrorCode(1_040_001_005, "AI 业务「{}」尚未绑定模型");
    ErrorCode BUSINESS_MODEL_INVALID = new ErrorCode(1_040_001_006, "AI 业务只能绑定已启用的对话模型");
    ErrorCode MODEL_IN_USE = new ErrorCode(1_040_001_007, "模型已被 AI 业务引用，请先调整业务绑定");
    ErrorCode EDU_EXTERNAL_STUDENT_DATA_DISABLED = new ErrorCode(1_040_001_008,
            "学生数据外发开关未开启，当前教育 AI 能力不可用");
    ErrorCode EDU_AI_BUSY = new ErrorCode(1_040_001_009, "AI 服务繁忙，请稍后重试");
    ErrorCode EDU_AI_TIMEOUT = new ErrorCode(1_040_001_010, "AI 服务响应超时");

    // ========== API 聊天角色 1-040-002-000 ==========
    ErrorCode CHAT_ROLE_NOT_EXISTS = new ErrorCode(1_040_002_000, "聊天角色不存在");
    ErrorCode CHAT_ROLE_DISABLE = new ErrorCode(1_040_001_001, "聊天角色「{}」已禁用");

    // ========== API 聊天会话 1-040-003-000 ==========
    ErrorCode CHAT_CONVERSATION_NOT_EXISTS = new ErrorCode(1_040_003_000, "对话不存在");
    ErrorCode CHAT_CONVERSATION_MODEL_ERROR = new ErrorCode(1_040_003_001, "该聊天模型的配置不完整");

    // ========== API 聊天消息 1-040-004-000 ==========
    ErrorCode CHAT_MESSAGE_NOT_EXIST = new ErrorCode(1_040_004_000, "消息不存在");
    ErrorCode CHAT_STREAM_ERROR = new ErrorCode(1_040_004_001, "对话生成异常");

    // ========== API 绘画 1-040-005-000 ==========

    // ========== AI 工具 1-040-010-000 ==========
    ErrorCode TOOL_NOT_EXISTS = new ErrorCode(1_040_010_000, "工具不存在");
    ErrorCode TOOL_NAME_NOT_EXISTS = new ErrorCode(1_040_010_001, "工具「{}」找不到 Bean");

    // ========== AI 接入治理 1-040-011-000 ==========
    ErrorCode AI_QUOTA_EXCEEDED = new ErrorCode(1_040_011_000, "今日 AI 用量已达上限，明天再试或联系管理员调整");
    ErrorCode AI_CIRCUIT_OPEN = new ErrorCode(1_040_011_001, "AI 服务暂时不可用，请稍后再试");

    // ========== AI 提示词 1-040-012-000 ==========
    ErrorCode PROMPT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_040_012_000, "提示词版本不存在，刷新后再试");
    ErrorCode PROMPT_TEMPLATE_BUSINESS_INVALID = new ErrorCode(1_040_012_001, "提示词所属的 AI 业务不存在，刷新后再试");
    ErrorCode PROMPT_TEMPLATE_REQUIRED_FRAGMENT = new ErrorCode(1_040_012_002,
            "保存提示词没有成功：缺少必需内容「{}」，AI 结果会无法解析。补上后再保存");
    ErrorCode PROMPT_TEMPLATE_VERSION_CONFLICT = new ErrorCode(1_040_012_003,
            "保存提示词没有成功：同一业务刚有人保存了新版本。刷新后再试");
    ErrorCode PROMPT_TEMPLATE_TOO_LONG = new ErrorCode(1_040_012_004,
            "保存提示词没有成功：内容超过 {} 字。精简后再保存");

}
