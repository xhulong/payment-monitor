export const formatApiTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const milliseconds = String(date.getMilliseconds()).padStart(3, '0');
  return `${date.toLocaleString()}.${milliseconds}`;
};

export const toUtcIso = (value?: Date | string) => {
  if (!value) return undefined;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
};
