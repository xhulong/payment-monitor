import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const configs = [
  'deploy/nginx.ports.conf',
  'deploy/nginx.production.conf'
];

describe.each(configs)('%s public payment proxy', (configPath) => {
  it('keeps dynamic QR SVG requests away from the static asset handler', () => {
    const config = readFileSync(resolve(process.cwd(), configPath), 'utf8');
    const publicPaymentLocation =
      'location ^~ /api/public/payment-orders/';
    const locationStart = config.indexOf(publicPaymentLocation);
    const nextApiLocation = config.indexOf('location /api/', locationStart);

    expect(locationStart).toBeGreaterThanOrEqual(0);
    expect(locationStart).toBeLessThan(
      config.indexOf('location ~* \\.(?:js|mjs|css')
    );
    expect(config.slice(locationStart, nextApiLocation)).toContain(
      'proxy_pass http://backend:8080'
    );
  });
});
