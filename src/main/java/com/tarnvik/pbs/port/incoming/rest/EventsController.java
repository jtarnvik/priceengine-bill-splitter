package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live stream the public page listens to. Public and read-only: it sends, and accepts nothing.
 */
@RestController
@RequiredArgsConstructor
public class EventsController {
  private final EventStreamProvider eventStreamProvider;

  @GetMapping(value = "/api/public/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events() {
    return eventStreamProvider.connect();
  }
}
