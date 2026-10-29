package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;

/**
 * Reads a receipt photo into its lines. Two implementations, chosen by {@code pbs.receipt-parser}: ClaudeReceiptParser
 * (the default), which calls the API and records every parse, and CannedReceiptParser, which answers with those
 * recordings instead, for development at no cost.
 */
public interface ReceiptParser {
  /**
   * @param jpeg the photo as the browser uploaded it, already downscaled and re-encoded as JPEG
   * @throws ReceiptParseException when no answer could be had, with a message fit to show the owner
   */
  ParsedReceipt parse(byte[] jpeg);
}
