package com.tarnvik.pbs.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line of the receipt. Amounts in öre; price is the line's total and the one that is split, quantity and unit
 * price are information.
 *
 * @param id 1, 2, 3 … in receipt order and never reused, so that an id stays valid when rows are removed (phase 8).
 *           The list's order is the receipt's; the id does not define it
 */
public record ReceiptItem(
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int id,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long unitPrice,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long price,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note
) {}
