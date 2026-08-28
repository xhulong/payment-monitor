import axiosModule from 'axios';

const axios = axiosModule as any;

export const API_CRYPTO_V2_CONTENT_TYPE = 'application/vnd.paymentmonitor.crypto+json';
export const API_CRYPTO_V2_VERSION = '2';
export const API_CRYPTO_V2_VERSION_HEADER = 'X-Api-Crypto-Version';
export const API_CRYPTO_V2_UNAVAILABLE_MESSAGE =
  '当前访问地址不支持浏览器安全加密。请使用 HTTPS 访问；仅在部署电脑本机调试时可使用 http://localhost:5173。';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const hkdfSaltSource = encoder.encode('payment-monitor/api-crypto-v2');
const GCM_TAG_BYTES = 16;

interface ApiCryptoJwk {
  kty: 'RSA';
  kid: string;
  use: 'enc';
  alg: 'RSA-OAEP-256';
  n: string;
  e: string;
}

interface ApiCryptoJwksResponse {
  activeKid?: string;
  keys: ApiCryptoJwk[];
}

export interface ApiCryptoV2Context {
  jti: string;
  method: string;
  path: string;
  masterKey: Uint8Array;
  responseKey: CryptoKey;
}

export interface ApiCryptoV2Envelope {
  v: number;
  kid?: string;
  jti: string;
  ts: number;
  status?: number;
  wrappedKey?: string;
  iv: string;
  ciphertext: string;
  tag: string;
}

let jwksCache: {
  expiresAt: number;
  activeKid: string;
  keys: Map<string, CryptoKey>;
} | undefined;

export function isApiCryptoV2Enabled(): boolean {
  return import.meta.env.VITE_APP_API_CRYPTO_V2 === 'true';
}

export function hasRequiredApiCryptoV2WebCrypto(
  cryptoApi: Crypto | null | undefined
): boolean {
  const subtle = cryptoApi?.subtle;
  return Boolean(
    cryptoApi &&
    typeof cryptoApi.getRandomValues === 'function' &&
    subtle &&
    typeof subtle.importKey === 'function' &&
    typeof subtle.encrypt === 'function' &&
    typeof subtle.decrypt === 'function' &&
    typeof subtle.deriveKey === 'function' &&
    typeof subtle.digest === 'function'
  );
}

export function isApiCryptoV2Available(): boolean {
  return hasRequiredApiCryptoV2WebCrypto(globalThis.crypto);
}

export async function encryptApiCryptoV2Request(
  data: unknown,
  method: string,
  path: string,
  baseApi: string,
  forceJwksRefresh = false
): Promise<{ envelope: ApiCryptoV2Envelope; context: ApiCryptoV2Context }> {
  requireWebCrypto();
  const normalizedMethod = method.toUpperCase();
  const normalizedPath = normalizePath(path, baseApi);
  const keySet = await getJwks(baseApi, forceJwksRefresh);
  const publicKey = keySet.keys.get(keySet.activeKid);
  if (!publicKey) {
    throw new Error('api-crypto-v2 active public key is unavailable');
  }

  const masterKey = randomBytes(32);
  const requestKey = await importAesKey(masterKey, ['encrypt']);
  const jti = randomUuid();
  const timestamp = Math.floor(Date.now() / 1000);
  const aad = encoder.encode(
    `2\nREQUEST\n${normalizedMethod}\n${normalizedPath}\n${keySet.activeKid}\n${jti}\n${timestamp}`
  );
  const plaintext = encoder.encode(JSON.stringify(data ?? null));
  const iv = randomBytes(12);
  const encrypted = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 },
      requestKey,
      plaintext
    )
  );
  const { ciphertext, tag } = splitGcmCiphertext(encrypted);
  const wrappedKey = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, masterKey)
  );
  const responseKey = await deriveResponseKey(masterKey, jti, normalizedMethod, normalizedPath);

  return {
    envelope: {
      v: 2,
      kid: keySet.activeKid,
      jti,
      ts: timestamp,
      wrappedKey: encodeBase64Url(wrappedKey),
      iv: encodeBase64Url(iv),
      ciphertext: encodeBase64Url(ciphertext),
      tag: encodeBase64Url(tag)
    },
    context: {
      jti,
      method: normalizedMethod,
      path: normalizedPath,
      masterKey,
      responseKey
    }
  };
}

export async function decryptApiCryptoV2Response(
  data: unknown,
  responseStatus: number,
  context: ApiCryptoV2Context
): Promise<unknown> {
  requireWebCrypto();
  const envelope = parseEnvelope(data);
  if (
    envelope.v !== 2 ||
    envelope.jti !== context.jti ||
    !Number.isInteger(envelope.status) ||
    envelope.status !== responseStatus ||
    !envelope.iv ||
    !envelope.ciphertext ||
    !envelope.tag
  ) {
    throw new Error('API_CRYPTO_INVALID');
  }

  const timestamp = envelope.ts;
  if (!Number.isInteger(timestamp) || Math.abs(Math.floor(Date.now() / 1000) - timestamp) > 180) {
    throw new Error('API_CRYPTO_INVALID');
  }

  const aad = encoder.encode(
    `2\nRESPONSE\n${envelope.status}\n${context.path}\n${context.jti}\n${timestamp}`
  );
  const ciphertext = concatBytes(
    decodeBase64Url(envelope.ciphertext),
    decodeBase64Url(envelope.tag)
  );
  try {
    const plaintext = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: decodeBase64Url(envelope.iv),
        additionalData: aad,
        tagLength: 128
      },
      context.responseKey,
      ciphertext
    );
    const text = decoder.decode(plaintext);
    return text ? JSON.parse(text) : undefined;
  } finally {
    context.masterKey.fill(0);
  }
}

