package com.tarnvik.pbs.service;

/** "Klar" while something is still unassigned. Answered 409; the message is Swedish and shown as it is. */
public class NotAllAssignedException extends RuntimeException {
  public NotAllAssignedException() {
    super("Allt är inte fördelat än.");
  }
}
