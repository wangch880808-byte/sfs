/**
 * 学生提交附件的规则（V1.0.1.7 S8）：上限来自任务详情的 attachmentLimits（后端读 infra_config），
 * 没有返回时用默认值；上传前在前端先查个数、大小和类型，最终仍以后端校验为准。
 */

export type AttachmentCategory = 'audio' | 'document' | 'image' | 'video';

export interface AttachmentLimits {
  /** 允许的类型：image、audio、video；为空表示不限 */
  allowedTypes: string[];
  maxCount: number;
  maxFileSizeMb: number;
  maxTotalSizeMb: number;
}

/** 任务详情里与附件有关的字段（attachmentLimits 由后端返回，旧版后端没有） */
export interface AttachmentRuleSource {
  attachmentLimits?: null | {
    allowedTypes?: null | string[];
    maxCount?: null | number;
    maxFileSizeMb?: null | number;
    maxTotalSizeMb?: null | number;
  };
  requiredAttachmentTypesJson?: null | string;
  submissionMode?: null | string;
}

/** 与后端 HomeworkAttachmentService 的默认值一致 */
export const DEFAULT_ATTACHMENT_LIMITS = {
  maxCount: 10,
  maxFileSizeMb: 100,
  maxTotalSizeMb: 300,
} as const;

const MB = 1024 * 1024;

const TYPE_LABELS: Record<string, string> = {
  audio: '音频',
  image: '图片',
  video: '视频',
};

const TYPE_ACCEPT: Record<string, string> = {
  audio: 'audio/*',
  image: 'image/*',
  video: 'video/*',
};

/** 按 MIME 类型判断，浏览器没给类型时按扩展名判断（后端也按文件内容和文件名识别） */
export function getAttachmentCategory(file: {
  name?: string;
  type?: string;
  url?: string;
}): AttachmentCategory {
  const lowerName = String(file.name || file.url || '').toLowerCase();
  const mimeType = String(file.type || '').toLowerCase();
  if (
    mimeType.startsWith('audio/') ||
    /\.(?:mp3|wav|m4a|aac|amr|ogg)(?:\?|$)/.test(lowerName)
  ) {
    return 'audio';
  }
  if (
    mimeType.startsWith('video/') ||
    /\.(?:mp4|mov|m4v|avi|webm|mkv)(?:\?|$)/.test(lowerName)
  ) {
    return 'video';
  }
  if (
    mimeType.startsWith('image/') ||
    /\.(?:png|jpe?g|gif|bmp|webp|svg)(?:\?|$)/.test(lowerName)
  ) {
    return 'image';
  }
  return 'document';
}

export function getAttachmentCategoryLabel(category: AttachmentCategory) {
  return TYPE_LABELS[category] ?? '文档';
}

/** 任务要求的附件类型：任务配置优先，没有配置时按提交方式推断 */
export function resolveRequiredTypes(source: AttachmentRuleSource): string[] {
  const raw = source.requiredAttachmentTypesJson;
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => String(item).trim().toLowerCase())
          .filter(Boolean);
      }
    } catch {
      // 配置格式不对时按提交方式推断，提交时由后端报错
    }
  }
  switch (source.submissionMode) {
    case 'audio': {
      return ['audio'];
    }
    case 'image': {
      return ['image'];
    }
    case 'mixed': {
      return ['image', 'audio', 'video'];
    }
    case 'video': {
      return ['video'];
    }
    default: {
      return [];
    }
  }
}

function positiveInt(value: null | number | undefined, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : fallback;
}

/** 当前任务的附件上限与允许的类型 */
export function resolveAttachmentLimits(
  source: AttachmentRuleSource,
): AttachmentLimits {
  const limits = source.attachmentLimits ?? {};
  const allowedTypes = Array.isArray(limits.allowedTypes)
    ? limits.allowedTypes
        .map((item) => String(item).trim().toLowerCase())
        .filter(Boolean)
    : resolveRequiredTypes(source);
  return {
    allowedTypes,
    maxCount: positiveInt(limits.maxCount, DEFAULT_ATTACHMENT_LIMITS.maxCount),
    maxFileSizeMb: positiveInt(
      limits.maxFileSizeMb,
      DEFAULT_ATTACHMENT_LIMITS.maxFileSizeMb,
    ),
    maxTotalSizeMb: positiveInt(
      limits.maxTotalSizeMb,
      DEFAULT_ATTACHMENT_LIMITS.maxTotalSizeMb,
    ),
  };
}

/** “图片 / 音频” */
export function formatAllowedTypes(types: string[]) {
  return types.map((item) => TYPE_LABELS[item] || item).join(' / ');
}

/** 选择文件对话框的 accept；不限类型时返回空字符串 */
export function buildUploadAccept(types: string[]) {
  return types
    .map((item) => TYPE_ACCEPT[item])
    .filter(Boolean)
    .join(',');
}

/** 提示文字里的上限：“最多 10 个附件，单个不超过 100 MB，合计不超过 300 MB。” */
export function describeAttachmentLimits(limits: AttachmentLimits) {
  return `最多 ${limits.maxCount} 个附件，单个不超过 ${limits.maxFileSizeMb} MB，合计不超过 ${limits.maxTotalSizeMb} MB。`;
}

export interface CandidateFile {
  name: string;
  size: number;
  type?: string;
}

export interface ExistingAttachment {
  /** 已知大小（字节）；服务器上的旧附件读不到大小，按 0 计 */
  size?: number;
}

/**
 * 检查一次选中的一批文件：按顺序逐个判断，通过的才计入个数和合计大小。
 * 返回与 batch 一一对应的结果：null 表示可以上传，字符串是不能上传的原因。
 * existing 是列表里已有的附件（包括上传中和上传失败的，它们也占名额）。
 */
export function checkAttachmentBatch(
  batch: CandidateFile[],
  existing: ExistingAttachment[],
  limits: AttachmentLimits,
): Array<null | string> {
  let count = existing.length;
  let totalBytes = existing.reduce(
    (sum, item) => sum + Math.max(Number(item.size), 0),
    0,
  );
  return batch.map((file) => {
    const name = `「${file.name}」`;
    if (
      limits.allowedTypes.length > 0 &&
      !limits.allowedTypes.includes(getAttachmentCategory(file))
    ) {
      return `${name}没有添加：这个任务只能上传${formatAllowedTypes(limits.allowedTypes)}。`;
    }
    if (file.size === 0) {
      return `${name}没有添加：文件是空的，请检查后重新选择。`;
    }
    if (file.size > limits.maxFileSizeMb * MB) {
      return `${name}没有添加：单个附件不能超过 ${limits.maxFileSizeMb} MB，请压缩后再上传。`;
    }
    if (count + 1 > limits.maxCount) {
      return `${name}没有添加：最多上传 ${limits.maxCount} 个附件，可以先删掉不需要的。`;
    }
    if (totalBytes + file.size > limits.maxTotalSizeMb * MB) {
      return `${name}没有添加：附件合计不能超过 ${limits.maxTotalSizeMb} MB，可以压缩或删掉部分附件。`;
    }
    count += 1;
    totalBytes += file.size;
    return null;
  });
}
