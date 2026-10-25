/**
 * The stream's payloads. Hand-written: springdoc cannot see an SseEmitter's events, so codegen has nothing to
 * generate them from (see "Frontend data layer" in CLAUDE.md).
 */

/** `event: viewers` — phase 2's dummy state, the number of pages connected. Matches ViewersResponse in Java. */
export interface ViewersEvent {
  viewers: number
}

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting';

/** What the stream's cache entry holds: the latest of each event, plus how the connection is doing. */
export interface LiveState {
  viewers: number | null,
  status: ConnectionStatus
}
