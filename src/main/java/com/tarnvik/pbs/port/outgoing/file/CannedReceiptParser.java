package com.tarnvik.pbs.port.outgoing.file;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.service.ReceiptParseException;
import com.tarnvik.pbs.service.ReceiptParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Answers with the recordings instead of calling Claude ({@code pbs.receipt-parser=canned}): the next one in name
 * order on every upload, starting over after the last, so that a reset and a new upload show a different receipt.
 * The answer is Claude's own JSON, so numbering, the sum check and the event run on it exactly as on a real parse.
 *
 * <p>Waits a moment before answering (pbs.canned-parser-delay, a second by default), so that the page's waiting
 * state can be seen.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pbs.receipt-parser", havingValue = "canned")
public class CannedReceiptParser implements ReceiptParser {
  private final RecordingProvider recordingProvider;
  private final Duration delay;
  private final AtomicInteger uploads = new AtomicInteger();

  public CannedReceiptParser(RecordingProvider recordingProvider,
                             @Value("${pbs.canned-parser-delay:1s}") Duration delay) {
    this.recordingProvider = recordingProvider;
    this.delay = delay;
  }

  @Override
  public ParsedReceipt parse(byte[] jpeg) {
    List<Path> files = recordingProvider.recordingFiles();
    if (files.isEmpty()) {
      throw new ReceiptParseException("Det finns inga inspelade kvitton att svara med (pbs.recording-dir).");
    }
    Path file = files.get(Math.floorMod(uploads.getAndIncrement(), files.size()));
    pause();
    log.info("Answering with the canned recording {}", file.getFileName());
    return recordingProvider.read(file).parsedReceipt();
  }

  private void pause() {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
