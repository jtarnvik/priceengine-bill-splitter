package com.tarnvik.pbs.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** A discount or a rounding line, in öre, negative when it lowers the total. */
public record ReceiptAdjustment(
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount
) {}
