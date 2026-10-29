package com.tarnvik.pbs.service;

/** No item with that id on the current receipt. Answered 404; the message is Swedish and shown as it is. */
public class UnknownItemException extends RuntimeException {
  public UnknownItemException(int itemId) {
    super("Rad " + itemId + " finns inte på kvittot.");
  }
}
