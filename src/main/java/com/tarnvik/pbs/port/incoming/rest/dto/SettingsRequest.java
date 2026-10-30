package com.tarnvik.pbs.port.incoming.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The owner's settings, saved as a whole.
 *
 * @param names as the dialog's field split on commas: the server trims each and drops empty ones
 * @param receiptParser {@code claude} or {@code canned}
 */
public record SettingsRequest(@NotNull List<@NotNull String> names, @NotNull String payee,
                              @NotNull String receiptParser) {}
