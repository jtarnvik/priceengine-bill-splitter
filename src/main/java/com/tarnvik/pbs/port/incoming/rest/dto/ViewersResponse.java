package com.tarnvik.pbs.port.incoming.rest.dto;

/**
 * Phase 2's dummy state: how many pages are connected to the stream. Phase 5 replaces it with the receipt and split
 * events; the stream's mechanics stay.
 */
public record ViewersResponse(int viewers) {}
