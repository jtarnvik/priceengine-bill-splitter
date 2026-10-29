package com.tarnvik.pbs.port.incoming.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** An error the frontend shows as it is: errorMessage() in error-message.ts reads {@code message}. */
public record ErrorMessageResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
