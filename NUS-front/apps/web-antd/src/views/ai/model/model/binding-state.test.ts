import { describe, expect, it } from 'vitest';

import { mergeBindingDrafts } from './binding-state';

const binding = (
  businessCode: string,
  modelId: number,
  modelName = 'model',
) => ({
  available: true,
  businessCode,
  businessName: businessCode,
  modelId,
  modelName,
});

describe('refresh business model bindings', () => {
  it('preserves unsaved selections while refreshing untouched bindings', () => {
    const result = mergeBindingDrafts(
      [binding('a', 1, 'renamed'), binding('b', 4)],
      [binding('a', 3, 'draft'), binding('b', 2)],
      new Map([
        ['a', 1],
        ['b', 2],
      ]),
    );
    expect(result.map((item) => item.modelId)).toEqual([3, 4]);
  });
  it('reflects renamed or unavailable saved models without overwriting draft IDs', () => {
    const latest = [{ ...binding('a', 1, 'renamed'), available: false }];
    expect(
      mergeBindingDrafts(latest, [binding('a', 1)], new Map([['a', 1]])),
    ).toEqual(latest);
    expect(
      mergeBindingDrafts(latest, [binding('a', 9)], new Map([['a', 1]]))[0]
        ?.modelId,
    ).toBe(9);
  });
  it('initializes new business bindings from the server', () => {
    const latest = [binding('a', 1)];
    expect(mergeBindingDrafts(latest, [], new Map())).toEqual(latest);
  });
});
