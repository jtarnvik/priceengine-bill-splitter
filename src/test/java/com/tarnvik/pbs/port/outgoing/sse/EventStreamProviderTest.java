package com.tarnvik.pbs.port.outgoing.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventStreamProviderTest {
  /**
   * A phone gone without closing: its send fails. The page is dropped and the broadcast goes on to the others, and
   * completeWithError is never called — Tomcat refuses it from this thread, which once broke off the whole broadcast.
   */
  @Test
  void sendHeartbeat_deadPage_isDroppedAndTheOthersStillGetIt() throws IOException {
    SseEmitter dead = mock(SseEmitter.class);
    SseEmitter alive = mock(SseEmitter.class);
    // The first send, the receipt on connect, goes through; then the phone is gone.
    doNothing().doThrow(new IOException("Broken pipe")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
    EventStreamProvider provider = spy(new EventStreamProvider());
    doReturn(dead, alive).when(provider).newEmitter();
    provider.connect(Optional::empty);
    provider.connect(Optional::empty);

    provider.sendHeartbeat();
    provider.sendHeartbeat();

    verify(dead, never()).completeWithError(any());
    // Connect and the first ping; the second ping no longer tries it.
    verify(dead, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    // Connect and both pings.
    verify(alive, times(3)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void closeAll_emitterAlreadyEnded_stillClosesTheOthers() {
    SseEmitter ended = mock(SseEmitter.class);
    SseEmitter open = mock(SseEmitter.class);
    doThrow(new IllegalStateException("already ended")).when(ended).complete();
    EventStreamProvider provider = spy(new EventStreamProvider());
    doReturn(ended, open).when(provider).newEmitter();
    provider.connect(Optional::empty);
    provider.connect(Optional::empty);

    provider.closeAll();

    verify(open).complete();
  }
}
