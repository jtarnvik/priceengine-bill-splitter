package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.incoming.rest.dto.ErrorMessageResponse;
import com.tarnvik.pbs.service.InvalidSettingsException;
import com.tarnvik.pbs.service.NotAllAssignedException;
import com.tarnvik.pbs.service.ReceiptParseException;
import com.tarnvik.pbs.service.SplitDoneException;
import com.tarnvik.pbs.service.StaleReceiptException;
import com.tarnvik.pbs.service.UnknownItemException;
import com.tarnvik.pbs.service.UnknownPersonException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The service's refusals as statuses, each with the exception's Swedish message, which the page shows as it is
 * (ErrorMessageResponse, read by the frontend as `serverMessage`). One place for all controllers, since several
 * share them.
 */
@RestControllerAdvice
public class RestExceptionHandler {
  /** 502: the failure was Claude's, or on the way to it, not the request's. */
  @ExceptionHandler(ReceiptParseException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ErrorMessageResponse handleParseFailure(ReceiptParseException e) {
    return new ErrorMessageResponse(e.getMessage());
  }

  /** 409: the page holds an earlier receipt, or the state does not allow the change just now. */
  @ExceptionHandler({StaleReceiptException.class, SplitDoneException.class, NotAllAssignedException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorMessageResponse handleConflict(RuntimeException e) {
    return new ErrorMessageResponse(e.getMessage());
  }

  @ExceptionHandler(UnknownItemException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorMessageResponse handleUnknownItem(UnknownItemException e) {
    return new ErrorMessageResponse(e.getMessage());
  }

  @ExceptionHandler({UnknownPersonException.class, InvalidSettingsException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorMessageResponse handleBadRequest(RuntimeException e) {
    return new ErrorMessageResponse(e.getMessage());
  }
}
