package com.tarnvik.pbs.service;

/**
 * A tick while the split is marked done. Answered 409: the amounts in the Swish buttons must not change under a
 * friend who is paying; the owner undoes "Klar" first. The message is Swedish and shown as it is.
 */
public class SplitDoneException extends RuntimeException {
  public SplitDoneException() {
    super("Notan är klar. Ångra klar för att ändra.");
  }
}
