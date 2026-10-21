/**
 * 界面文案规则（V1.0.1.6，规则版本 nus-ui-copy-rules-v1）。依据 docs/ui-copy.md《师砺平台界面术语表与文案规范》，
 * 从 ScholarOS 的界面文案扫描（tests/copy）移植，按师砺平台的术语与范围改写。改规范时同步改这里。
 *
 * - 扫描与检查在 copyScan.ts，测试在 copy-scan.test.ts（随 pnpm test:unit 运行）；
 * - 清单与基线：node apps/web-antd/tests/copy/inventory.ts [输出目录] [--write-baseline]。
 *
 * 本文件只放规则数据，不依赖其他模块；Node 直接运行（类型剥离），只能写可擦除的 TypeScript。
 */
// cspell:ignore Ollama ADHMQSW Yadhmosw

export const COPY_RULES_VERSION = 'nus-ui-copy-rules-v1';

// ---------------------------------------------------------------- 扫描范围

/** 应用源码目录。扫描结果里的文件路径都相对前端仓库根（NUS-front），分隔符统一为 / */
export const APP_SRC = 'apps/web-antd/src';

/**
 * 扫描范围（规范 §4）：页面、组件、布局、工具函数、路由、表格与表单适配、登录状态里的 .vue 与 .ts。
 * packages/ 是框架代码，不扫；接口定义（api/）只有请求与类型，不扫。
 */
export const SCAN_DIRS = [
  'views',
  'components',
  'layouts',
  'utils',
  'router',
  'adapter',
  'store',
].map((dir) => `${APP_SRC}/${dir}`);

/** 中文语言包：每个字符串值算一条脚本文案，位置写语言包键（如 page.auth.login） */
export const LOCALE_DIR = `${APP_SRC}/locales/langs/zh-CN`;

/** 不扫的文件：测试与类型声明 */
export const SKIP_FILES: RegExp[] = [/\.(?:spec|test)\.ts$/, /\.d\.ts$/];

// ---------------------------------------------------------------- 英文

/** 保留英文的产品与技术名（规范 §2），按原样大小写匹配；「OAuth 2.0」里的版本号是数字，不算词 */
export const ALLOWED_ENGLISH = new Set<string>([
  'AI',
  'API',
  'App',
  // 规范 §2 补充（V1.0.1.6 定稿）：产品名、文件格式与通行的技术名
  'Cron',
  'CSS',
  'DeepSeek',
  'Druid',
  'Excel',
  'H5',
  'HTML',
  'IP',
  'Java',
  'JavaScript',
  'JSON',
  'Markdown',
  'MCP',
  'OAuth',
  'Office',
  'Ollama',
  'OpenAI',
  'OpenID',
  'PDF',
  'PPT',
  'Redis',
  'SkyWalking',
  'SQL',
  'UnionID',
  'URL',
  'Web',
  'Word',
  'XLSX',
]);

/** 单位（规范 §3.6、§3.7 第 24 条：数字与单位之间空一格，如「20 MB」） */
export const ALLOWED_UNITS = new Set<string>(['GB', 'KB', 'MB', 'TB']);

/** 键盘按键名照键帽写（「按 Enter 发送，Shift + Enter 换行」） */
export const ALLOWED_KEYS = new Set<string>([
  'Alt',
  'Cmd',
  'Ctrl',
  'Enter',
  'Esc',
  'Shift',
  'Tab',
]);

/**
 * 只在管理端允许的英文（规范 §2）：「ID」只在管理端排障信息里出现，面向教师和学生的写「学号」「编号」。
 * 管理端指系统管理、基础设施、AI 模型配置与 AI 对话管理（只有管理员能进）。
 */
export const ADMIN_ONLY_ENGLISH = new Set<string>([
  // 对象存储配置要与云服务控制台对得上：首次出现写「存储桶（Bucket）」这样的中文加英文
  'AccessKey',
  // Redis 的持久化方式名，云服务控制台同样写英文
  'AOF',
  'Bucket',
  'Endpoint',
  'ID',
  'RDB',
  'Region',
  'SecretKey',
]);
export const ADMIN_AREA =
  /^apps\/web-antd\/src\/views\/(?:system|infra|ai\/model|ai\/chat\/manager)\//;

