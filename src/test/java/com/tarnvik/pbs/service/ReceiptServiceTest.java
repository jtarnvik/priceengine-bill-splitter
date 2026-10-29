package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReceiptServiceTest {
  private static final byte[] JPEG = {1, 2, 3};

  private final EventStreamProvider eventStreamProvider = mock(EventStreamProvider.class);

  @Test
  void upload_numbersItemsInReceiptOrder() {
    Receipt receipt = serviceAnswering(parsed(1500, List.of(), item("Öl", 3, 500L, 1500), item("Pizza", 1, null, 0)))
      .upload(JPEG);

    assertThat(receipt.items()).extracting("id", "name").containsExactly(
      tuple(1, "Öl"),
      tuple(2, "Pizza"));
    assertThat(receipt.items().getFirst().quantity()).isEqualTo(3);
    assertThat(receipt.items().getFirst().unitPrice()).isEqualTo(500L);
    assertThat(receipt.items().get(1).unitPrice()).isNull();
  }

  @Test
  void upload_itemsAddingUpToTotal_matches() {
    Receipt receipt = serviceAnswering(parsed(25000, List.of(), item("Pizza", 1, null, 14500),
      item("Pasta", 1, null, 10500))).upload(JPEG);

    assertThat(receipt.linesTotal()).isEqualTo(25000);
    assertThat(receipt.totalMatches()).isTrue();
  }

  @Test
  void upload_itemsNotAddingUpToTotal_doesNotMatch() {
    Receipt receipt = serviceAnswering(parsed(26000, List.of(), item("Pizza", 1, null, 14500),
      item("Pasta", 1, null, 10500))).upload(JPEG);

    assertThat(receipt.linesTotal()).isEqualTo(25000);
    assertThat(receipt.totalMatches()).isFalse();
  }

  /** A discount and öresavrundning are the usual reason the items alone do not make the total. */
  @Test
  void upload_adjustmentsCountTowardsTheSum() {
    List<ParsedReceipt.Adjustment> adjustments = List.of(new ParsedReceipt.Adjustment("Rabatt 10%", -3390),
      new ParsedReceipt.Adjustment("Öresavrundning", -10));
    Receipt receipt = serviceAnswering(parsed(30500, adjustments, item("Hamburgare", 1, null, 33900)))
      .upload(JPEG);

    assertThat(receipt.linesTotal()).isEqualTo(30500);
    assertThat(receipt.totalMatches()).isTrue();
    assertThat(receipt.adjustments()).extracting("name").containsExactly("Rabatt 10%", "Öresavrundning");
  }

  @Test
  void upload_replacesCurrentReceiptAndBroadcastsIt() {
    ReceiptService service = serviceAnswering(parsed(1000, List.of(), item("Kaffe", 1, null, 1000)));

    Receipt first = service.upload(JPEG);
    Receipt second = service.upload(JPEG);

    assertThat(second.receiptId()).isNotEqualTo(first.receiptId());
    assertThat(service.currentReceipt()).contains(second);
    verify(eventStreamProvider).broadcastReceipt(first);
    verify(eventStreamProvider).broadcastReceipt(second);
  }

  @Test
  void upload_noItems_failsAndKeepsCurrentReceipt() {
    ReceiptService service = serviceAnswering(parsed(0, List.of()));

    assertThatThrownBy(() -> service.upload(JPEG)).isInstanceOf(ReceiptParseException.class);
    assertThat(service.currentReceipt()).isEmpty();
    verify(eventStreamProvider, never()).broadcastReceipt(any());
  }

  @Test
  void reset_emptiesReceiptAndBroadcastsNull() {
    ReceiptService service = serviceAnswering(parsed(1000, List.of(), item("Kaffe", 1, null, 1000)));
    service.upload(JPEG);

    service.reset();

    assertThat(service.currentReceipt()).isEmpty();
    verify(eventStreamProvider).broadcastReceipt(null);
  }

  private ReceiptService serviceAnswering(ParsedReceipt parsed) {
    return new ReceiptService(jpeg -> parsed, eventStreamProvider);
  }

  private static ParsedReceipt parsed(long total, List<ParsedReceipt.Adjustment> adjustments,
                                      ParsedReceipt.Item... items) {
    return new ParsedReceipt("Restaurang", total, List.of(items), adjustments);
  }

  private static ParsedReceipt.Item item(String name, int quantity, Long unitPrice, long price) {
    return new ParsedReceipt.Item(name, quantity, Optional.ofNullable(unitPrice), price, Optional.empty());
  }
}
