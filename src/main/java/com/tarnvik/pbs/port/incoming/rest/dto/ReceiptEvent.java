package com.tarnvik.pbs.port.incoming.rest.dto;

import com.tarnvik.pbs.model.Receipt;

/**
 * The stream's {@code receipt} event: the whole receipt after an upload, {@code null} after a reset or when there has
 * been none. Wrapped rather than sent bare, so that "no receipt" is {@code {"receipt": null}} and not an empty data
 * line.
 */
public record ReceiptEvent(Receipt receipt) {}
