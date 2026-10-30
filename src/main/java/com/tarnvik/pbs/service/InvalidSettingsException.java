package com.tarnvik.pbs.service;

/** Settings the owner cannot save as they are. Answered 400; the message is Swedish and shown as it is. */
public class InvalidSettingsException extends RuntimeException {
  public InvalidSettingsException(String message) {
    super(message);
  }
}
