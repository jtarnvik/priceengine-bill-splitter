package com.tarnvik.pbs.port.incoming.scheduled;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatJob {
  private final EventStreamProvider eventStreamProvider;

  // Every 20 s: well inside Cloudflare's 100 s idle limit. A closed tab is dropped within two heartbeats: the
  // first write to a connection the other end has closed usually succeeds, and only the next one fails.
  @Scheduled(fixedRate = 20_000)
  public void sendHeartbeat() {
    eventStreamProvider.sendHeartbeat();
  }
}
