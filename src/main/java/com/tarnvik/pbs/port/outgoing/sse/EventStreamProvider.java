package com.tarnvik.pbs.port.outgoing.sse;

import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.port.incoming.rest.dto.ReceiptEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The Server-Sent Events registry: every open page holds one emitter here. The mechanics are overlord's
 * SseNotificationProvider's — long timeout, comment heartbeat, dead emitters dropped on a failed send — but each event
 * carries the whole state rather than a "something changed" signal (see Live updates in CLAUDE.md).
 *
 * <p>A new page gets the current state as its first event, so that a reconnect is indistinguishable from a first
 * connect: {@code receipt} always, {@code {"receipt": null}} when there is none.
 *
 * <p>The number of connected pages is logged at debug on every connect and disconnect; phase 2 broadcast it as a
 * dummy event, and phase 10's terminal dashboard may show it.
 */
@Slf4j
@Component
public class EventStreamProvider {
  // Long-lived stream; the browser's EventSource reconnects by itself whenever it is closed.
  private static final long EMITTER_TIMEOUT_MILLIS = Duration.ofHours(24).toMillis();
  private static final String RECEIPT_EVENT = "receipt";

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  /**
   * Registers a new page and sends it the current receipt. The returned emitter must be handed back from the
   * controller method, so that Spring keeps the response open. Anything sent before that is buffered and goes out
   * first.
   *
   * <p>Synchronised with broadcastReceipt, and the receipt read only once the emitter is registered, so that an
   * upload at the same moment cannot leave the new page with the receipt before it: either the read sees the new
   * receipt, or the broadcast of it comes after.
   */
  public synchronized SseEmitter connect(Supplier<Optional<Receipt>> currentReceipt) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
    emitter.onCompletion(() -> remove(emitter));
    emitter.onTimeout(() -> {
      emitter.complete();
      remove(emitter);
    });
    emitter.onError(e -> remove(emitter));
    emitters.add(emitter);
    log.debug("SSE client connected, {} active connection(s)", emitters.size());
    send(emitter, receiptEvent(currentReceipt.get().orElse(null)));
    return emitter;
  }

  /** The new receipt to every page, or {@code null} after a reset. */
  public synchronized void broadcastReceipt(Receipt receipt) {
    broadcast(receiptEvent(receipt));
  }

  /**
   * A comment line ({@code : ping}) that EventSource ignores. It keeps a quiet stream from looking idle to
   * Cloudflare, which drops a response after 100 seconds without a byte, and finds dead connections: a send to a
   * phone that has gone away fails, and the failure removes it.
   */
  public void sendHeartbeat() {
    if (emitters.isEmpty()) {
      return;
    }
    broadcast(SseEmitter.event().comment("ping"));
  }

  /**
   * Closes every stream as the application stops. ContextClosedEvent comes before the web server's graceful
   * shutdown, which would otherwise wait its full 30 seconds for these never-ending responses to finish.
   */
  @EventListener(ContextClosedEvent.class)
  public void closeAll() {
    for (SseEmitter emitter : emitters) {
      emitter.complete();
    }
    emitters.clear();
  }

  private void remove(SseEmitter emitter) {
    // The callbacks can fire more than once for one emitter (an error, then completion); only the first removal
    // is worth logging.
    if (emitters.remove(emitter)) {
      log.debug("SSE client gone, {} active connection(s)", emitters.size());
    }
  }

  private static SseEmitter.SseEventBuilder receiptEvent(Receipt receipt) {
    return SseEmitter.event().name(RECEIPT_EVENT).data(new ReceiptEvent(receipt), MediaType.APPLICATION_JSON);
  }

  private void broadcast(SseEmitter.SseEventBuilder event) {
    for (SseEmitter emitter : emitters) {
      send(emitter, event);
    }
  }

  private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (Exception e) {
      // Client gone (IOException) or emitter already completed; the error callback drops it.
      log.trace("Dropping dead SSE emitter: {}", e.getMessage());
      emitter.completeWithError(e);
    }
  }
}
