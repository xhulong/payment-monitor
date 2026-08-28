import type { PairingCodeVO, PairingStatusVO } from './types';

export interface PairingQrPayload {
  schema: number;
  serverUrl: string;
  pairingCode: string;
}

export const buildPairingQrPayload = (
  pairing: Pick<PairingCodeVO, 'qrSchema' | 'serverUrl' | 'pairingCode'>
): PairingQrPayload => ({
  schema: pairing.qrSchema,
  serverUrl: pairing.serverUrl,
  pairingCode: pairing.pairingCode
});

export const serializePairingQrPayload = (
  pairing: Pick<PairingCodeVO, 'qrSchema' | 'serverUrl' | 'pairingCode'>
): string => JSON.stringify(buildPairingQrPayload(pairing));

interface PairingStatusPollerOptions {
  intervalMs?: number;
  loadStatus: (pairingSessionId: string | number) => Promise<PairingStatusVO>;
  onStatus: (status: PairingStatusVO) => void | Promise<void>;
  onError?: (error: unknown) => void;
}

export interface PairingStatusPoller {
  start: (pairingSessionId: string | number) => void;
  stop: () => void;
  isRunning: () => boolean;
}

export const createPairingStatusPoller = ({
  intervalMs = 1500,
  loadStatus,
  onStatus,
  onError
}: PairingStatusPollerOptions): PairingStatusPoller => {
  let active = false;
  let pairingSessionId: string | number | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let requestInFlight = false;
  let generation = 0;

  const clearTimer = () => {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  };

  const stop = () => {
    active = false;
    pairingSessionId = undefined;
    generation += 1;
    clearTimer();
  };

  const schedule = () => {
    clearTimer();
    if (!active || pairingSessionId === undefined) return;
    timer = setTimeout(poll, intervalMs);
  };

  const poll = async () => {
    timer = undefined;
    if (!active || pairingSessionId === undefined) return;
    if (requestInFlight) {
      schedule();
      return;
    }

    const currentSessionId = pairingSessionId;
    const currentGeneration = generation;
    requestInFlight = true;
    try {
      const status = await loadStatus(currentSessionId);
      if (
        !active ||
        generation !== currentGeneration ||
        pairingSessionId !== currentSessionId
      ) {
        return;
      }
      await onStatus(status);
      if (status.status === 'PENDING') {
        schedule();
      } else {
        stop();
      }
    } catch (error) {
      if (
        !active ||
        generation !== currentGeneration ||
        pairingSessionId !== currentSessionId
      ) {
        return;
      }
      onError?.(error);
      schedule();
    } finally {
      requestInFlight = false;
    }
  };

  return {
    start(sessionId) {
      stop();
      active = true;
      pairingSessionId = sessionId;
      schedule();
    },
    stop,
    isRunning: () => active
  };
};
