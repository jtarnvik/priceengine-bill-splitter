import type { Receipt } from '../communication/api/generated-api';

/**
 * The stream's payloads. Hand-written: springdoc cannot see an SseEmitter's events, so codegen has nothing to
 * generate them from (see "Frontend data layer" in CLAUDE.md). The receipt inside them is the generated type, since
 * the upload answers with the same one.
 */

/** `event: receipt` — the whole receipt after an upload, `null` after a reset. Matches ReceiptEvent in Java. */
export interface ReceiptEvent {
  receipt: Receipt | null
}

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting';

/** What the stream's cache entry holds: the latest of each event, plus how the connection is doing. */
export interface LiveState {
  /** `undefined` until the first `receipt` event, which the server sends on every connect; `null` is "no receipt". */
  receipt: Receipt | null | undefined,
  status: ConnectionStatus
}
