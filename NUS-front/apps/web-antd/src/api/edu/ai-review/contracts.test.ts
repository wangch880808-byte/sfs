import { describe, expect, it } from 'vitest';

import { parseAiTaskStartResponse } from './contracts';

describe('AI task start contract', () => {
  it('accepts the only supported response shape', () => {
    expect(parseAiTaskStartResponse({ taskId: 12 })).toEqual({ taskId: 12 });
  });

  it.each([12, { id: 12 }, { taskId: '12' }, { taskId: 0 }])(
    'rejects incompatible payload %o',
    (payload) => expect(() => parseAiTaskStartResponse(payload)).toThrow(),
  );
});
