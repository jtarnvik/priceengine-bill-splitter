package com.tarnvik.pbs.service;

/**
 * A change meant for a receipt that is no longer the current one — replaced by an upload, emptied by a reset — or
 * when there is none. Answered 409. Without it, a second open tab could apply ticks meant for the previous receipt
 * to the new one by item id. The message is Swedish and shown to the owner as it is.
 */
public class StaleReceiptException extends RuntimeException {
  public StaleReceiptException() {
    super("Kvittot har bytts ut sedan sidan laddades. Ladda om sidan.");
  }
}
