package com.tarnvik.pbs.port.incoming.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * The whole set of people for one item, never a toggle: a request repeated or arriving late then changes nothing it
 * should not. An empty list makes the item unassigned.
 *
 * @param receiptId the receipt the owner's page holds, so that a tab left on an earlier receipt cannot tick the
 *                  current one's rows by id
 */
public record AssigneesRequest(@NotNull UUID receiptId, @NotNull List<@NotNull String> people) {}
