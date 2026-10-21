import type { AiModelModelApi } from '#/api/ai/model/model';

/** Refresh server data without discarding locally edited selections. */
export function mergeBindingDrafts(
  incoming: AiModelModelApi.BusinessModelBinding[],
  drafts: AiModelModelApi.BusinessModelBinding[],
  saved: Map<string, number | undefined>,
) {
  const draftMap = new Map(drafts.map((draft) => [draft.businessCode, draft]));
  return incoming.map((binding) => {
    const draft = draftMap.get(binding.businessCode);
    return draft && draft.modelId !== saved.get(binding.businessCode)
      ? { ...binding, modelId: draft.modelId, modelName: draft.modelName }
      : { ...binding };
  });
}