/**
 * 文件扩展名是文件名的一部分（「题库导入模板.xlsx」「支持 .jpg、.png 格式」），不算英文词。
 * 只认带点的写法；不带点的格式名（JPG、ZIP）仍按英文检查，界面上写「图片」「压缩包」或 §2 里的名字。
 */
export const FILE_EXTENSIONS = [
  '7z',
  'bmp',
  'csv',
  'doc',
  'docx',
  'gif',
  'htm',
  'html',
  'jpeg',
  'jpg',
  'json',
  'md',
  'mov',
  'mp3',
  'mp4',
  'pdf',
  'png',
  'ppt',
  'pptx',
  'rar',
  'svg',
  'txt',
  'wav',
  'webp',
  'xls',
  'xlsx',
  'zip',
];

// ---------------------------------------------------------------- 哪些文字算「显示」

/** 显示用的静态属性：值不含中文时也检查英文（含中文的属性一律检查） */
export const DISPLAY_ATTRS = new Set<string>([
  'addon-after',
  'addon-before',
  'addonAfter',
  'addonBefore',
  'alt',
  'aria-label',
  'cancel-text',
  'cancelText',
  'checked-children',
  'checkedChildren',
  'content',
  'description',
  'empty-description',
  'empty-text',
  'emptyText',
  'extra',
  'header',
  'help',
  'hint',
  'label',
  'loading-text',
  'message',
  'not-found-content',
  'notFoundContent',
  'ok-text',
  'okText',
  'placeholder',
  'sub-title',
  'subtitle',
  'subTitle',
  'suffix',
  'text',
  'tip',
  'title',
  'tooltip',
  'un-checked-children',
  'unCheckedChildren',
]);

/** 对象里用于显示的字段名（固定清单 + DISPLAY_KEY_PATTERN）：表格列的 title、表单的 label 与 placeholder 等 */
export const DISPLAY_KEYS = new Set<string>([
  'cancelText',
  'caption',
  'content',
  'desc',
  'description',
  'detail',
  'emptyText',
  'extra',
  'help',
  'hint',
  'label',
  'message',
  'note',
  'okText',
  'placeholder',
  'reason',
  'subtitle',
  'subTitle',
  'suffix',
  'summary',
  'text',
  'tip',
  'title',
  'tooltip',
]);

/** 名字以这些结尾的字段也按显示字段处理（actorLabel、emptyTitle、confirmText 等） */
export const DISPLAY_KEY_PATTERN =
  /(?:label|Label|title|Title|text|Text|hint|Hint|message|Message|desc|Desc|description|Description|summary|Summary|reason|Reason|detail|Detail|note|Note|placeholder|Placeholder|caption|Caption|tip|Tip)$/;

/** 名字里带这些词的字段不是显示文字（echarts 的 extraCssText、textStyle 等样式字段） */
export const NON_DISPLAY_KEY_PATTERN = /css|style|class/i;

/** 名字以这些结尾的常量是颜色、图标、类型等映射表，键碰巧叫 text、label 也不是显示文字 */
export const NON_DISPLAY_CONST =
  /(?:COLOR|ICON|CLASS|STYLE|TYPE|KIND|KEY|CODE|FIELD|PATH|ROUTE|URL)S?(?:_MAP)?$|(?:Color|Icon|Class|Style|Type|Kind|Key|Code|Field|Path|Route|Url)s?(?:Map)?$/;

/**
 * 这些字段的值是字段映射，不是显示文字：fieldNames: { label: 'name', value: 'id' } 里的 'name' 是字段名。
 * 显示字段的对象挂在这些字段下时不检查英文。
 */
export const MAPPING_KEYS = new Set<string>([
  'field-names',
  'fieldNames',
  'replaceFields',
]);

