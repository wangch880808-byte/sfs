import type { ThemeMode } from './palette';

import { describe, expect, it } from 'vitest';

import {
  applyNusTheme,
  buildNusColorVars,
  buildVbenColorVars,
  contrastRatio,
  hexToHslChannels,
  INLINE_VBEN_COLOR_VARS,
  mix,
  NUS_PALETTES,
  NUS_THEME_STYLE_ID,
  nusThemeCss,
  PREFERENCE_THEME_COLORS,
  rgbaToHslChannels,
  STATUS_TONES,
  TEXT_TOKENS,
  THEME_TONES,
} from './palette';

/**
 * 界面规范的颜色（V1.0.1.6 验收第 1 条）：浅色与深色下，全部文字 token 在画布、面板、悬停三种底上的对比度
 * 都 ≥ 4.5（WCAG AA），逐项断言；表头（凹陷底）、浮层、选中底与强调浅底另外核对。
 */
const MODES: ThemeMode[] = ['light', 'dark'];
const AA = 4.5;
const BACKGROUNDS = [
  '--nus-bg-canvas',
  '--nus-bg-surface',
  '--nus-bg-hover',
] as const;
const HEX = /^#[0-9a-f]{6}$/;

/** 取变量值；缺了直接报错，断言里不用非空断言 */
const pick = (vars: Record<string, string>, name: string): string => {
  const value = vars[name];
  if (value === undefined) throw new Error(`缺少变量 ${name}`);
  return value;
};

