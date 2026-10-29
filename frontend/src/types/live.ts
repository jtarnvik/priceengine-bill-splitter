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

/**
 * `event: split` — who had what, and what it comes to, after every change. Matches Split in Java; amounts in öre.
 * Every participant is in `people`, in the configured order: the names come from here.
 */
export interface Split {
  receiptId: string,
  /** Every item of the receipt, in its order; `people` is empty when nobody has been ticked for it. */
  items: SplitItem[],
  people: SplitPerson[],
  /** What nobody has been ticked for yet: "Kvar att dela på". */
  unassigned: { adjustmentShare: number, total: number },
  /** Whether the owner has pressed "Klar": the amounts are final, and the friends can pay. */
  done: boolean,
  /** Who to pay and how; only while done, so the owner's number reaches the page then and at no other time. */
  payment: Payment | null
}

export interface Payment {
  /** The participant who paid the bill; their row gets no Swish button. */
  payee: string,
  swishNumber: string,
  /** The restaurant and the date, already within Swish's characters and 50 of them. */
  message: string
}

export interface SplitItem {
  id: number,
  people: string[]
}

export interface SplitPerson {
  name: string,
  /** The person's share of each item they had; the shares of one item add up to its price. */
  items: { id: number, share: number }[],
  /** Their part of the discounts and rounding lines. */
  adjustmentShare: number,
  /** Rounded to whole kronor. */
  total: number
}

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting';

/** What the stream's cache entry holds: the latest of each event, plus how the connection is doing. */
export interface LiveState {
  /** `undefined` until the first `receipt` event, which the server sends on every connect; `null` is "no receipt". */
  receipt: Receipt | null | undefined,
  /** The split of `receipt`, `null` until its `split` event; never one of another receipt. */
  split: Split | null,
  status: ConnectionStatus
}
