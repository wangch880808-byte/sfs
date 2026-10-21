export function createClientRequestId(
  browserCrypto: undefined | { randomUUID?: () => string } = globalThis.crypto,
) {
  if (typeof browserCrypto?.randomUUID === 'function') {
    return browserCrypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
