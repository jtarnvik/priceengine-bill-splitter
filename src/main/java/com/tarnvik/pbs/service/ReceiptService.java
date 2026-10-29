package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptAdjustment;
import com.tarnvik.pbs.model.ReceiptItem;
import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one receipt there is, in memory: empty at startup, replaced by every upload, emptied by a reset. Every change
 * is broadcast whole to every connected page.
 *
 * <p>The receipt is immutable, so it can be read and serialised for every listener without locking. Only the owner
 * writes, so there is no contention to design for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {
  private final ReceiptParser receiptParser;
  private final EventStreamProvider eventStreamProvider;
  private final AtomicReference<Receipt> current = new AtomicReference<>();

  public Optional<Receipt> currentReceipt() {
    return Optional.ofNullable(current.get());
  }

  /**
   * Parses the photo, numbers the items, runs the sum check, and replaces the current receipt with the result. A
   * failed parse leaves the current receipt as it was.
   */
  public Receipt upload(byte[] jpeg) {
    // Which parser answers, since `canned` and `claude` look alike from the page: only one costs money.
    log.info("Parsing an upload of {} bytes with {}", jpeg.length, receiptParser.getClass().getSimpleName());
    ParsedReceipt parsed = receiptParser.parse(jpeg);
    if (parsed.items() == null || parsed.items().isEmpty()) {
      // Most likely a photo of something other than a receipt; the prompt asks for no items then.
      throw new ReceiptParseException("Hittade inga rader på bilden. Ta en ny bild av kvittot.");
    }
    Receipt receipt = toReceipt(parsed);
    if (!receipt.totalMatches()) {
      log.info("Receipt from {} does not add up: the lines make {} öre, the receipt says {}",
        receipt.restaurantName(), receipt.linesTotal(), receipt.total());
    }
    current.set(receipt);
    eventStreamProvider.broadcastReceipt(receipt);
    return receipt;
  }

  /** Empties the receipt for every phone, not only on the owner's screen. */
  public void reset() {
    current.set(null);
    eventStreamProvider.broadcastReceipt(null);
  }

  /**
   * Items are numbered 1, 2, 3 … in receipt order. The sum check compares the items plus adjustments with the
   * receipt's own total: a misread price is the error that matters in a bill splitter, and this catches it without
   * trusting the model.
   */
  private static Receipt toReceipt(ParsedReceipt parsed) {
    List<ReceiptItem> items = new ArrayList<>();
    for (ParsedReceipt.Item item : parsed.items()) {
      items.add(new ReceiptItem(items.size() + 1, item.name(), item.quantity(), item.unitPrice().orElse(null), item.price(),
        item.note().orElse(null)));
    }
    List<ReceiptAdjustment> adjustments = new ArrayList<>();
    if (parsed.adjustments() != null) {
      for (ParsedReceipt.Adjustment adjustment : parsed.adjustments()) {
        adjustments.add(new ReceiptAdjustment(adjustment.name(), adjustment.amount()));
      }
    }
    long linesTotal = items.stream().mapToLong(ReceiptItem::price).sum()
      + adjustments.stream().mapToLong(ReceiptAdjustment::amount).sum();
    return new Receipt(UUID.randomUUID(), parsed.restaurantName(), parsed.total(), List.copyOf(items),
      List.copyOf(adjustments), linesTotal, linesTotal == parsed.total());
  }
}