/** 把 vben 的 HSL 通道写法换回 #rrggbb，核对写进页面的值与色板一致 */
const hslChannelsToHex = (value: string): string => {
  const [h, s, l] = value.replaceAll('%', '').split(' ').map(Number) as [
    number,
    number,
    number,
  ];
  const sat = s / 100;
  const light = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const a = sat * Math.min(light, 1 - light);
  const f = (n: number) =>
    light - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return `#${[f(0), f(8), f(4)]
    .map((v) =>
      Math.round(v * 255)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;
};

const pairs = MODES.flatMap((mode) =>
  TEXT_TOKENS.flatMap((text) => BACKGROUNDS.map((bg) => ({ bg, mode, text }))),
);

describe('文字 token 的对比度', () => {
  it('文字 token 覆盖三档文字、做文字用的主色与状态色，以及五类状态色', () => {
    expect([...TEXT_TOKENS]).toEqual([
      '--nus-text',
      '--nus-text-secondary',
      '--nus-text-tertiary',
      '--nus-primary',
      '--nus-success',
      '--nus-warning',
      '--nus-danger',
      '--nus-info',
      '--nus-status-neutral',
      '--nus-status-running',
      '--nus-status-waiting',
      '--nus-status-done',
      '--nus-status-failed',
    ]);
    expect(pairs).toHaveLength(2 * 13 * 3);
  });

  it.each(pairs)('$mode：$text 在 $bg 上 ≥ 4.5', ({ bg, mode, text }) => {
    const vars = buildNusColorVars(mode);
    expect(vars[text], text).toMatch(HEX);
    expect(vars[bg], bg).toMatch(HEX);
    const ratio = contrastRatio(pick(vars, text), pick(vars, bg));
    expect(
      ratio,
      `${mode} ${text} ${vars[text]} 在 ${bg} ${vars[bg]} 上 ${ratio.toFixed(2)}`,
    ).toBeGreaterThanOrEqual(AA);
  });

  it.each(MODES)(
    '%s：文字 token 在表头（凹陷底）与浮层底上同样 ≥ 4.5',
    (mode) => {
      const vars = buildNusColorVars(mode);
      for (const text of TEXT_TOKENS) {
        for (const bg of ['--nus-bg-sunken', '--nus-bg-elevated']) {
          expect(
            contrastRatio(pick(vars, text), pick(vars, bg)),
            `${mode} ${text} 在 ${bg}`,
          ).toBeGreaterThanOrEqual(AA);
        }
      }
    },
  );

  it.each(MODES)(
    '%s：选中底上的主色与正文、强调浅底上的同色文字、文字悬停色 ≥ 4.5',
    (mode) => {
      const vars = buildNusColorVars(mode);
      for (const text of [
        '--nus-primary',
        '--nus-text',
        '--nus-text-secondary',
      ]) {
        expect(
          contrastRatio(pick(vars, text), pick(vars, '--nus-bg-selected')),
          `${text} 在选中底`,
        ).toBeGreaterThanOrEqual(AA);
      }
      for (const tone of THEME_TONES) {
        for (const bg of [`--nus-${tone}-tint`, `--nus-${tone}-tint-hover`]) {
          expect(
            contrastRatio(pick(vars, `--nus-${tone}`), pick(vars, bg)),
            `${tone} 在 ${bg}`,
          ).toBeGreaterThanOrEqual(AA);
        }
        expect(
          contrastRatio(
            pick(vars, `--nus-${tone}-hover`),
            pick(vars, '--nus-bg-surface'),
          ),
          `${tone} 文字悬停`,
        ).toBeGreaterThanOrEqual(AA);
      }
    },
  );

  it.each(MODES)('%s：实心底（含悬停、按下）上的白字 ≥ 4.5', (mode) => {
    const vars = buildNusColorVars(mode);
    for (const tone of THEME_TONES) {
      for (const state of ['', '-hover', '-active']) {
        const fill = pick(vars, `--nus-${tone}-fill${state}`);
        expect(
          contrastRatio(pick(vars, '--nus-on-fill'), fill),
          `${tone}${state} ${fill}`,
        ).toBeGreaterThanOrEqual(AA);
      }
    }
  });

  it('原来的主色与 vben 默认状态色确实不达标（调整的依据）', () => {
    // 原主色 hsl(212 100% 45%) 在悬停底上只有 4.35
    expect(contrastRatio('#0073e6', '#f0f0f2')).toBeLessThan(AA);
    // 原主色做深色下的文字只有 3.9
    expect(contrastRatio('#0073e6', '#16171a')).toBeLessThan(AA);
    // vben 默认的成功、警告、危险色做白底文字分别只有 1.9、1.7、3.3
    expect(contrastRatio('#5cd18a', '#ffffff')).toBeLessThan(3);
    expect(contrastRatio('#ff4d50', '#ffffff')).toBeLessThan(3.5);
    // 深色的提亮主色放不了白字，所以实心底另取
    expect(
      contrastRatio('#ffffff', NUS_PALETTES.dark.tones.primary),
    ).toBeLessThan(AA);
  });
});

describe('颜色 token 的名字与取值', () => {
  it('两套模式的变量名一致，颜色都是合法值', () => {
    const light = buildNusColorVars('light');
    const dark = buildNusColorVars('dark');
    expect(Object.keys(dark)).toEqual(Object.keys(light));
    for (const vars of [light, dark]) {
      for (const [name, value] of Object.entries(vars)) {
        expect(value, name).toMatch(
          /^(#[0-9a-f]{6}|rgba\(\d{1,3}, \d{1,3}, \d{1,3}, [\d.]+\))$/,
        );
      }
    }
    expect(Object.keys(buildVbenColorVars('dark'))).toEqual(
      Object.keys(buildVbenColorVars('light')),
    );
  });

  it.each(MODES)('%s：五类状态色两两不同，强调浅底上同样 ≥ 4.5', (mode) => {
    const vars = buildNusColorVars(mode);
    const statuses = Object.keys(STATUS_TONES);
    const colors = statuses.map((status) => vars[`--nus-status-${status}`]);
    expect(new Set(colors).size, colors.join(' ')).toBe(statuses.length);
    for (const status of statuses) {
      expect(
        contrastRatio(
          pick(vars, `--nus-status-${status}`),
          pick(vars, `--nus-status-${status}-tint`),
        ),
        `${status} 浅底`,
      ).toBeGreaterThanOrEqual(AA);
    }
  });

  it('主色保留原来的蓝：浅色 hsl(212 100% 42%)，深色 hsl(212 100% 60%)', () => {
    expect(hexToHslChannels(NUS_PALETTES.light.tones.primary)).toBe(
      '212 100% 42%',
    );
    expect(hexToHslChannels(NUS_PALETTES.dark.tones.primary)).toBe(
      '212.1 100% 60%',
    );
  });

  it.each(MODES)(
    '%s：vben 变量换回颜色后与色板一致（误差不超过 1/255）',
    (mode) => {
      const p = NUS_PALETTES[mode];
      const vben = buildVbenColorVars(mode);
      const expected: Record<string, string> = {
        '--background': p.surface,
        '--background-deep': p.canvas,
        '--border': p.borderSubtle,
        '--card': p.surface,
        '--destructive': p.tones.danger,
        '--foreground': p.textPrimary,
        '--header': p.canvas,
        '--input': p.border,
        '--muted-foreground': p.textSecondary,
        '--popover': p.elevated,
        '--primary': p.tones.primary,
        '--sidebar': p.canvas,
        '--success': p.tones.success,
        '--warning': p.tones.warning,
      };
      for (const [name, hex] of Object.entries(expected)) {
        const back = hslChannelsToHex(pick(vben, name));
        const diff = Math.max(
          ...[1, 3, 5].map((i) =>
            Math.abs(
              Number.parseInt(back.slice(i, i + 2), 16) -
                Number.parseInt(hex.slice(i, i + 2), 16),
            ),
          ),
        );
        expect(
          diff,
          `${mode} ${name} ${vben[name]} → ${back}，色板 ${hex}`,
        ).toBeLessThanOrEqual(1);
      }
    },
  );

  it('vben 的正文与次要文字同样 ≥ 4.5（Tailwind 的 text-foreground、text-muted-foreground）', () => {
    for (const mode of MODES) {
      const vben = buildVbenColorVars(mode);
      for (const text of ['--foreground', '--muted-foreground', '--primary']) {
        for (const bg of [
          '--background',
          '--background-deep',
          '--accent',
          '--muted',
        ]) {
          expect(
            contrastRatio(
              hslChannelsToHex(pick(vben, text)),
              hslChannelsToHex(pick(vben, bg)),
            ),
            `${mode} ${text} 在 ${bg}`,
          ).toBeGreaterThanOrEqual(AA);
        }
      }
    }
  });

  it.each(MODES)(
    '%s：图表分类色两两不同，在面板与画布上 ≥ 3（图形对比度）',
    (mode) => {
      const vars = buildNusColorVars(mode);
      const colors = [1, 2, 3, 4, 5, 6].map((i) =>
        pick(vars, `--nus-chart-${i}`),
      );
      expect(new Set(colors).size).toBe(6);
      for (const color of colors) {
        for (const bg of ['--nus-bg-surface', '--nus-bg-canvas']) {
          expect(
            contrastRatio(color, pick(vars, bg)),
            `${mode} ${color} 在 ${bg}`,
          ).toBeGreaterThanOrEqual(3);
        }
      }
    },
  );

  it('深色遮罩比原来的 40% 更深', () => {
    expect(rgbaToHslChannels(NUS_PALETTES.dark.overlay)).toBe('0 0% 0% / 56%');
    expect(buildVbenColorVars('dark')['--overlay']).toBe('0 0% 0% / 56%');
  });

  it('混色与 HSL 换算', () => {
    expect(mix('#ffffff', '#000000', 0.5)).toBe('#808080');
    expect(mix('#0064d6', '#ffffff', 0)).toBe('#ffffff');
    expect(hexToHslChannels('#ffffff')).toBe('0 0% 100%');
    expect(hexToHslChannels('#000000')).toBe('0 0% 0%');
    expect(() => rgbaToHslChannels('rgb(0, 0, 0)')).toThrow();
  });

  it('偏好里的主色与状态色取浅色值', () => {
    expect(PREFERENCE_THEME_COLORS).toEqual({
      colorDestructive: NUS_PALETTES.light.tones.danger,
      colorPrimary: NUS_PALETTES.light.tones.primary,
      colorSuccess: NUS_PALETTES.light.tones.success,
      colorWarning: NUS_PALETTES.light.tones.warning,
    });
  });
});

describe('写进页面的样式', () => {
  it('浅色挂在 html:root:not(.dark)，深色挂在 html:root.dark；行内写的四个变量加 !important', () => {
    const css = nusThemeCss();
    const [light, dark] = css.split('\n');
    expect(light).toMatch(/^html:root:not\(\.dark\)\{--nus-bg-canvas:#f7f7f8;/);
    expect(dark).toMatch(/^html:root\.dark\{--nus-bg-canvas:#0f1011;/);
    expect(dark).toContain('--primary:212.1 100% 60% !important');
    expect(light).toContain('--primary:212 100% 42% !important');
    for (const name of INLINE_VBEN_COLOR_VARS) {
      expect(light).toContain(`${name}:`);
      expect(dark).toMatch(new RegExp(`${name}:[^;]+ !important`));
    }
    // 其余变量不加 !important
    expect(css.match(/!important/g)).toHaveLength(
      2 * INLINE_VBEN_COLOR_VARS.length,
    );
  });

  it('样式只写一次', () => {
    applyNusTheme();
    applyNusTheme();
    const styles = document.querySelectorAll(`#${NUS_THEME_STYLE_ID}`);
    expect(styles).toHaveLength(1);
    expect(styles[0]?.textContent).toBe(nusThemeCss());
    styles[0]?.remove();
  });

  it('写进页面后，深色下 --primary 盖过行内样式', () => {
    const root = document.documentElement;
    root.style.setProperty('--primary', '212 100% 45%');
    root.classList.add('dark');
    applyNusTheme();
    expect(getComputedStyle(root).getPropertyValue('--primary').trim()).toBe(
      '212.1 100% 60%',
    );
    root.classList.remove('dark');
    expect(getComputedStyle(root).getPropertyValue('--primary').trim()).toBe(
      '212 100% 42%',
    );
    document.querySelector(`#${NUS_THEME_STYLE_ID}`)?.remove();
    root.style.removeProperty('--primary');
  });
});
