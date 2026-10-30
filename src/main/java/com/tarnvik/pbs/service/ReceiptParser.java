package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;

/**
 * Reads a receipt photo into its lines. Two implementations, both always present: ClaudeReceiptParser, which calls
 * the API and records every parse, and CannedReceiptParser, which answers with those recordings instead, at no cost.
 * The owner's settings pick one per upload (SettingsService).
 */
public interface ReceiptParser {
  /** The setting's value for this parser: {@code claude} or {@code canned}. */
  String name();

  /**
   * @param jpeg the photo as the browser uploaded it, already downscaled and re-encoded as JPEG
   * @throws ReceiptParseException when no answer could be had, with a message fit to show the owner
   */
  ParsedReceipt parse(byte[] jpeg);
}
