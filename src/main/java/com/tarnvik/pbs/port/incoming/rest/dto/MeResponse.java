package com.tarnvik.pbs.port.incoming.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The logged-in owner. Response fields need requiredMode = REQUIRED, or the generated TypeScript type makes them
 * optional.
 */
public record MeResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {}
