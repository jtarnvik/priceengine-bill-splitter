package com.tarnvik.pbs.port.outgoing.file;

import com.tarnvik.pbs.model.ParsedReceipt;

/**
 * One real parse, as stored beside its JPEG: Claude's answer before numbering, and what it cost. The cost fields are
 * null in a hand-written recording, which needs only parsedReceipt.
 *
 * @param outputTokens thinking included
 * @param durationMillis the API call alone, from request to answer
 */
public record Recording(
  ParsedReceipt parsedReceipt,
  String model,
  String effort,
  Long inputTokens,
  Long outputTokens,
  Long durationMillis
) {}
