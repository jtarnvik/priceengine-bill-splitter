package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import com.tarnvik.pbs.service.ReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live stream the public page listens to. Public and read-only: it sends, and accepts nothing.
 *
 * <p>Tagged so that code generation can exclude it by tag: the stream is the hand-written endpoint in live-api.ts.
 */
@Slf4j
@RestController
@Tag(name = "events")
@RequiredArgsConstructor
public class EventsController {
  private final EventStreamProvider eventStreamProvider;
  private final ReceiptService receiptService;

  @GetMapping(value = "/api/public/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(@RequestHeader(value = "User-Agent", required = false) String userAgent) {
    log.info("Stream requested by {}", userAgent); // TEMPORARY, for the phase 5 phone test
    return eventStreamProvider.connect(receiptService::currentReceipt);
  }
}
