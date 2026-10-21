/**
 * 界面文案扫描（V1.0.1.6，docs/ui-copy.md §4）：从页面源码里取出用户看得到的文字，逐条做规则检查。
 * 从 ScholarOS 的界面文案扫描（tests/copy/copyScan.ts）移植。
 *
 * 取哪些文字：
 * - 模板：文本节点（连同相邻的插值拼成一句，插值写成「{…}」）、静态属性值、绑定表达式里的字符串；
 * - 脚本（.vue 的 script 与 .ts）：含中文的字符串与模板字符串、JSX 文字；不含中文的，只取明确用于显示的位置
 *   （label、title 等显示字段，提示与确认框的参数，名字以 Label、Text 等结尾的函数的返回值）；
 * - 中文语言包：每个字符串值，位置写语言包键，{0}、{name} 这类插值按「{…}」看待。
 * 不取：注释、import、类型位置、对象的键、console 输出、比较与 switch 分支里的取值、正则。
 *
 * 规则（见 copyRules.ts）：称呼「您」、感叹号、宣传腔与行话、开发占位与内部引用、内部标识、英文残留、
 * 非标准术语、中文旁的半角标点与空格。
 *
 * 本文件只依赖 Node 与仓库已有的 typescript、vue/compiler-sfc，可被 vitest 引用，也可以用 Node 直接运行 inventory.ts
 * （类型剥离：只写可擦除的 TypeScript，本地模块带 .ts 扩展名）。
 */
// cspell:ignore punct
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

// typescript 是前端仓库根目录的开发依赖（pnpm workspace），扫描只在测试与命令行里用，不进应用
// eslint-disable-next-line n/no-extraneous-import
import ts from 'typescript';
import { parse as parseSfc } from 'vue/compiler-sfc';

import {
  ADMIN_AREA,
  ADMIN_ONLY_ENGLISH,
  ALLOWED_ENGLISH,
  ALLOWED_KEYS,
  ALLOWED_UNITS,
  CONTEXT_EXEMPT,
  DATE_FORMAT_LETTERS,
  DATE_FORMAT_TOKEN,
  DISPLAY_ATTRS,
  DISPLAY_FUNCTION_NAME,
  DISPLAY_KEY_PATTERN,
  DISPLAY_KEYS,
  EXCLAMATION,
  EXCLAMATION_HINT,
  EXEMPT,
  FILE_EXTENSIONS,
  HONORIFIC,
  HONORIFIC_HINT,
  INTERNAL_ID,
  INTERNAL_REF_PATTERNS,
  LOCALE_DIR,
  MAPPING_KEYS,
  MESSAGE_CALLEE,
  NON_DISPLAY_CONST,
  NON_DISPLAY_KEY_PATTERN,
  pageOf,
  SCAN_DIRS,
  SKIP_FILES,
  TONE_WORDS,
  VARIANTS,
} from './copyRules.ts';

export type EntryKind = 'attr' | 'expr' | 'script' | 'text';

export interface CopyEntry {
  /** 相对前端仓库根的路径，分隔符统一为 / */
  file: string;
  line: number;
  kind: EntryKind;
  /** 位置说明：属性名、字段名、调用名、所在函数名、语言包键 */
  where: string;
  /** 规范化后的文字；插值与表达式写成 {…} */
  text: string;
  /** 所属页面或模块（抽检、按页统计用） */
  page: string;
}

export type FindingRule =
  | 'english'
  | 'exclamation'
  | 'honorific'
  | 'internal-id'
  | 'internal-ref'
  | 'punct'
  | 'tone'
  | 'variant';

export interface Finding {
  rule: FindingRule;
  file: string;
  line: number;
  where: string;
  text: string;
  /** 命中的片段 */
  hit: string;
  /** 规则给出的建议（标准写法、规范条目） */
  hint?: string;
}

export interface ScanResult {
  files: string[];
  entries: CopyEntry[];
  findings: Finding[];
}

