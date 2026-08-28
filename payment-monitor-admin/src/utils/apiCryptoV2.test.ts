import { describe, expect, it } from 'vitest';
import {
  API_CRYPTO_V2_UNAVAILABLE_MESSAGE,
  hasRequiredApiCryptoV2WebCrypto
} from './apiCryptoV2';

describe('api-crypto-v2 browser capability', () => {
  it('rejects an insecure-context crypto object without SubtleCrypto', () => {
    const cryptoApi = {
      getRandomValues: <T extends ArrayBufferView | null>(array: T) => array
    } as Crypto;

    expect(hasRequiredApiCryptoV2WebCrypto(cryptoApi)).toBe(false);
    expect(API_CRYPTO_V2_UNAVAILABLE_MESSAGE).toContain('HTTPS');
    expect(API_CRYPTO_V2_UNAVAILABLE_MESSAGE).toContain('localhost');
  });

  it('accepts the WebCrypto operations required by the v2 protocol', () => {
    const subtle = {
      importKey: () => undefined,
      encrypt: () => undefined,
      decrypt: () => undefined,
      deriveKey: () => undefined,
      digest: () => undefined
    } as unknown as SubtleCrypto;
    const cryptoApi = {
      subtle,
      getRandomValues: <T extends ArrayBufferView | null>(array: T) => array
    } as Crypto;

    expect(hasRequiredApiCryptoV2WebCrypto(cryptoApi)).toBe(true);
  });
});
