import type { HLJSApi, LanguageFn } from 'highlight.js';

import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import diff from 'highlight.js/lib/languages/diff';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import plaintext from 'highlight.js/lib/languages/plaintext';
import python from 'highlight.js/lib/languages/python';
import scss from 'highlight.js/lib/languages/scss';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

const languageEntries: Array<[string, LanguageFn]> = [
  ['bash', bash],
  ['css', css],
  ['diff', diff],
  ['java', java],
  ['javascript', javascript],
  ['json', json],
  ['plaintext', plaintext],
  ['python', python],
  ['scss', scss],
  ['shell', bash],
  ['sh', bash],
  ['sql', sql],
  ['typescript', typescript],
  ['xml', xml],
  ['yaml', yaml],
];

let initialized = false;

function getHighlightJs(): HLJSApi {
  if (initialized) {
    return hljs;
  }

  for (const [languageName, language] of languageEntries) {
    if (!hljs.getLanguage(languageName)) {
      hljs.registerLanguage(languageName, language);
    }
  }

  initialized = true;
  return hljs;
}

function highlightCode(code: string, lang?: string) {
  const highlighter = getHighlightJs();
  const normalizedLang = lang?.trim().toLowerCase();

  if (normalizedLang && highlighter.getLanguage(normalizedLang)) {
    return highlighter.highlight(code, {
      ignoreIllegals: true,
      language: normalizedLang,
    }).value;
  }

  return highlighter.highlightAuto(code).value;
}

function encodeCopyPayload(value: string) {
  return encodeURIComponent(value);
}

function decodeCopyPayload(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export {
  decodeCopyPayload,
  encodeCopyPayload,
  escapeHtml,
  getHighlightJs,
  highlightCode,
};
