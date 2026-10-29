package com.tarnvik.pbs.model;

import java.time.Instant;

/**
 * The one receipt there is and how far it has been split: what ReceiptService holds, and what a page is sent on
 * connect. Immutable, so a snapshot can be serialised for every listener without locking; every change builds a new
 * one.
 *
 * @param uploadedAt when the receipt was uploaded: its date goes into the Swish message
 * @param changedAt the last change — upload, tick, "Klar", undo — from which the receipt expires
 */
public record ReceiptState(Receipt receipt, Split split, Instant uploadedAt, Instant changedAt) {}