/** 提示、确认框等调用：message.success('…')、Modal.confirm(…)、notification.warning(…) */
export const MESSAGE_CALLEE =
  /(?:^|\.)(?:alert|confirm|error|info|loading|notify|prompt|success|toast|warn|warning)$/;

/**
 * 名字以这些结尾的函数，返回值就是显示文字。不含 ScholarOS 的 Name：这里 skinName、langName、getUploadingImgName
 * 这类函数返回的是配置值与文件名。
 */
export const DISPLAY_FUNCTION_NAME =
  /(?:Label|Labels|Text|Title|Hint|Description|Desc|Summary|Caption|Message|Sentence|Line|Note|Reason|Tip|Placeholder|Word|Phrase)$/;

/**
 * dayjs 的格式串（'YYYY年M月D日'、'MM-DD HH:mm'）是格式化的输入，不是显示文字：
 * 拉丁字母全是日期占位的字母（Y、M、D、H、m、s、A、Do 等），且至少有一个明确的日期占位。
 */
export const DATE_FORMAT_LETTERS = /^[ADHMQSWYadhmosw]*$/;
export const DATE_FORMAT_TOKEN = /YYYY|MM|DD|HH|mm|ss|M月|D日|H时|m分/;

// ---------------------------------------------------------------- 语气（规范 §3.1）

/** 称呼统一用「你」（§3.1 第 2 条） */
export const HONORIFIC = '您';
export const HONORIFIC_HINT =
  '称呼统一用「你」，多数标签可以省略称呼（规范 §3.1 第 2 条）';

/** 不用感叹号（§3.1 第 3 条） */
export const EXCLAMATION = /[!！]/;
export const EXCLAMATION_HINT = '不用感叹号（规范 §3.1 第 3 条）';

interface ToneWord {
  word: RegExp | string;
  hint: string;
}

const PROMO_HINT = '宣传腔（规范 §3.1 第 4 条）：换成具体说法';
const JARGON_HINT = '工程与管理行话（规范 §3.1 第 5 条）：写具体动作或对象';
const CHEER_HINT = '不写「恭喜」「太棒了」「加油」（规范 §3.1 第 3 条）';

/**
 * 宣传腔与行话（§3.1 第 3～5 条）。
 * 「解锁」只查宣传腔的用法（解锁更多功能、解锁新技能）：等级闯关里的「已解锁」「等待解锁」「达标后自动解锁」
 * 是功能状态，不算。
 */
export const TONE_WORDS: ToneWord[] = [
  ...[
    '赋能',
    '助力',
    '一站式',
    '全方位',
    '无缝',
    '轻松',
    '一键',
    '智能化',
    '深度洞察',
    '精准',
    '极致',
    '打造',
    '旅程',
    '保驾护航',
  ].map((word) => ({ hint: PROMO_HINT, word })),
  {
    hint: PROMO_HINT,
    word: /解锁(?:更多|全新|全部|新的?|你的|专属|无限|潜能|潜力|成就)/,
  },
  { hint: PROMO_HINT, word: /开启.{0,12}之旅/ },
  ...['闭环', '贯通', '收口', '落地', '抓手', '底座', '口径'].map((word) => ({
    hint: JARGON_HINT,
    word,
  })),
  {
    hint: '工程与管理行话（规范 §3.1 第 5 条）：写「接入」或具体动作',
    word: '对接',
  },
  ...['恭喜', '太棒了', '加油'].map((word) => ({ hint: CHEER_HINT, word })),
];

// ---------------------------------------------------------------- 内部引用（规范 §3.2）

const PLACEHOLDER_HINT =
  '开发占位（规范 §3.2 第 7 条）：没上线的功能写「暂未开放」和现在能做什么';

/**
 * 开发占位与内部引用（§3.2 第 7 条及同类写法）。字段名、枚举原值等内部标识由 internal-id 规则查。
 * 「开发中」只认「功能开发中」「正在开发」这类说法，不碰「在软件开发中」。
 */