/** 汉字（含扩展区与兼容汉字） */
const CJK = /\p{Script=Han}/u;
/** 不含中文的模板字符串里「v${版本}」这种版本写法也算显示文字 */
const VERSION_TEXT = /(?:^|[\s(（·])v\{…\}/;
const HAS_LETTER = /[A-Z]/i;
const HAS_ANY_LETTER = /\p{L}/u;
export const PLACEHOLDER = '{…}';

const norm = (s: string) => s.replaceAll(/\s+/g, ' ').trim();

// ---------------------------------------------------------------- 文件

const toPosix = (p: string) => p.split(sep).join('/');

const exists = (path: string) => {
  try {
    statSync(path);
    return true;
  } catch {
    return false;
  }
};

const walk = (root: string, dir: string, ext: RegExp): string[] => {
  const abs = join(root, dir);
  if (!exists(abs)) return [];
  return readdirSync(abs)
    .toSorted()
    .flatMap((name) => {
      const path = join(abs, name);
      const rel = toPosix(relative(root, path));
      if (statSync(path).isDirectory()) return walk(root, rel, ext);
      return ext.test(name) ? [rel] : [];
    });
};

const listFiles = (root: string): string[] => {
  const files = new Set<string>();
  for (const dir of SCAN_DIRS)
    for (const f of walk(root, dir, /\.(?:ts|vue)$/)) files.add(f);
  for (const f of walk(root, LOCALE_DIR, /\.json$/)) files.add(f);
  return [...files]
    .filter((f) => !SKIP_FILES.some((re) => re.test(f)))
    .toSorted();
};

// ---------------------------------------------------------------- 脚本里的字符串

const literalText = (node: ts.Node): null | string => {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node))
    return node.text;
  if (ts.isTemplateExpression(node)) {
    let s = node.head.text;
    for (const span of node.templateSpans) s += PLACEHOLDER + span.literal.text;
    return s;
  }
  return null;
};

const calleeText = (
  call: ts.CallExpression | ts.NewExpression,
  sf: ts.SourceFile,
): string => norm(call.expression.getText(sf));

/** 所在函数的名字（函数声明、赋给变量的箭头函数、对象方法） */
const enclosingFunctionName = (node: ts.Node): null | string => {
  for (let p: ts.Node | undefined = node.parent; p; p = p.parent) {
    if (ts.isFunctionDeclaration(p) || ts.isMethodDeclaration(p))
      return p.name?.getText() ?? null;
    if (ts.isArrowFunction(p) || ts.isFunctionExpression(p)) {
      const holder = p.parent;
      if (holder && ts.isVariableDeclaration(holder))
        return holder.name.getText();
      if (holder && ts.isPropertyAssignment(holder))
        return holder.name.getText();
      // 作为参数传入（如 computed(() => …)）的匿名函数：继续向外找变量名
      if (holder && ts.isCallExpression(holder)) {
        const decl = holder.parent;
        if (decl && ts.isVariableDeclaration(decl)) return decl.name.getText();
      }
      return null;
    }
  }
  return null;
};

/** 所在对象字面量被赋给的常量名（标签映射表） */
const enclosingConstName = (node: ts.Node): null | string => {
  for (let p: ts.Node | undefined = node.parent; p; p = p.parent) {
    if (ts.isVariableDeclaration(p)) return p.name.getText();
    if (ts.isFunctionLike(p)) return null;
  }
  return null;
};

