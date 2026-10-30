package com.tarnvik.pbs.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The owner's settings, in memory: a restart brings back {@code pbs.participants}, {@code pbs.payee} and
 * {@code pbs.receipt-parser}.
 *
 * @param names the people at the table, in the order the pages show them, two to four, each once
 * @param payee the one of them who paid the bill and gets no Swish button
 * @param receiptParser {@code claude} or {@code canned}: which parser answers the next upload
 */
public record Settings(
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> names,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String payee,
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String receiptParser
) {}
