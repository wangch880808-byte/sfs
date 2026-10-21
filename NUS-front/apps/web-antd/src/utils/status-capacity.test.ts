import { describe, expect, it } from 'vitest';

import {
  getCertificateEligibilityMeta,
  getListedHighlight,
  getPendingEvaluationMeta,
  getQualifiedStatusMeta,
  getRateHighlight,
  getSkillHighlightMeta,
} from './status-capacity';

describe('达标状态', () => {
  it('没有判定时为待评定，等教师批改', () => {
    expect(getQualifiedStatusMeta(null)).toEqual({
      label: '待评定',
      tone: 'running',
    });
    expect(getQualifiedStatusMeta(undefined)).toEqual(
      getPendingEvaluationMeta(),
    );
  });

  it('已达标为完成，未达标为失败', () => {
    expect(getQualifiedStatusMeta(true)).toEqual({
      label: '已达标',
      tone: 'done',
    });
    expect(getQualifiedStatusMeta(false)).toEqual({
      label: '未达标',
      tone: 'failed',
    });
  });
});

describe('获证判定', () => {
  it('后端没给出判定时不显示', () => {
    expect(getCertificateEligibilityMeta(null)).toBeNull();
    expect(getCertificateEligibilityMeta(undefined)).toBeNull();
  });

  it('达到阈值为完成，未达到等学生继续努力', () => {
    expect(getCertificateEligibilityMeta(true)).toEqual({
      label: '已达到获证阈值',
      tone: 'done',
    });
    expect(getCertificateEligibilityMeta(false)).toEqual({
      label: '尚未达到获证阈值',
      tone: 'waiting',
    });
  });
});

describe('优势与薄弱', () => {
  it('能力分析页：80% 及以上为优势，60% 以下为薄弱', () => {
    expect(getRateHighlight(80)).toBe('strength');
    expect(getRateHighlight(95.5)).toBe('strength');
    expect(getRateHighlight(79.9)).toBeNull();
    expect(getRateHighlight(60)).toBeNull();
    expect(getRateHighlight(59.9)).toBe('weakness');
    expect(getRateHighlight(0)).toBe('weakness');
    expect(getRateHighlight(null)).toBeNull();
  });

  it('实训报告：按后端名单标注，优势优先', () => {
    expect(getListedHighlight('需求分析', ['需求分析'], [])).toBe('strength');
    expect(getListedHighlight('编码实现', [], ['编码实现'])).toBe('weakness');
    expect(getListedHighlight('测试', ['测试'], ['测试'])).toBe('strength');
    expect(getListedHighlight('部署')).toBeNull();
  });

  it('标签文字与类别', () => {
    expect(getSkillHighlightMeta('strength')).toEqual({
      label: '优势',
      tone: 'done',
    });
    expect(getSkillHighlightMeta('weakness')).toEqual({
      label: '薄弱',
      tone: 'failed',
    });
    expect(getSkillHighlightMeta(null)).toBeNull();
  });
});
