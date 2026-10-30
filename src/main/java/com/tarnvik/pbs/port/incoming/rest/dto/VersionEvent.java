package com.tarnvik.pbs.port.incoming.rest.dto;

/**
 * The stream's {@code version} event, the first on every connect: the server's build. A page that started with
 * another version reloads itself, so that a phone left on the page since the last dinner does not run old code
 * against a new server.
 */
public record VersionEvent(String version) {}