export const INTERNAL_REF_PATTERNS: { hint: string; re: RegExp }[] = [
  { hint: PLACEHOLDER_HINT, re: /待接入|待开发|待实现|待联调/ },
  {
    hint: PLACEHOLDER_HINT,
    re: /(?:功能|页面|模块)(?:正在)?开发中|正在开发|敬请期待/,
  },
  { hint: PLACEHOLDER_HINT, re: /\b(?:TODO|FIXME|TBD|mock|Mock|MOCK)\b/ },
  {
    hint: PLACEHOLDER_HINT,
    re: /测试数据|假数据|演示数据|占位(?:数据|文字|内容)/,
  },
  {
    hint: '内部引用（规范 §3.2 第 6 条）：不写文档章节与版本计划编号',
    re: /\bPRD\b|§\s*\d+|(?<![\w.])V\d+(?:\.\d+){2,}/,
  },
];

/** 内部标识的写法（§3.2 第 6 条：字段名、接口名、枚举原值、配置项名）；英文词符合这些写法时记为 internal-id */
export const INTERNAL_ID: RegExp[] = [
  /^[a-z][a-z0-9]*(?:_[a-z0-9]+)+$/, // snake_case
  /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/, // UPPER_SNAKE
  /^[a-z]+(?:[A-Z][a-z0-9]*)+$/, // camelCase
  /^(?:none|null|undefined|true|false|NaN)$/, // 原样显示的空值与布尔值
];

// ---------------------------------------------------------------- 术语（规范 §1）

interface Variant {
  /** 规范 §1 里的概念 */
  concept: string;
  /** 标准写法 */
  use: string;
  avoid: (RegExp | string)[];
  /** 只在这些文件里查：按上下文才算非标准写法的词 */
  only?: RegExp;
}

/** 按上下文查的词（只在 only 指定的文件里查）一律不查这些文件：AI 对话的「会话」「消息」、站内信管理的「消息」是正当用法 */
export const CONTEXT_EXEMPT =
  /^apps\/web-antd\/src\/views\/(?:ai|system\/notify)\//;

/** 留言模块：教师「留言互动」、学生「我的留言」与留言状态 */
const MESSAGE_MODULE =
  /^apps\/web-antd\/src\/(?:views\/edu\/(?:student\/)?message\/|utils\/status-message\.ts$)/;

/**
 * 非标准术语（§1「不用」一列）。从严只查不会误报的词：「消息」「会话」只在留言模块里查，
 * 「创建」「还原」有正当用法（AI 对话的会话、数据还原），不查。
 */
export const VARIANTS: Variant[] = [
  {
    avoid: ['实训作业', /(?<!实训)作业/],
    concept: '教师布置给学生完成的训练项目',
    use: '「实训任务」（正式称呼）或「任务」',
  },
  { avoid: ['作答记录'], concept: '学生交上来的答案', use: '「提交记录」' },
  {
    avoid: ['评阅', '批阅', '阅卷'],
    concept: '教师给出成绩与评语',
    use: '「批改」',
  },
  {
    avoid: ['退回', '驳回', '返工'],
    concept: '要求学生重新完成',
    use: '「打回」（动作）、「已打回」（状态）或「重做」（学生要做的事）',
  },
  {
    avoid: ['站内信'],
    concept: '师生问答往来或系统提醒',
    use: '「留言」（师生问答）或「通知」（系统提醒）',
  },
  {
    avoid: ['教师手动批改', '人工批阅'],
    concept: '只由教师批改、不用 AI 初评的批改方式',
    use: '「仅人工批改」',
  },
  { avoid: ['新增'], concept: '新建一个对象', use: '「新建」' },
  {
    avoid: ['归档'],
    concept: '往期不再使用、只读保留，统计照常计入',
    use: '「封存」（真正移除用「删除」）',
  },
  { avoid: ['技能'], concept: '学生在指标上的表现', use: '「能力」' },
  {
    avoid: ['消息', '会话'],
    concept: '学生与教师之间的问答往来',
    only: MESSAGE_MODULE,
    use: '「留言」（结束一串留言写「关闭留言」）',
  },
];

// ---------------------------------------------------------------- 放行

interface Exempt {
  file: RegExp | string;
  rule?: string;
  hit?: string;
  text?: string;
  why: string;
}

