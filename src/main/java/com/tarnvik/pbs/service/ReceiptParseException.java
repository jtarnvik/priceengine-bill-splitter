package com.tarnvik.pbs.service;

/**
 * Every way a parse can fail — an API error, a timeout, a refusal, an answer without items — as one exception, answered
 * 502. The message is Swedish and is shown to the owner as it is; the cause, for the log, is not.
 */
public class ReceiptParseException extends RuntimeException {
  public ReceiptParseException(String message) {
    super(message);
  }

  public ReceiptParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
