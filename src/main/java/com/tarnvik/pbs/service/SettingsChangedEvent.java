package com.tarnvik.pbs.service;

/**
 * Published on every save of the owner's settings, changed or not. The terminal dashboard repaints on it, so that its
 * parser line follows the setting at once.
 */
public record SettingsChangedEvent() {}
