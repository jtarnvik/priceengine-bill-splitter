package com.tarnvik.pbs.port.incoming.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the owner's page needs to know about how pbs is set up. Phase 9's settings page extends it with the names and
 * makes the parser switchable.
 *
 * @param receiptParser {@code claude} or {@code canned}: the page offers random ticking only on canned receipts,
 *                      where testing a long one by hand gets tedious
 */
public record SettingsResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String receiptParser) {}