/** 逐条放行（不算违反规范的用法），每条写明理由；要改的存量问题记在基线里，不写在这里 */
export const EXEMPT: Exempt[] = [
  ...['Root', 'Authentication'].map((hit) => ({
    file: 'apps/web-antd/src/router/routes/core.ts',
    hit,
    rule: 'english',
    why: '布局路由的标题（hideInBreadcrumb、hideInTab），不在面包屑、标签页与页面标题里出现',
  })),
  {
    file: 'apps/web-antd/src/views/system/menu/data.ts',
    hit: 'user',
    rule: 'english',
    why: '路由地址的填写样例（「例如 user」），是管理员要照着填的值',
  },
  {
    file: /^apps\/web-antd\/src\/(?:views\/capacity\/course\/|utils\/status-course\.ts$)/,
    hit: '驳回',
    rule: 'variant',
    why: '课程审批的结果（管理员审批教师申请开设的课程），不是要求学生重做的「打回」',
  },
];

// ---------------------------------------------------------------- 页面归属

/** 文件 → 页面或模块（清单按页统计与抽检用），按顺序取第一个匹配 */
const PAGE_OF: [RegExp, string][] = [
  [/\/views\/_core\/authentication\//, '登录'],
  [/\/views\/_core\/profile\//, '个人中心'],
  [/\/views\/_core\/fallback\//, '错误页'],
  [/\/views\/dashboard\//, '工作台'],
  [/\/views\/edu\/statistics\/admin-dashboard/, '工作台（管理端）'],
  [
    /\/views\/edu\/statistics\/(?:teacher-dashboard|components\/teacher-)/,
    '工作台（教师）',
  ],
  [/\/views\/edu\/statistics\/components\/admin-/, '工作台（管理端）'],
  [/\/views\/edu\/statistics\/skill-assessment/, '学情 · 能力评估'],
  [/\/views\/edu\/statistics\//, '学情'],
  [/\/views\/edu\/homework\//, '实训任务'],
  [/\/views\/edu\/message\//, '留言互动'],
  [/\/views\/edu\/student\/homework\//, '学生 · 任务中心'],
  [/\/views\/edu\/student\/course\//, '学生 · 我的课程'],
  [/\/views\/edu\/student\/capacity\//, '学生 · 能力'],
  [/\/views\/edu\/student\/message\//, '学生 · 我的留言'],
  [/\/views\/edu\/student\/news\//, '学生 · 校园资讯'],
  [/\/views\/edu\/student\/home\//, '学生 · 学习首页'],
  [/\/views\/edu\/student\/profile\//, '学生 · 个人中心'],
  [
    /\/views\/edu\/(?:student\/index|teacher\/index|common\/)/,
    '教务 · 教师与学生账号',
  ],
  [/\/views\/edu\/(?:class|dept|semester)\//, '教务 · 组织、班级与学期'],
  [/\/views\/edu\/certificate\//, '教务 · 证书'],
  [
    /\/views\/edu\/(?:courseresource|questionbank)\/|\/components\/course-resource\//,
    '教学 · 课程资源与题库',
  ],
  [/\/views\/capacity\/course\//, '实训课程'],
  [/\/views\/capacity\/indicator\//, '评价指标'],
  [/\/views\/ai\/chat\/manager\//, 'AI 对话管理'],
  [/\/views\/ai\/chat\//, 'AI 对话'],
  [/\/views\/ai\/model\//, 'AI 模型配置'],
  [/\/views\/system\//, '系统管理'],
  [/\/views\/infra\//, '基础设施'],
  [/\/components\//, '共用组件'],
  [/\/layouts\//, '布局与顶栏'],
  [/\/utils\//, '共用（状态与文案函数）'],
  [/\/router\//, '路由与导航'],
  [/\/adapter\//, '表格与表单适配'],
  [/\/store\//, '登录状态'],
  [/\/locales\//, '语言包'],
];

export const pageOf = (file: string): string =>
  PAGE_OF.find(([re]) => re.test(file))?.[1] ?? '其他';
