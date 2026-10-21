import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  canSubmitHomework,
  formatDeadlineTime,
  getDefaultRedoDeadline,
  isDeadlinePassed,
  isRedoDeadlineEffective,
  isValidRedoDeadline,
  resolveEffectiveDeadline,
  toDeadlineTime,
} from './homework-deadline';

const at = (value: string) => dayjs(value).valueOf();
const NOW = at('2026-09-26 10:00:00');
const PAST = at('2026-09-20 18:00:00');
const FUTURE = at('2026-09-30 18:00:00');

describe('homework deadline parsing', () => {
  it('parses millisecond, second and string values', () => {
    expect(toDeadlineTime(FUTURE)).toBe(FUTURE);
    expect(toDeadlineTime(FUTURE / 1000)).toBe(FUTURE);
    expect(toDeadlineTime(String(FUTURE))).toBe(FUTURE);
    expect(toDeadlineTime('2026-09-30 18:00:00')).toBe(FUTURE);
  });

  it('returns undefined for blank or invalid values', () => {
    expect(toDeadlineTime(undefined)).toBeUndefined();
    expect(toDeadlineTime(null)).toBeUndefined();
    expect(toDeadlineTime('')).toBeUndefined();
    expect(toDeadlineTime('not-a-date')).toBeUndefined();
  });

  it('formats deadlines to minutes', () => {
    expect(formatDeadlineTime(FUTURE)).toBe('2026-09-30 18:00');
    expect(formatDeadlineTime(undefined)).toBe('-');
  });
});

describe('effective deadline', () => {
  it('prefers the backend effective deadline', () => {
    expect(
      resolveEffectiveDeadline({
        deadline: PAST,
        effectiveDeadline: FUTURE,
        redoDeadline: PAST,
      }),
    ).toBe(FUTURE);
  });

  it('uses the redo deadline before the homework deadline', () => {
    expect(
      resolveEffectiveDeadline({ deadline: PAST, redoDeadline: FUTURE }),
    ).toBe(FUTURE);
    expect(
      resolveEffectiveDeadline({ deadline: PAST, redoDeadline: null }),
    ).toBe(PAST);
  });

  it('marks the redo deadline as effective only for returned records', () => {
    expect(isRedoDeadlineEffective({ redoDeadline: FUTURE, status: 3 })).toBe(
      true,
    );
    expect(isRedoDeadlineEffective({ redoDeadline: FUTURE, status: 2 })).toBe(
      false,
    );
    expect(isRedoDeadlineEffective({ redoDeadline: null, status: 3 })).toBe(
      false,
    );
  });

  it('treats a missing deadline as open', () => {
    expect(isDeadlinePassed({}, NOW)).toBe(false);
    expect(isDeadlinePassed({ deadline: PAST }, NOW)).toBe(true);
  });
});

describe('student submit availability', () => {
  it('allows first submission only before the homework deadline', () => {
    expect(canSubmitHomework({ deadline: FUTURE, status: 0 }, NOW)).toBe(true);
    expect(canSubmitHomework({ deadline: PAST, status: 0 }, NOW)).toBe(false);
    expect(canSubmitHomework({ status: 0 }, NOW)).toBe(true);
  });

  it('allows resubmission within the redo deadline after the homework deadline', () => {
    expect(
      canSubmitHomework(
        { deadline: PAST, redoDeadline: FUTURE, status: 3 },
        NOW,
      ),
    ).toBe(true);
    expect(
      canSubmitHomework({ deadline: PAST, redoDeadline: PAST, status: 3 }, NOW),
    ).toBe(false);
  });

  it('respects canSubmit=false from the backend', () => {
    expect(
      canSubmitHomework({ canSubmit: false, deadline: FUTURE, status: 0 }, NOW),
    ).toBe(false);
  });

  it('does not allow submitting pending or graded records', () => {
    for (const status of [1, 2, 4]) {
      expect(canSubmitHomework({ deadline: FUTURE, status }, NOW)).toBe(false);
    }
  });
});

describe('redo deadline defaults', () => {
  it('extends seven days from the homework deadline when it is later', () => {
    expect(getDefaultRedoDeadline(FUTURE, NOW)).toBe('2026-10-07 18:00:00');
  });

  it('extends seven days from now when the homework has closed', () => {
    expect(getDefaultRedoDeadline(PAST, NOW)).toBe('2026-10-03 10:00:00');
    expect(getDefaultRedoDeadline(undefined, NOW)).toBe('2026-10-03 10:00:00');
  });

  it('drops seconds from the default value', () => {
    expect(getDefaultRedoDeadline(undefined, at('2026-09-26 10:00:42'))).toBe(
      '2026-10-03 10:00:00',
    );
  });

  it('requires the redo deadline to be later than now', () => {
    expect(isValidRedoDeadline('2026-09-26 10:00:01', NOW)).toBe(true);
    expect(isValidRedoDeadline('2026-09-26 10:00:00', NOW)).toBe(false);
    expect(isValidRedoDeadline(undefined, NOW)).toBe(false);
  });
});
