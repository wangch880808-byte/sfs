export type CourseResourceLike = {
  id?: number;
  resourceName?: string;
  fileUrl?: string;
  fileType?: string;
  fileSize?: number;
  createTime?: Date | string;
};

export type ResourceKind =
  | 'archive'
  | 'audio'
  | 'excel'
  | 'image'
  | 'other'
  | 'pdf'
  | 'powerpoint'
  | 'text'
  | 'video'
  | 'word';

export type ResourceTypeCode =
  | 'archive'
  | 'audio'
  | 'excel'
  | 'image'
  | 'other'
  | 'pdf'
  | 'ppt'
  | 'text'
  | 'video'
  | 'word';

const KIND_LABEL_MAP: Record<ResourceKind, string> = {
  archive: '压缩包',
  audio: '音频',
  excel: '表格',
  image: '图片',
  other: '文件',
  pdf: 'PDF',
  powerpoint: 'PPT',
  text: '文本',
  video: '视频',
  word: 'Word',
};

const KIND_COLOR_MAP: Record<ResourceKind, string> = {
  archive: 'default',
  audio: 'orange',
  excel: 'green',
  image: 'cyan',
  other: 'default',
  pdf: 'red',
  powerpoint: 'gold',
  text: 'blue',
  video: 'purple',
  word: 'blue',
};

const KIND_ICON_MAP: Record<ResourceKind, string> = {
  archive: 'lucide:file-archive',
  audio: 'lucide:file-audio-2',
  excel: 'lucide:file-spreadsheet',
  image: 'lucide:file-image',
  other: 'lucide:file',
  pdf: 'lucide:file-text',
  powerpoint: 'lucide:presentation',
  text: 'lucide:file-type-2',
  video: 'lucide:file-video-2',
  word: 'lucide:file-text',
};

