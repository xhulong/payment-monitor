const DEFAULT_INTERNAL_REDIRECT = '/index';
const LOCAL_ORIGIN = 'https://payment-monitor.local';

function firstString(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
}

function decodeForValidation(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function isLoginPath(pathname: string): boolean {
  return pathname.split('/').some(segment => segment === 'login');
}

function hasUnsafeRedirectCharacter(value: string): boolean {
  return Array.from(value).some(character => {
    const codePoint = character.codePointAt(0) ?? 0;
    return codePoint <= 31 || codePoint === 127 || character === "'" || character === '"';
  });
}

function normalizeFallback(value: string): string {
  const candidate = value.trim();
  if (!candidate.startsWith('/') || candidate.startsWith('//')) {
    return DEFAULT_INTERNAL_REDIRECT;
  }
  return candidate;
}

export function normalizeContextPath(value: unknown): string {
  let candidate = firstString(value).trim();
  candidate = candidate.replace(/^['"]+|['"]+$/g, '').trim();
  if (!candidate || candidate === '/') {
    return '/';
  }
  if (
    candidate.includes("'") ||
    candidate.includes('"') ||
    candidate.includes('\\') ||
    candidate.includes('?') ||
    candidate.includes('#') ||
    candidate.includes('://') ||
    candidate.includes('..')
  ) {
    return '/';
  }
  const segments = candidate.split('/').filter(Boolean);
  if (!segments.length || segments.some(segment => !/^[A-Za-z0-9._~-]+$/.test(segment))) {
    return '/';
  }
  return `/${segments.join('/')}/`;
}

export function normalizeInternalRedirect(value: unknown, fallback = DEFAULT_INTERNAL_REDIRECT): string {
  const safeFallback = normalizeFallback(fallback);
  let candidate = firstString(value).trim();
  if (!candidate) {
    return safeFallback;
  }

  const decoded = decodeForValidation(candidate);
  if (!candidate.startsWith('/') && decoded.startsWith('/')) {
    candidate = decoded;
  }
  const validationValue = decodeForValidation(candidate);
  if (
    !candidate.startsWith('/') ||
    candidate.startsWith('//') ||
    validationValue.startsWith('//') ||
    hasUnsafeRedirectCharacter(validationValue) ||
    validationValue.includes('\\')
  ) {
    return safeFallback;
  }

  try {
    const url = new URL(candidate, LOCAL_ORIGIN);
    if (url.origin !== LOCAL_ORIGIN || isLoginPath(url.pathname)) {
      return safeFallback;
    }
    return `${url.pathname}${url.search}${url.hash}` || safeFallback;
  } catch {
    return safeFallback;
  }
}