export function normalizePath(url: string, baseApi: string): string {
  const raw = String(url || '');
  try {
    const parsed = new URL(raw, window.location.origin);
    return parsed.pathname.replace(baseApi.replace(/\/+$/, ''), '') || '/';
  } catch {
    const withoutQuery = raw.split('?', 1)[0];
    const base = baseApi.replace(/\/+$/, '');
    const path = withoutQuery.startsWith(base)
      ? withoutQuery.slice(base.length)
      : withoutQuery;
    return path.startsWith('/') ? path : `/${path}`;
  }
}

async function getJwks(baseApi: string, force = false) {
  const now = Date.now();
  if (!force && jwksCache && jwksCache.expiresAt > now) {
    return jwksCache;
  }

  const response = (await axios.get(
    `${baseApi.replace(/\/+$/, '')}/api/v2/crypto/jwks`,
    { withCredentials: true }
  )) as { data: ApiCryptoJwksResponse };
  const parsed =
    typeof response.data === 'string'
      ? (JSON.parse(response.data) as ApiCryptoJwksResponse | { data: ApiCryptoJwksResponse })
      : response.data;
  const payload =
    'data' in parsed && parsed.data?.keys
      ? parsed.data
      : (parsed as ApiCryptoJwksResponse);
  const keys = new Map<string, CryptoKey>();
  for (const jwk of payload.keys || []) {
    if (jwk.kty !== 'RSA' || jwk.alg !== 'RSA-OAEP-256' || !jwk.kid) {
      continue;
    }
    const key = await crypto.subtle.importKey(
      'jwk',
      jwk as JsonWebKey,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt']
    );
    keys.set(jwk.kid, key);
  }
  const activeKid =
    payload.activeKid && keys.has(payload.activeKid)
      ? payload.activeKid
      : payload.keys?.find(key => key.kid && keys.has(key.kid))?.kid;
  if (!activeKid) {
    throw new Error('api-crypto-v2 JWKS has no usable RSA key');
  }
  jwksCache = {
    expiresAt: now + 5 * 60 * 1000,
    activeKid,
    keys
  };
  return jwksCache;
}

async function importAesKey(rawKey: Uint8Array, usages: KeyUsage[]) {
  return crypto.subtle.importKey(
    'raw',
    asBufferSource(rawKey),
    { name: 'AES-GCM', length: 256 },
    false,
    usages
  );
}

async function deriveResponseKey(
  masterKey: Uint8Array,
  jti: string,
  method: string,
  path: string
) {
  const hkdfKey = await crypto.subtle.importKey(
    'raw',
    asBufferSource(masterKey),
    { name: 'HKDF' },
    false,
    ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: await sha256(hkdfSaltSource),
      info: encoder.encode(`response|${jti}|${method}|${path}`)
    },
    hkdfKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );
}

async function sha256(value: Uint8Array) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', asBufferSource(value)));
}

function parseEnvelope(data: unknown): ApiCryptoV2Envelope {
  if (typeof data === 'string') {
    return JSON.parse(data) as ApiCryptoV2Envelope;
  }
  if (data && typeof data === 'object') {
    return data as ApiCryptoV2Envelope;
  }
  throw new Error('API_CRYPTO_INVALID');
}

function splitGcmCiphertext(data: Uint8Array) {
  if (data.length <= GCM_TAG_BYTES) {
    throw new Error('api-crypto-v2 ciphertext is too short');
  }
  return {
    ciphertext: data.slice(0, -GCM_TAG_BYTES),
    tag: data.slice(-GCM_TAG_BYTES)
  };
}

function concatBytes(...parts: Uint8Array[]) {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const result = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}

function randomBytes(length: number) {
  const result = new Uint8Array(length);
  requireWebCrypto().getRandomValues(result);
  return result;
}

function randomUuid() {
  const cryptoApi = requireWebCrypto();
  if (typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }
  const bytes = randomBytes(16);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function encodeBase64Url(bytes: Uint8Array) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function decodeBase64Url(value: string) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const result = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    result[i] = binary.charCodeAt(i);
  }
  return result;
}

function asBufferSource(value: Uint8Array): BufferSource {
  return value as unknown as BufferSource;
}

function requireWebCrypto(): Crypto {
  const cryptoApi = globalThis.crypto;
  if (!hasRequiredApiCryptoV2WebCrypto(cryptoApi)) {
    throw new Error(API_CRYPTO_V2_UNAVAILABLE_MESSAGE);
  }
  return cryptoApi;
}
