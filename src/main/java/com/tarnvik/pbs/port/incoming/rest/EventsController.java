package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import com.tarnvik.pbs.service.ReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live stream the public page listens to. Public and read-only: it sends, and accepts nothing.
 *
 * <p>Tagged so that code generation can exclude it by tag: the stream is the hand-written endpoint in live-api.ts.
 */
@RestController
@Tag(name = "events")
@RequiredArgsConstructor
public class EventsController {
  private final EventStreamProvider eventStreamProvider;
  private final ReceiptService receiptService;

  @GetMapping(value = "/api/public/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events() {
    return eventStreamProvider.connect(receiptService::currentState);
  }
}
