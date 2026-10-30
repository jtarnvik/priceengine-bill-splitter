package com.tarnvik.pbs.service;

/**
 * Published when the owner changes the names. ReceiptService listens and resets the receipt: its ticks name people
 * who may no longer be at the table. An event rather than a call, since ReceiptService itself asks SettingsService for
 * the names.
 */
public record NamesChangedEvent() {}
