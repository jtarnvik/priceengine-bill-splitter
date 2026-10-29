package com.tarnvik.pbs.model;

/**
 * The one receipt there is and how far it has been split: what ReceiptService holds, and what a page is sent on
 * connect. Immutable, so a snapshot can be serialised for every listener without locking; every change builds a new
 * one.
 */
public record ReceiptState(Receipt receipt, Split split) {}
