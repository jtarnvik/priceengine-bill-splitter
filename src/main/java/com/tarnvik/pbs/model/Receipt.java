package com.tarnvik.pbs.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The receipt as the browser gets it: what Claude read, with an id per item and the result of the sum check. Sent
 * in the upload's answer and in the stream's {@code receipt} event; the frontend has it as a generated type, which
 * its hand-written stream types in live.ts use.
 *
 * @param receiptId new on every upload, so that a request meant for an earlier receipt can be told apart
 * @param linesTotal the items and adjustments added up, in öre
 * @param totalMatches whether linesTotal is the receipt's own total. When it is not, a price was most likely misread
 */
public record Receipt(
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID receiptId,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String restaurantName,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReceiptItem> items,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReceiptAdjustment> adjustments,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long linesTotal,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean totalMatches
) {}
