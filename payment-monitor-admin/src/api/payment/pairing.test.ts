import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PairingStatusVO } from './types';
import {
  buildPairingQrPayload,
  createPairingStatusPoller,
  serializePairingQrPayload
} from './pairing';

describe('payment pairing QR payload', () => {
  const pairing = {
    qrSchema: 1,
    serverUrl: 'http://localhost:8080',
    pairingCode: '12345678'
  };

  it('uses the fixed schema, server URL and eight-digit code shape', () => {
    expect(buildPairingQrPayload(pairing)).toEqual({
      schema: 1,
      serverUrl: 'http://localhost:8080',
      pairingCode: '12345678'
    });
  });

  it('serializes the exact QR JSON contract without extra fields', () => {
    expect(serializePairingQrPayload(pairing)).toBe(
      '{"schema":1,"serverUrl":"http://localhost:8080","pairingCode":"12345678"}'
    );
  });
});

describe('payment pairing status poller', () => {
  const pendingStatus: PairingStatusVO = {
    pairingSessionId: 'session-1',
    status: 'PENDING',
    expiresAt: '2026-07-20T12:00:00+08:00'
  };

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not overlap status requests and schedules the next request after completion', async () => {
    vi.useFakeTimers();
    let resolveFirstRequest: ((value: PairingStatusVO) => void) | undefined;
    const loadStatus = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise<PairingStatusVO>(resolve => {
            resolveFirstRequest = resolve;
          })
      )
      .mockResolvedValue(pendingStatus);
    const onStatus = vi.fn();
    const poller = createPairingStatusPoller({ loadStatus, onStatus });

    poller.start('session-1');
    vi.advanceTimersByTime(1500);
    await Promise.resolve();
    expect(loadStatus).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(4500);
    await Promise.resolve();
    expect(loadStatus).toHaveBeenCalledTimes(1);

    resolveFirstRequest?.(pendingStatus);
    await Promise.resolve();
    await Promise.resolve();
    vi.advanceTimersByTime(1500);
    await Promise.resolve();
    expect(loadStatus).toHaveBeenCalledTimes(2);

    poller.stop();
  });

  it('stops polling after pairing succeeds', async () => {
    vi.useFakeTimers();
    const pairedStatus: PairingStatusVO = {
      ...pendingStatus,
      status: 'PAIRED',
      deviceId: 'device-1',
      deviceName: '红米测试机',
      pairedAt: '2026-07-20T11:58:00+08:00'
    };
    const loadStatus = vi.fn().mockResolvedValue(pairedStatus);
    const onStatus = vi.fn();
    const poller = createPairingStatusPoller({ loadStatus, onStatus });

    poller.start('session-1');
    await vi.advanceTimersByTimeAsync(1500);

    expect(onStatus).toHaveBeenCalledWith(pairedStatus);
    expect(poller.isRunning()).toBe(false);

    await vi.advanceTimersByTimeAsync(6000);
    expect(loadStatus).toHaveBeenCalledTimes(1);
  });

  it('stops future requests when the dialog closes', async () => {
    vi.useFakeTimers();
    const loadStatus = vi.fn().mockResolvedValue(pendingStatus);
    const poller = createPairingStatusPoller({
      loadStatus,
      onStatus: vi.fn()
    });

    poller.start('session-1');
    poller.stop();
    await vi.advanceTimersByTimeAsync(3000);

    expect(loadStatus).not.toHaveBeenCalled();
    expect(poller.isRunning()).toBe(false);
  });
});
