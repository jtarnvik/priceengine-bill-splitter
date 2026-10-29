package com.tarnvik.pbs.service;

/** A name that is not among the participants. Answered 400; the message is Swedish and shown as it is. */
public class UnknownPersonException extends RuntimeException {
  public UnknownPersonException(String name) {
    super(name + " är inte med och delar.");
  }
}
