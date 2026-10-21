import { describe, expect, it } from 'vitest';

import { createClientRequestId } from './client-request-id';

describe('client request id for AI homework preview', () => {
  it('uses browser randomUUID when available', () => {
    expect(createClientRequestId({ randomUUID: () => 'uuid-1' })).toBe(
      'uuid-1',
    );
  });

  it('falls back on HTTP origins without randomUUID', () => {
    expect(createClientRequestId({})).toMatch(/^\d+-[a-z0-9]+$/);
  });
});
