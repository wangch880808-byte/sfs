import { describe, expect, it } from 'vitest';

import { buildAntdTheme } from './antd';
import { buildNusColorVars, contrastRatio, NUS_PALETTES } from './palette';

const AA = 4.5;
const MODES = ['light', 'dark'] as const;

describe('ant-design-vue 的 token', () => {
  it.each(MODES)(
    '%s：文字类 token 在容器、画布、浮层、悬停与选中底上 ≥ 4.5',
    (mode) => {
      const { token } = buildAntdTheme(mode);
      const texts = [
        'colorText',
        'colorTextSecondary',
        'colorTextTertiary',
        'colorTextDescription',
        'colorTextPlaceholder',
        'colorTextLabel',
        'colorLink',
        'colorLinkHover',
        'colorPrimary',
        'colorPrimaryText',
        'colorPrimaryHover',
        'colorErrorText',
        'colorSuccessText',
        'colorWarningText',
      ] as const;
      const backgrounds = [
        'colorBgContainer',
        'colorBgLayout',
        'colorBgElevated',
        'controlItemBgHover',
        'controlItemBgActive',
        'colorFillAlter',
      ] as const;
      for (const text of texts) {
        for (const bg of backgrounds) {
          expect(
            contrastRatio(token[text], token[bg]),
            `${mode} ${text} ${token[text]} 在 ${bg} ${token[bg]}`,
          ).toBeGreaterThanOrEqual(AA);
        }
      }
    },
  );

  it.each(MODES)('%s：放白字的组件改用实心底，白字 ≥ 4.5', (mode) => {
    const { components } = buildAntdTheme(mode);
    for (const fill of [
      components.DatePicker.colorPrimary,
      components.DatePicker.colorPrimaryHover,
      components.DatePicker.colorPrimaryActive,
      components.Steps.colorPrimary,
      components.Badge.colorError,
    ]) {
      expect(contrastRatio('#ffffff', fill), fill).toBeGreaterThanOrEqual(AA);
    }
  });

  it('深色下做文字的主色与实心底分开，浅色下两者相同', () => {
    const dark = buildAntdTheme('dark');
    const light = buildAntdTheme('light');
    expect(dark.token.colorPrimary).toBe(NUS_PALETTES.dark.tones.primary);
    expect(dark.components.DatePicker.colorPrimary).toBe(
      NUS_PALETTES.dark.fills.primary,
    );
    expect(light.token.colorPrimary).toBe(
      light.components.DatePicker.colorPrimary,
    );
  });

  it('圆角四档、控件高度与对话框规格取界面规范', () => {
    const { components, token } = buildAntdTheme('dark');
    expect([
      token.borderRadiusXS,
      token.borderRadiusSM,
      token.borderRadius,
      token.borderRadiusLG,
      components.Modal.borderRadiusLG,
    ]).toEqual([2, 4, 6, 8, 12]);
    expect([
      token.controlHeightSM,
      token.controlHeight,
      token.controlHeightLG,
    ]).toEqual([28, 32, 40]);
    expect(token.fontSize).toBe(14);
    expect(components.Modal.fontSizeHeading5).toBe(15);
    expect(token.fontFamily).toContain("'PingFang SC'");
    expect(token.fontFamily).toContain("'Microsoft YaHei'");
  });

  it('动效 120 / 180ms；减少动态效果时为 0', () => {
    const normal = buildAntdTheme('light').token;
    expect([
      normal.motionDurationFast,
      normal.motionDurationMid,
      normal.motionDurationSlow,
    ]).toEqual(['0.12s', '0.18s', '0.18s']);
    const reduced = buildAntdTheme('light', { reducedMotion: true }).token;
    expect([
      reduced.motionDurationFast,
      reduced.motionDurationMid,
      reduced.motionDurationSlow,
    ]).toEqual(['0s', '0s', '0s']);
  });

  it('阴影只给浮层：卡片的 tertiary 阴影只是一圈淡边框', () => {
    const { token } = buildAntdTheme('light');
    expect(token.boxShadowTertiary).toBe(
      `0 0 0 1px ${NUS_PALETTES.light.borderSubtle}`,
    );
    expect(token.boxShadowSecondary).toContain('0 8px 24px');
  });
});

describe('提示条的底色与边框', () => {
  it.each(MODES)('%s：取状态色浅底，正文与状态色在上面都 ≥ 4.5', (mode) => {
    const { token } = buildAntdTheme(mode);
    const p = NUS_PALETTES[mode];
    const pairs = [
      ['colorSuccessBg', p.tones.success],
      ['colorWarningBg', p.tones.warning],
      ['colorErrorBg', p.tones.danger],
      ['colorInfoBg', p.tones.primary],
    ] as const;
    for (const [bg, tone] of pairs) {
      const value = (token as unknown as Record<string, string>)[bg] ?? '';
      expect(value, bg).toMatch(/^#[0-9a-f]{6}$/);
      expect(
        contrastRatio(token.colorText, value),
        `${mode} 正文在 ${bg}`,
      ).toBeGreaterThanOrEqual(AA);
      expect(
        contrastRatio(tone, value),
        `${mode} 状态色在 ${bg}`,
      ).toBeGreaterThanOrEqual(AA);
    }
    // 与设计 token 的强调浅底一致（浅色下不再是算法从加深后的种子色派生的灰黄色）
    expect((token as unknown as Record<string, string>).colorWarningBg).toBe(
      buildNusColorVars(mode)['--nus-warning-tint'],
    );
  });
});
