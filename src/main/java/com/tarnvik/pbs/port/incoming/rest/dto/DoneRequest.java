package com.tarnvik.pbs.port.incoming.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Done or not, as a whole state, never a toggle: a request repeated or arriving late then changes nothing it should
 * not.
 *
 * @param receiptId the receipt the owner's page holds
 */
public record DoneRequest(@NotNull UUID receiptId, @NotNull Boolean done) {}