/** 字面量在表达式里是不是「结果」位置（被显示），而不是比较、下标、调用参数之类 */
const isResultPosition = (node: ts.Node): boolean => {
  let cur: ts.Node = node;
  for (let p: ts.Node | undefined = node.parent; p; cur = p, p = p.parent) {
    if (
      ts.isParenthesizedExpression(p) ||
      ts.isAsExpression(p) ||
      ts.isNonNullExpression(p) ||
      ts.isSatisfiesExpression(p)
    )
      continue;
    if (ts.isConditionalExpression(p)) {
      if (p.condition === cur) return false;
      continue;
    }
    if (ts.isBinaryExpression(p)) {
      const op = p.operatorToken.kind;
      if (
        op === ts.SyntaxKind.QuestionQuestionToken ||
        op === ts.SyntaxKind.BarBarToken ||
        op === ts.SyntaxKind.PlusToken
      )
        continue;
      if (op === ts.SyntaxKind.AmpersandAmpersandToken && p.right === cur)
        continue;
      return false;
    }
    if (ts.isTemplateSpan(p) || ts.isTemplateExpression(p)) continue;
    if (ts.isArrayLiteralExpression(p)) continue;
    if (ts.isSpreadElement(p)) continue;
    return (
      ts.isReturnStatement(p) ||
      ts.isArrowFunction(p) ||
      ts.isExpressionStatement(p) ||
      ts.isPropertyAssignment(p) ||
      ts.isVariableDeclaration(p) ||
      ts.isCallExpression(p) ||
      ts.isNewExpression(p) ||
      ts.isBinaryExpression(p)
    );
  }
  return true;
};

const COMPARISON_OPERATORS = new Set<ts.SyntaxKind>([
  ts.SyntaxKind.EqualsEqualsEqualsToken,
  ts.SyntaxKind.EqualsEqualsToken,
  ts.SyntaxKind.ExclamationEqualsEqualsToken,
  ts.SyntaxKind.ExclamationEqualsToken,
  ts.SyntaxKind.InKeyword,
]);

/** dayjs 的格式串（'YYYY年M月D日'）是输入，不是显示文字 */
const isDateFormat = (text: string) =>
  DATE_FORMAT_LETTERS.test(text.replaceAll(/[^A-Z]/gi, '')) &&
  DATE_FORMAT_TOKEN.test(text);

const skipLiteral = (node: ts.Node, sf: ts.SourceFile): boolean => {
  const p = node.parent;
  if (!p) return false;
  const text = literalText(node);
  if (text !== null && isDateFormat(text)) return true;
  if (
    ts.isImportDeclaration(p) ||
    ts.isExportDeclaration(p) ||
    ts.isExternalModuleReference(p) ||
    ts.isLiteralTypeNode(p) ||
    ts.isImportTypeNode(p)
  )
    return true;
  // 对象键、属性名、下标
  if (
    (ts.isPropertyAssignment(p) || ts.isPropertySignature(p)) &&
    p.name === node
  )
    return true;
  if (ts.isElementAccessExpression(p) && p.argumentExpression === node)
    return true;
  if (ts.isCaseClause(p)) return true;
  if (
    ts.isBinaryExpression(p) &&
    COMPARISON_OPERATORS.has(p.operatorToken.kind)
  )
    return true;
  // console 输出、import()、import.meta.glob、defineOptions、正则
  for (let a: ts.Node | undefined = p; a; a = a.parent) {
    if (ts.isCallExpression(a)) {
      const c = calleeText(a, sf);
      if (
        /^console\./.test(c) ||
        c === 'import' ||
        c.includes('import.meta.glob')
      )
        return true;
      if (c === 'defineOptions' || c === 'RegExp') return true;
    }
    if (ts.isNewExpression(a) && calleeText(a, sf) === 'RegExp') return true;
    if (ts.isStatement(a)) break;
  }
  return false;
};

const whereOf = (node: ts.Node, sf: ts.SourceFile): string => {
  for (let p: ts.Node | undefined = node.parent; p; p = p.parent) {
    if (ts.isPropertyAssignment(p)) return `${p.name.getText(sf)}:`;
    if (ts.isCallExpression(p) || ts.isNewExpression(p)) {
      const c = calleeText(p, sf);
      return `${c.length > 40 ? `${c.slice(0, 40)}…` : c}()`;
    }
    if (ts.isReturnStatement(p) || ts.isArrowFunction(p)) {
      const fn = enclosingFunctionName(node);
      return fn ? `${fn}()` : 'return';
    }
    if (ts.isVariableDeclaration(p)) return `${p.name.getText(sf)} =`;
    if (ts.isJsxElement(p) || ts.isJsxFragment(p)) return 'JSX';
    if (ts.isStatement(p)) break;
  }
  const fn = enclosingFunctionName(node);
  return fn ? `${fn}()` : '';
};

