import { describe, expect, it } from 'vitest';

import {
  PLATFORM_SHELL_PREFERENCES,
  PLATFORM_THEME_PREFERENCES,
} from '#/styles/theme/platform';

import {
  getLegacyPreferenceUpdates,
  getTimezoneStorageKey,
} from './legacy-preferences';

type Snapshot = Parameters<typeof getLegacyPreferenceUpdates>[0];

function createSnapshot(
  overrides: {
    companySiteLink?: string;
    enablePreferences?: boolean;
    languageToggle?: boolean;
    locale?: 'en-US' | 'zh-CN';
    sidebar?: Partial<Snapshot['sidebar']>;
    tabbar?: Partial<Snapshot['tabbar']>;
    theme?: Partial<Snapshot['theme']>;
    timezone?: boolean;
  } = {},
) {
  return {
    app: {
      enablePreferences: overrides.enablePreferences ?? false,
      locale: overrides.locale ?? 'zh-CN',
    },
    breadcrumb: { enable: true, ...PLATFORM_SHELL_PREFERENCES.breadcrumb },
    copyright: { companySiteLink: overrides.companySiteLink ?? '/' },
    header: { enable: true, ...PLATFORM_SHELL_PREFERENCES.header },
    sidebar: {
      collapsed: false,
      ...PLATFORM_SHELL_PREFERENCES.sidebar,
      ...overrides.sidebar,
    },
    tabbar: {
      enable: true,
      ...PLATFORM_SHELL_PREFERENCES.tabbar,
      ...overrides.tabbar,
    },
    theme: { ...PLATFORM_THEME_PREFERENCES, mode: 'dark', ...overrides.theme },
    widget: {
      languageToggle: overrides.languageToggle ?? false,
      timezone: overrides.timezone ?? false,
    },
  } as Snapshot;
}

describe('legacy preference migration', () => {
  it('keeps current platform preferences unchanged', () => {
    expect(getLegacyPreferenceUpdates(createSnapshot(), '/')).toEqual({});
  });

  it('resets a persisted locale to zh-CN', () => {
    expect(
      getLegacyPreferenceUpdates(createSnapshot({ locale: 'en-US' }), '/'),
    ).toEqual({ app: { locale: 'zh-CN' } });
  });

  it('turns off persisted language and timezone toggles', () => {
    expect(
      getLegacyPreferenceUpdates(createSnapshot({ languageToggle: true }), '/'),
    ).toEqual({ widget: { languageToggle: false, timezone: false } });
    expect(
      getLegacyPreferenceUpdates(createSnapshot({ timezone: true }), '/'),
    ).toEqual({ widget: { languageToggle: false, timezone: false } });
  });

  it('replaces retired template and deployment site links', () => {
    for (const link of ['https://www.vben.pro', 'https://legacy.example.edu']) {
      expect(
        getLegacyPreferenceUpdates(
          createSnapshot({ companySiteLink: link }),
          '/',
        ),
      ).toEqual({ copyright: { companySiteLink: '/' } });
    }
  });

  it('resets the cached V1.0.1.5 theme colors to the platform palette and keeps the theme mode', () => {
    const cached = createSnapshot({
      theme: {
        colorDestructive: 'hsl(348 100% 61%)',
        colorPrimary: 'hsl(212 100% 45%)',
        colorSuccess: 'hsl(144 57% 58%)',
        colorWarning: 'hsl(42 84% 61%)',
        mode: 'light',
      },
    });
    expect(getLegacyPreferenceUpdates(cached, '/')).toEqual({
      theme: {
        colorDestructive: PLATFORM_THEME_PREFERENCES.colorDestructive,
        colorPrimary: PLATFORM_THEME_PREFERENCES.colorPrimary,
        colorSuccess: PLATFORM_THEME_PREFERENCES.colorSuccess,
        colorWarning: PLATFORM_THEME_PREFERENCES.colorWarning,
      },
    });
  });

  it('resets radius, font size, built-in theme and semi-dark regions changed in the preferences drawer', () => {
    const cached = createSnapshot({
      theme: {
        builtinType: 'violet',
        fontSize: 18,
        radius: '1',
        semiDarkHeader: true,
        semiDarkSidebar: true,
      },
    });
    expect(getLegacyPreferenceUpdates(cached, '/')).toEqual({
      theme: {
        builtinType: 'default',
        fontSize: 16,
        radius: '0.5',
        semiDarkHeader: false,
        semiDarkSidebar: false,
      },
    });
  });

  it('resets cached shell sizes and tab bar style, keeps the collapsed sidebar', () => {
    const cached = createSnapshot({
      sidebar: { collapsed: true, width: 224 },
      tabbar: { showIcon: true, styleType: 'chrome' },
    });
    expect(getLegacyPreferenceUpdates(cached, '/')).toEqual({
      sidebar: { width: 220 },
      tabbar: { showIcon: false, styleType: 'brisk' },
    });
  });

  it('resets the login page layout switched in the old toolbar', () => {
    const cached = createSnapshot();
    cached.app.authPageLayout = 'panel-center';
    expect(getLegacyPreferenceUpdates(cached, '/')).toEqual({
      app: { authPageLayout: 'panel-right' },
    });
  });

  it('closes the preferences drawer cached as enabled', () => {
    expect(
      getLegacyPreferenceUpdates(
        createSnapshot({ enablePreferences: true }),
        '/',
      ),
    ).toEqual({ app: { enablePreferences: false } });
  });

  it('builds the timezone store key used by pinia persistence', () => {
    expect(getTimezoneStorageKey('NUS-vben-antd-5.5.9-prod')).toBe(
      'NUS-vben-antd-5.5.9-prod-core-timezone',
    );
  });
});