const MIME_KIND_RULES: Array<[RegExp, ResourceKind]> = [
  [/^video\//i, 'video'],
  [/^audio\//i, 'audio'],
  [/^image\//i, 'image'],
  [/pdf/i, 'pdf'],
  [/(msword|wordprocessingml)/i, 'word'],
  [/(presentationml|ms-powerpoint)/i, 'powerpoint'],
  [/(spreadsheetml|ms-excel)/i, 'excel'],
  [/(text\/|csv|json)/i, 'text'],
  [/(zip|rar|7z|tar|gzip)/i, 'archive'],
];

const EXT_KIND_RULES: Array<[RegExp, ResourceKind]> = [
  [/\.(mp4|mov|avi|mkv|webm)$/i, 'video'],
  [/\.(mp3|wav|ogg|m4a)$/i, 'audio'],
  [/\.(png|jpg|jpeg|gif|webp|bmp|svg)$/i, 'image'],
  [/\.(pdf)$/i, 'pdf'],
  [/\.(doc|docx)$/i, 'word'],
  [/\.(ppt|pptx)$/i, 'powerpoint'],
  [/\.(xls|xlsx)$/i, 'excel'],
  [/\.(txt|md|csv|json)$/i, 'text'],
  [/\.(zip|rar|7z|tar|gz)$/i, 'archive'],
];

const TYPE_CODE_KIND_MAP: Record<ResourceTypeCode, ResourceKind> = {
  archive: 'archive',
  audio: 'audio',
  excel: 'excel',
  image: 'image',
  other: 'other',
  pdf: 'pdf',
  ppt: 'powerpoint',
  text: 'text',
  video: 'video',
  word: 'word',
};

export const COURSE_RESOURCE_ACCEPT = [
  '.pdf',
  '.doc',
  '.docx',
  '.ppt',
  '.pptx',
  '.xls',
  '.xlsx',
  '.txt',
  '.csv',
  '.md',
  '.json',
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.webp',
  '.bmp',
  '.svg',
  '.mp3',
  '.wav',
  '.ogg',
  '.m4a',
  '.mp4',
  '.mov',
  '.avi',
  '.mkv',
  '.webm',
  '.m4v',
  '.mpeg',
  '.mpg',
  '.3gp',
  '.zip',
  '.7z',
  '.tar',
  '.gz',
  '.rar',
].join(',');

export const COURSE_RESOURCE_MAX_SIZE = 500 * 1024 * 1024;

function getFileExtension(resource: CourseResourceLike) {
  const candidate = String(resource.resourceName || resource.fileUrl || '').trim();
  const sanitized = candidate.split('?')[0]?.split('#')[0] || candidate;
  const match = sanitized.match(/(\.[A-Za-z0-9]+)$/);
  return match?.[1]?.toLowerCase() || '';
}

export function resolveResourceKind(resource: CourseResourceLike): ResourceKind {
  const mimeType = String(resource.fileType || '').trim();
  const normalizedTypeCode = normalizeResourceTypeCode(mimeType);
  if (normalizedTypeCode) {
    return TYPE_CODE_KIND_MAP[normalizedTypeCode];
  }
  for (const [pattern, kind] of MIME_KIND_RULES) {
    if (pattern.test(mimeType)) {
      return kind;
    }
  }

  const extension = getFileExtension(resource);
  for (const [pattern, kind] of EXT_KIND_RULES) {
    if (pattern.test(extension)) {
      return kind;
    }
  }

  return 'other';
}

export function getResourceKindLabel(kind: ResourceKind) {
  return KIND_LABEL_MAP[kind];
}

export function getResourceKindColor(kind: ResourceKind) {
  return KIND_COLOR_MAP[kind];
}

export function getResourceKindIcon(kind: ResourceKind) {
  return KIND_ICON_MAP[kind];
}

export function formatResourceSize(bytes?: number) {
  const size = Number(bytes || 0);
  if (!size) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function normalizeResourceName(fileName: string, fallback = '未命名资源') {
  const trimmed = String(fileName || '').trim();
  if (!trimmed) return fallback;
  const normalized = trimmed.replace(/\.[^.]+$/, '').trim();
  return normalized || trimmed || fallback;
}

export function inferResourceFileType(file: File) {
  if (file.type) return file.type;
  const extension = getFileExtension({ resourceName: file.name });
  switch (extension) {
    case '.pdf':
      return 'application/pdf';
    case '.doc':
      return 'application/msword';
    case '.docx':
      return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    case '.ppt':
      return 'application/vnd.ms-powerpoint';
    case '.pptx':
      return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
    case '.xls':
      return 'application/vnd.ms-excel';
    case '.xlsx':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    case '.txt':
      return 'text/plain';
    case '.csv':
      return 'text/csv';
    case '.jpg':
    case '.jpeg':
      return 'image/jpeg';
    case '.png':
      return 'image/png';
    case '.gif':
      return 'image/gif';
    case '.mp3':
      return 'audio/mpeg';
    case '.wav':
      return 'audio/wav';
    case '.mp4':
      return 'video/mp4';
    case '.mov':
      return 'video/quicktime';
    case '.avi':
      return 'video/x-msvideo';
    case '.mkv':
      return 'video/x-matroska';
    case '.webm':
      return 'video/webm';
    case '.m4v':
      return 'video/x-m4v';
    case '.mpeg':
    case '.mpg':
      return 'video/mpeg';
    case '.3gp':
      return 'video/3gpp';
    case '.zip':
      return 'application/zip';
    case '.rar':
      return 'application/x-rar-compressed';
    case '.7z':
      return 'application/x-7z-compressed';
    case '.tar':
      return 'application/x-tar';
    case '.gz':
      return 'application/gzip';
    default:
      return 'application/octet-stream';
  }
}

export function normalizeResourceTypeCode(raw?: string): ResourceTypeCode | '' {
  const value = String(raw || '').trim().toLowerCase();
  if (!value) return '';
  switch (value) {
    case 'archive':
    case 'audio':
    case 'excel':
    case 'image':
    case 'other':
    case 'pdf':
    case 'ppt':
    case 'text':
    case 'video':
    case 'word': {
      return value;
    }
    case 'powerpoint': {
      return 'ppt';
    }
    default:
      break;
  }

  if (/^video\//i.test(value) || /\.(mp4|mov|avi|mkv|webm)$/i.test(value)) {
    return 'video';
  }
  if (/^audio\//i.test(value) || /\.(mp3|wav|ogg|m4a)$/i.test(value)) {
    return 'audio';
  }
  if (/^image\//i.test(value) || /\.(png|jpg|jpeg|gif|webp|bmp|svg)$/i.test(value)) {
    return 'image';
  }
  if (/pdf/i.test(value) || /\.pdf$/i.test(value)) {
    return 'pdf';
  }
  if (/(msword|wordprocessingml)/i.test(value) || /\.(doc|docx)$/i.test(value)) {
    return 'word';
  }
  if (/(presentationml|ms-powerpoint)/i.test(value) || /\.(ppt|pptx)$/i.test(value)) {
    return 'ppt';
  }
  if (/(spreadsheetml|ms-excel)/i.test(value) || /\.(xls|xlsx)$/i.test(value)) {
    return 'excel';
  }
  if (/(text\/|csv|json)/i.test(value) || /\.(txt|md|csv|json)$/i.test(value)) {
    return 'text';
  }
  if (/(zip|rar|7z|tar|gzip)/i.test(value) || /\.(zip|rar|7z|tar|gz)$/i.test(value)) {
    return 'archive';
  }
  return '';
}

export function inferResourceTypeCode(file: File): ResourceTypeCode {
  return normalizeResourceTypeCode(file.type || file.name) || 'other';
}

export function canPreviewResource(resource: CourseResourceLike) {
  const kind = resolveResourceKind(resource);
  return (
    kind === 'audio' ||
    kind === 'excel' ||
    kind === 'image' ||
    kind === 'pdf' ||
    kind === 'powerpoint' ||
    kind === 'text' ||
    kind === 'video' ||
    kind === 'word'
  );
}

export function getPreviewActionLabel(resource: CourseResourceLike) {
  const kind = resolveResourceKind(resource);
  if (kind === 'video') return '在线播放';
  if (kind === 'audio') return '在线收听';
  return '在线预览';
}

export function canUseOfficeViewer(url?: string) {
  const source = String(url || '').trim();
  if (!/^https?:\/\//i.test(source)) return false;
  return !/^(https?:\/\/)(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/i.test(
    source,
  );
}

export function buildOfficePreviewUrl(url?: string) {
  const source = String(url || '').trim();
  if (!canUseOfficeViewer(source)) return '';
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(source)}`;
}

export function triggerResourceDownload(resource: CourseResourceLike) {
  const fileUrl = String(resource.fileUrl || '').trim();
  if (!fileUrl) return false;
  const link = document.createElement('a');
  link.href = fileUrl;
  link.download =
    resource.resourceName ||
    fileUrl.split('/').pop()?.split('?')[0] ||
    'resource';
  link.target = '_blank';
  link.rel = 'noopener';
  document.body.append(link);
  link.click();
  link.remove();
  return true;
}