/** 显示字段所在的对象是不是字段映射（fieldNames: { label: 'name' }） */
const inMappingObject = (prop: ts.PropertyAssignment, sf: ts.SourceFile) => {
  const holder = prop.parent.parent;
  return (
    !!holder &&
    ts.isPropertyAssignment(holder) &&
    MAPPING_KEYS.has(holder.name.getText(sf).replaceAll(/['"]/g, ''))
  );
};

const isDisplayAscii = (
  node: ts.Node,
  sf: ts.SourceFile,
  where: string,
): boolean => {
  if (!isResultPosition(node)) return false;
  for (let p: ts.Node | undefined = node.parent; p; p = p.parent) {
    // 显示字段：{ label: 'xxx' }
    if (ts.isPropertyAssignment(p)) {
      const key = p.name.getText(sf).replaceAll(/['"]/g, '');
      const constName = enclosingConstName(p);
      if (
        NON_DISPLAY_KEY_PATTERN.test(key) ||
        (constName && NON_DISPLAY_CONST.test(constName))
      )
        return false;
      if (DISPLAY_KEYS.has(key) || DISPLAY_KEY_PATTERN.test(key))
        return !inMappingObject(p, sf);
      // 标签映射表的值：const XXX_LABEL = { KEY: 'xxx' }
      return (
        !!constName &&
        /(?:LABEL|TEXT|TITLE|NAME)S?(?:_MAP)?$|Labels?$|Texts?$/.test(constName)
      );
    }
    if (ts.isCallExpression(p) || ts.isNewExpression(p))
      return MESSAGE_CALLEE.test(calleeText(p, sf));
    if (ts.isReturnStatement(p) || ts.isArrowFunction(p)) {
      const fn = enclosingFunctionName(node);
      return !!fn && DISPLAY_FUNCTION_NAME.test(fn);
    }
    if (ts.isStatement(p)) break;
  }
  return where.endsWith('()') && DISPLAY_FUNCTION_NAME.test(where.slice(0, -2));
};

interface ScriptHit {
  line: number;
  text: string;
  where: string;
}

const scanScript = (
  code: string,
  lineOffset: number,
  kind: ts.ScriptKind,
): ScriptHit[] => {
  const sf = ts.createSourceFile(
    kind === ts.ScriptKind.TSX ? 'x.tsx' : 'x.ts',
    code,
    ts.ScriptTarget.Latest,
    true,
    kind,
  );
  const out: ScriptHit[] = [];
  const lineOf = (node: ts.Node) =>
    sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1 + lineOffset;
  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node)) {
      const text = norm(node.text);
      if (HAS_ANY_LETTER.test(text))
        out.push({ line: lineOf(node), text, where: 'JSX' });
      return;
    }
    const text = literalText(node);
    if (text !== null) {
      if (!skipLiteral(node, sf)) {
        const where = whereOf(node, sf);
        if (
          CJK.test(text) ||
          VERSION_TEXT.test(text) ||
          (HAS_LETTER.test(text) && isDisplayAscii(node, sf, where))
        )
          out.push({ line: lineOf(node), text: norm(text), where });
      }
      // 模板字符串里嵌的表达式可能还有字符串，继续往下
      if (!ts.isTemplateExpression(node)) return;
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return out;
};

// ---------------------------------------------------------------- 模板

/** 模板节点类型（@vue/compiler-core 的 NodeTypes） */
const NODE_ELEMENT = 1;
const NODE_TEXT = 2;
const NODE_INTERPOLATION = 5;
const NODE_ATTRIBUTE = 6;
const NODE_DIRECTIVE = 7;
const NODE_IF = 9;
const NODE_FOR = 11;

interface TplNode {
  type: number;
  loc: { source?: string; start: { line: number } };
  content?: any;
  children?: TplNode[];
  props?: any[];
  tag?: string;
}

/** 模板表达式里的字面量是否会被显示：调用的参数（如 formatDate(x, 'YYYY-MM-DD')）是输入，不算 */
const isTemplateResult = (node: ts.Node): boolean => {
  let cur: ts.Node = node;
  for (let p = node.parent; p; cur = p, p = p.parent) {
    if (
      ts.isParenthesizedExpression(p) ||
      (ts.isConditionalExpression(p) && p.condition !== cur) ||
      (ts.isBinaryExpression(p) &&
        (p.operatorToken.kind === ts.SyntaxKind.QuestionQuestionToken ||
          p.operatorToken.kind === ts.SyntaxKind.BarBarToken))
    )
      continue;
    if (
      (ts.isCallExpression(p) || ts.isNewExpression(p)) &&
      p.arguments?.includes(cur as ts.Expression)
    )
      return false;
    break;
  }
  return isResultPosition(node);
};

/** 表达式里的字符串（绑定属性、插值、事件处理） */
const exprLiterals = (expr: string): { result: boolean; text: string }[] => {
  const sf = ts.createSourceFile(
    'e.ts',
    `(${expr})`,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  );
  const out: { result: boolean; text: string }[] = [];
  const visit = (node: ts.Node) => {
    const text = literalText(node);
    if (text !== null && !skipLiteral(node, sf)) {
      out.push({ result: isTemplateResult(node), text: norm(text) });
      if (!ts.isTemplateExpression(node)) return;
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return out;
};

interface TplHit {
  line: number;
  kind: EntryKind;
  where: string;
  text: string;
}

/** 指令的位置说明：:title、@click、v-if */
const directiveWhere = (name: string, arg: string): string => {
  if (name === 'bind') return `:${arg}`;
  if (name === 'on') return `@${arg}`;
  return `v-${name}`;
};

/** 文本节点的行号取第一个看得见的字（节点从上一个标签的结尾算起，开头常是换行与缩进） */
const firstLine = (n: TplNode): number => {
  const lead = /^\s*/.exec(n.loc.source ?? '')?.[0] ?? '';
  return n.loc.start.line + lead.split('\n').length - 1;
};

const scanTemplate = (ast: TplNode): TplHit[] => {
  const out: TplHit[] = [];
  const visitElement = (el: TplNode) => {
    for (const p of el.props ?? []) {
      if (p.type === NODE_ATTRIBUTE) {
        const value: string = p.value?.content ?? '';
        if (!value || isDateFormat(value)) continue;
        const name: string = p.name;
        if (
          CJK.test(value) ||
          (DISPLAY_ATTRS.has(name) && HAS_LETTER.test(value))
        )
          out.push({
            kind: 'attr',
            line: p.loc.start.line,
            text: norm(value),
            where: name,
          });
      } else if (p.type === NODE_DIRECTIVE) {
        const expr: string = p.exp?.content ?? '';
        if (!expr || p.name === 'for' || p.name === 'slot') continue;
        const arg: string = p.arg?.content ?? '';
        const where = directiveWhere(p.name, arg);
        const displayAttr = p.name === 'bind' && DISPLAY_ATTRS.has(arg);
        for (const l of exprLiterals(expr)) {
          if (
            CJK.test(l.text) ||
            (displayAttr && l.result && HAS_LETTER.test(l.text))
          )
            out.push({
              kind: 'expr',
              line: p.loc.start.line,
              text: l.text,
              where,
            });
        }
      }
    }
    visitChildren(el.children);
  };
  const visitChildren = (children: TplNode[] = []) => {
    // 相邻的文本与插值拼成一句
    let group: TplNode[] = [];
    const flush = () => {
      if (group.length === 0) return;
      const hasText = group.some(
        (n) => n.type === NODE_TEXT && HAS_ANY_LETTER.test(n.content ?? ''),
      );
      const first = group[0];
      if (hasText && first) {
        const text = norm(
          group
            .map((n) => (n.type === NODE_TEXT ? n.content : PLACEHOLDER))
            .join(''),
        );
        out.push({ kind: 'text', line: firstLine(first), text, where: '' });
      }
      group = [];
    };
    for (const c of children) {
      if (c.type === NODE_TEXT || c.type === NODE_INTERPOLATION) {
        group.push(c);
        if (c.type === NODE_INTERPOLATION) {
          const expr: string = c.content?.content ?? '';
          for (const l of exprLiterals(expr)) {
            if (CJK.test(l.text) || (l.result && HAS_LETTER.test(l.text)))
              out.push({
                kind: 'expr',
                line: c.loc.start.line,
                text: l.text,
                where: '{{ }}',
              });
          }
        }
        continue;
      }
      flush();
      if (c.type === NODE_ELEMENT) visitElement(c);
      else if (c.type === NODE_IF || c.type === NODE_FOR)
        visitChildren(c.children);
    }
    flush();
  };
  visitChildren(ast.children);
  return out;
};

// ---------------------------------------------------------------- 语言包

/** 语言包里的 {0}、{name} 是插值 */
const I18N_ARG = /\{[^{}]*\}/g;

const scanLocale = (src: string, namespace: string): ScriptHit[] => {
  const sf = ts.parseJsonText('locale.json', src);
  const out: ScriptHit[] = [];
  const visit = (node: ts.Node, path: string[]) => {
    if (ts.isObjectLiteralExpression(node)) {
      for (const prop of node.properties) {
        if (!ts.isPropertyAssignment(prop)) continue;
        const key = ts.isStringLiteral(prop.name)
          ? prop.name.text
          : prop.name.getText(sf);
        visit(prop.initializer, [...path, key]);
      }
      return;
    }
    if (ts.isArrayLiteralExpression(node)) {
      node.elements.forEach((el, i) => visit(el, [...path, String(i)]));
      return;
    }
    if (ts.isStringLiteral(node) && HAS_ANY_LETTER.test(node.text)) {
      out.push({
        line: sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1,
        text: norm(node.text.replaceAll(I18N_ARG, PLACEHOLDER)),
        where: [namespace, ...path].join('.'),
      });
    }
  };
  const root = sf.statements[0];
  if (root) visit(root.expression, []);
  return out;
};

// ---------------------------------------------------------------- 汇总

const scriptKindOf = (lang: string | undefined) =>
  lang === 'tsx' || lang === 'jsx' ? ts.ScriptKind.TSX : ts.ScriptKind.TS;

/** 从一个文件的源码里取文案（.vue、.ts 与语言包 .json）；file 是相对前端仓库根的路径 */
export const scanSource = (file: string, src: string): CopyEntry[] => {
  const page = pageOf(file);
  const entry = (
    h: { line: number; text: string; where: string },
    kind: EntryKind,
  ) => ({
    file,
    kind,
    line: h.line,
    page,
    text: h.text,
    where: h.where,
  });
  if (file.endsWith('.json')) {
    const namespace = file.slice(file.lastIndexOf('/') + 1, -'.json'.length);
    return scanLocale(src, namespace).map((h) => entry(h, 'script'));
  }
  if (!file.endsWith('.vue'))
    return scanScript(src, 0, ts.ScriptKind.TS).map((h) => entry(h, 'script'));
  const entries: CopyEntry[] = [];
  const { descriptor } = parseSfc(src, { filename: file });
  const ast = descriptor.template?.ast as TplNode | undefined;
  if (ast) for (const h of scanTemplate(ast)) entries.push(entry(h, h.kind));
  for (const block of [descriptor.script, descriptor.scriptSetup]) {
    if (!block) continue;
    const offset = block.loc.start.line - 1;
    for (const h of scanScript(block.content, offset, scriptKindOf(block.lang)))
      entries.push(entry(h, 'script'));
  }
  return entries;
};

/**
 * 地址与路径是数据（规范 §3.7 第 22 条：网址里的冒号除外）：查英文与标点之前先去掉。
 * - 网址：http:// 或 https:// 开头，到空白或中文标点为止（只写协议头的「http://」也算）；
 * - 绝对路径：斜杠前不是字母、数字或点，斜杠后紧跟字母或数字，所以「入选 / 排除」「1/2」不受影响；
 * - 相对路径：小写开头、至少两个斜杠（小程序页面 pages/auth/scan/index），「PDF/Word」不算路径。
 */
const URL_OR_PATH =
  /https?:\/\/[^\s，；。、）」]*|(?<![\w.])\/[\w.-]+(?:\/[\w.-]+)*|(?<![\w./])[a-z][\w.-]*(?:\/[a-z0-9][\w.-]*){2,}/g;

/** 字符串里的 HTML 标签（v-html、innerHTML 拼的片段）不是显示文字，标签里的文字照常检查 */
const HTML_TAG = /<\/?[a-z][^<>]*>/gi;

const FILE_EXTENSION = new RegExp(
  String.raw`\.(?:${FILE_EXTENSIONS.join('|')})\b`,
  'gi',
);

/** 英文词：去掉插值占位、地址与路径、文件扩展名、允许的术语与单位后剩下的拉丁字母词 */
const englishTokens = (text: string, file: string): string[] => {
  const s = text
    .split(PLACEHOLDER)
    .join(' ')
    .replaceAll(HTML_TAG, ' ')
    .replaceAll(URL_OR_PATH, ' ')
    .replaceAll(FILE_EXTENSION, ' ');
  const tokens = s.match(/[A-Z][A-Z0-9]*(?:[-_.@'][A-Z0-9]+)*/gi) ?? [];
  return tokens.filter(
    (t) =>
      !ALLOWED_ENGLISH.has(t) &&
      !ALLOWED_UNITS.has(t) &&
      !ALLOWED_KEYS.has(t) &&
      !(ADMIN_ONLY_ENGLISH.has(t) && ADMIN_AREA.test(file)),
  );
};

/**
 * 标点与空格（规范 §3.7）：中文与英文、数字之间没空格，数字与单位之间没空格，中文旁的半角标点与括号，
 * 三个点，中文外面的直引号，弯引号（引用写「」）
 */
const PUNCT_PATTERNS: RegExp[] = [
  /\p{Script=Han}[\dA-Z]+|[\dA-Z]+\p{Script=Han}/iu,
  /\d+(?:\.\d+)?(?:KB|MB|GB|TB)\b/u,
  /\p{Script=Han} *[,:;?](?!\/\/)|[,;?] *\p{Script=Han}/u,
  /\p{Script=Han}[()]|[()]\p{Script=Han}/u,
  /\.{3}/u,
  /"[^"\p{Script=Han}]*\p{Script=Han}[^"]*"/u,
  /'[^'\p{Script=Han}]*\p{Script=Han}[^']*'/u,
  /[‘’“”]/u,
];

/**
 * 中文标点旁、两个汉字之间的空格（规范 §3.7 第 24 条；模板里中文句子跨行时，换行会显示成空格）；
 * 插值按占位符号看待，不算空格
 */
const CJK_SPACE =
  /[、。！，：；？）」』] (?=[\p{Script=Han}「（])|(?<=\p{Script=Han}) (?=[、。！，：；？\p{Script=Han}])/u;

const matchOf = (text: string, word: RegExp | string): null | string => {
  if (typeof word === 'string') return text.includes(word) ? word : null;
  return text.match(word)?.[0] ?? null;
};

export const checkEntry = (e: CopyEntry): Finding[] => {
  const out: Finding[] = [];
  const base = { file: e.file, line: e.line, text: e.text, where: e.where };
  const plain = e.text.split(PLACEHOLDER).join(' ').replaceAll(HTML_TAG, ' ');
  if (plain.includes(HONORIFIC))
    out.push({
      ...base,
      hint: HONORIFIC_HINT,
      hit: HONORIFIC,
      rule: 'honorific',
    });
  const bang = plain.match(EXCLAMATION)?.[0];
  if (bang)
    out.push({
      ...base,
      hint: EXCLAMATION_HINT,
      hit: bang,
      rule: 'exclamation',
    });
  for (const { hint, word } of TONE_WORDS) {
    const hit = matchOf(plain, word);
    if (hit) out.push({ ...base, hint, hit, rule: 'tone' });
  }
  const refs = new Set<string>();
  for (const { hint, re } of INTERNAL_REF_PATTERNS) {
    const hit = matchOf(plain, re);
    if (!hit) continue;
    refs.add(hit);
    out.push({ ...base, hint, hit, rule: 'internal-ref' });
  }
  for (const t of englishTokens(e.text, e.file)) {
    if (refs.has(t)) continue;
    const internal = INTERNAL_ID.some((re) => re.test(t));
    out.push({
      ...base,
      hint: internal
        ? '内部标识（规范 §3.2 第 6 条）：写成用户能懂的中文'
        : '英文残留（规范 §2）：写中文，产品与技术名才保留英文',
      hit: t,
      rule: internal ? 'internal-id' : 'english',
    });
  }
  for (const v of VARIANTS) {
    if (v.only && (!v.only.test(e.file) || CONTEXT_EXEMPT.test(e.file)))
      continue;
    for (const bad of v.avoid) {
      const hit = matchOf(plain, bad);
      if (hit)
        out.push({
          ...base,
          hint: `统一写${v.use}（规范 §1：${v.concept}）`,
          hit,
          rule: 'variant',
        });
    }
  }
  const noUrl = plain.replaceAll(URL_OR_PATH, ' ');
  for (const re of PUNCT_PATTERNS) {
    const hit = noUrl.match(re)?.[0];
    if (hit)
      out.push({
        ...base,
        hint: '中文句子用全角标点，中文与英文、数字之间空一格，省略号写「…」，引用写「」（规范 §3.7）',
        hit,
        rule: 'punct',
      });
  }
  const solid = e.text
    .split(PLACEHOLDER)
    .join('□')
    .replaceAll(URL_OR_PATH, '□');
  const space = solid.match(CJK_SPACE);
  if (space) {
    const at = space.index ?? 0;
    out.push({
      ...base,
      hint: '中文标点旁、汉字之间不加空格（规范 §3.7 第 24 条）',
      hit: `多余空格「${solid.slice(Math.max(0, at - 2), at + 3)}」`,
      rule: 'punct',
    });
  }
  return out;
};

const exempted = (f: Finding): boolean =>
  EXEMPT.some(
    (x) =>
      (typeof x.file === 'string' ? x.file === f.file : x.file.test(f.file)) &&
      (x.rule === undefined || x.rule === f.rule) &&
      (x.hit === undefined || x.hit === f.hit) &&
      (x.text === undefined || f.text.includes(x.text)),
  );

/** 一条命中的指纹（过渡基线用）：不含行号，改动别处的行不影响它 */
export const fingerprint = (f: Finding): string =>
  `${f.file}｜${f.rule}｜${f.hit}｜${f.text}`;

/** 一条文案的检查结果（去掉放行的） */
export const findingsOf = (e: CopyEntry): Finding[] =>
  checkEntry(e).filter((f) => !exempted(f));

/** 读文件；列出之后被删掉或改名的文件（多人同时改页面时会遇到）按空文件处理 */
const readSource = (path: string): string => {
  try {
    return readFileSync(path, 'utf8');
  } catch {
    return '';
  }
};

/** 扫描前端仓库（root 为 NUS-front 目录） */
export const scan = (root: string): ScanResult => {
  const files = listFiles(root);
  const entries = files.flatMap((file) =>
    scanSource(file, readSource(join(root, file))),
  );
  return { entries, files, findings: entries.flatMap((e) => findingsOf(e)) };
};
