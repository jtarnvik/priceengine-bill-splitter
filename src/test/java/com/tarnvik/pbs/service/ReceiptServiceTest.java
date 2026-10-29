package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptState;
import com.tarnvik.pbs.model.Split;
import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReceiptServiceTest {
  private static final byte[] JPEG = {1, 2, 3};
  private static final List<String> PARTICIPANTS = List.of("Jesper", "Anders", "Anna", "Kia");
  private static final String SWISH_NUMBER = "0700000000";
  private static final Duration EXPIRY = Duration.ofMinutes(30);

  private final EventStreamProvider eventStreamProvider = mock(EventStreamProvider.class);
  private final MovableClock clock = new MovableClock(Instant.parse("2026-10-29T18:00:00Z"));

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
  void upload_replacesCurrentReceiptAndBroadcastsItUnassigned() {
    ReceiptService service = serviceAnswering(parsed(1000, List.of(), item("Kaffe", 1, null, 1000)));

    Receipt first = service.upload(JPEG);
    Receipt second = service.upload(JPEG);

    assertThat(second.receiptId()).isNotEqualTo(first.receiptId());
    assertThat(currentReceipt(service)).isEqualTo(second);
    verify(eventStreamProvider).broadcastReceipt(first);
    verify(eventStreamProvider).broadcastReceipt(second);
    Split split = currentSplit(service);
    assertThat(split.receiptId()).isEqualTo(second.receiptId());
    assertThat(split.items()).extracting("people").containsOnly(List.of());
    verify(eventStreamProvider).broadcastSplit(split);
  }

  @Test
  void upload_noItems_failsAndKeepsCurrentReceipt() {
    ReceiptService service = serviceAnswering(parsed(0, List.of()));

    assertThatThrownBy(() -> service.upload(JPEG)).isInstanceOf(ReceiptParseException.class);
    assertThat(service.currentState()).isEmpty();
    verify(eventStreamProvider, never()).broadcastReceipt(any());
  }

  @Test
  void reset_emptiesReceiptAndBroadcastsNull() {
    ReceiptService service = serviceAnswering(parsed(1000, List.of(), item("Kaffe", 1, null, 1000)));
    service.upload(JPEG);

    service.reset();

    assertThat(service.currentState()).isEmpty();
    verify(eventStreamProvider).broadcastReceipt(null);
  }

  @Test
  void setAssignees_setsTheRowsPeopleInParticipantOrderAndBroadcasts() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = currentReceipt(service).receiptId();

    service.setAssignees(1, receiptId, List.of("Kia", "Anna", "Kia"));

    Split split = currentSplit(service);
    assertThat(split.items()).extracting("id", "people").containsExactly(
      tuple(1, List.of("Anna", "Kia")), tuple(2, List.of()));
    verify(eventStreamProvider).broadcastSplit(split);
  }

  /** The whole set each time, never a toggle: a second request replaces the first, an empty one clears the row. */
  @Test
  void setAssignees_replacesAndClearsTheSet() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = currentReceipt(service).receiptId();

    service.setAssignees(1, receiptId, List.of("Anna", "Kia"));
    service.setAssignees(1, receiptId, List.of("Jesper"));
    assertThat(currentSplit(service).items().getFirst().people()).containsExactly("Jesper");

    service.setAssignees(1, receiptId, List.of());
    assertThat(currentSplit(service).items().getFirst().people()).isEmpty();
  }

  @Test
  void setAssignees_otherRowsKeepTheirPeople() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = currentReceipt(service).receiptId();

    service.setAssignees(1, receiptId, List.of("Anna"));
    service.setAssignees(2, receiptId, List.of("Kia"));

    assertThat(currentSplit(service).items()).extracting("people").containsExactly(List.of("Anna"), List.of("Kia"));
  }

  @Test
  void setAssignees_earlierReceipt_isRefused() {
    ReceiptService service = serviceWithTwoItems();
    UUID earlier = currentReceipt(service).receiptId();
    service.upload(JPEG);

    assertThatThrownBy(() -> service.setAssignees(1, earlier, List.of("Anna")))
      .isInstanceOf(StaleReceiptException.class);
    assertThat(currentSplit(service).items().getFirst().people()).isEmpty();
  }

  @Test
  void setAssignees_noReceipt_isRefused() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = currentReceipt(service).receiptId();
    service.reset();

    assertThatThrownBy(() -> service.setAssignees(1, receiptId, List.of("Anna")))
      .isInstanceOf(StaleReceiptException.class);
  }

  @Test
  void setAssignees_unknownItem_isRefused() {
    ReceiptService service = serviceWithTwoItems();

    assertThatThrownBy(() -> service.setAssignees(3, currentReceipt(service).receiptId(), List.of("Anna")))
      .isInstanceOf(UnknownItemException.class);
  }

  @Test
  void setAssignees_unknownPerson_isRefused() {
    ReceiptService service = serviceWithTwoItems();

    assertThatThrownBy(() -> service.setAssignees(1, currentReceipt(service).receiptId(), List.of("Bertil")))
      .isInstanceOf(UnknownPersonException.class);
    assertThat(currentSplit(service).items().getFirst().people()).isEmpty();
  }

  @Test
  void upload_startsTheNewReceiptUnassigned() {
    ReceiptService service = serviceWithTwoItems();
    service.setAssignees(1, currentReceipt(service).receiptId(), List.of("Anna"));

    service.upload(JPEG);

    assertThat(currentSplit(service).items()).extracting("people").containsOnly(List.of());
  }

  /** People are identified by first name, so two of the same could not be told apart. */
  @Test
  void constructor_sameNameTwice_fails() {
    assertThatThrownBy(() -> service(List.of("Anna", "Anna"), "Anna", SWISH_NUMBER))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructor_payeeNotAmongParticipants_fails() {
    assertThatThrownBy(() -> service(PARTICIPANTS, "Bertil", SWISH_NUMBER)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructor_noSwishNumber_fails() {
    assertThatThrownBy(() -> service(PARTICIPANTS, "Jesper", " ")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void setDone_everythingAssigned_carriesThePaymentUntilUndone() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = assignEverything(service);

    service.setDone(receiptId, true);
    Split done = currentSplit(service);
    assertThat(done.done()).isTrue();
    assertThat(done.payment()).isEqualTo(new Split.Payment("Jesper", SWISH_NUMBER, "Restaurang 2026-10-29"));
    verify(eventStreamProvider).broadcastSplit(done);

    service.setDone(receiptId, false);
    Split undone = currentSplit(service);
    assertThat(undone.done()).isFalse();
    assertThat(undone.payment()).isNull();
    // Twice: undone, the split equals the one broadcast after the last tick, before "Klar".
    verify(eventStreamProvider, times(2)).broadcastSplit(undone);
  }

  @Test
  void setDone_somethingUnassigned_isRefused() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = currentReceipt(service).receiptId();
    service.setAssignees(1, receiptId, List.of("Anna"));

    assertThatThrownBy(() -> service.setDone(receiptId, true)).isInstanceOf(NotAllAssignedException.class);
    assertThat(currentSplit(service).payment()).isNull();
  }

  @Test
  void setDone_earlierReceipt_isRefused() {
    ReceiptService service = serviceWithTwoItems();
    UUID earlier = assignEverything(service);
    service.upload(JPEG);

    assertThatThrownBy(() -> service.setDone(earlier, true)).isInstanceOf(StaleReceiptException.class);
  }

  /** The amounts in the Swish buttons must not change under a friend who is paying. */
  @Test
  void setAssignees_whileDone_isRefused() {
    ReceiptService service = serviceWithTwoItems();
    UUID receiptId = assignEverything(service);
    service.setDone(receiptId, true);

    assertThatThrownBy(() -> service.setAssignees(1, receiptId, List.of("Kia")))
      .isInstanceOf(SplitDoneException.class);
  }

  @Test
  void upload_startsNotDone() {
    ReceiptService service = serviceWithTwoItems();
    service.setDone(assignEverything(service), true);

    service.upload(JPEG);

    assertThat(currentSplit(service).done()).isFalse();
    assertThat(currentSplit(service).payment()).isNull();
  }

  @Test
  void expireIfIdle_expiresThirtyMinutesAfterTheLastChange() {
    ReceiptService service = serviceWithTwoItems();

    clock.advance(Duration.ofMinutes(29));
    service.expireIfIdle();
    assertThat(service.currentState()).isPresent();

    clock.advance(Duration.ofMinutes(1));
    service.expireIfIdle();
    assertThat(service.currentState()).isEmpty();
    verify(eventStreamProvider).broadcastReceipt(null);
  }

  @Test
  void expireIfIdle_anyChangeRestartsTheTime() {
    ReceiptService service = serviceWithTwoItems();
    clock.advance(Duration.ofMinutes(20));
    service.setAssignees(1, currentReceipt(service).receiptId(), List.of("Anna"));

    clock.advance(Duration.ofMinutes(25));
    service.expireIfIdle();
    assertThat(service.currentState()).isPresent();

    clock.advance(Duration.ofMinutes(5));
    service.expireIfIdle();
    assertThat(service.currentState()).isEmpty();
  }

  @Test
  void swishMessage_dropsWhatSwishDoesNotTakeAndKeepsTheDate() {
    LocalDate date = LocalDate.of(2026, 10, 29);

    assertThat(ReceiptService.swishMessage("Kött & Fisk*  Åre", date)).isEqualTo("Kött Fisk Åre 2026-10-29");
    assertThat(ReceiptService.swishMessage("", date)).isEqualTo("2026-10-29");
    String longMessage = ReceiptService.swishMessage("A".repeat(60), date);
    assertThat(longMessage).hasSize(50).endsWith(" 2026-10-29");
  }

  private UUID assignEverything(ReceiptService service) {
    UUID receiptId = currentReceipt(service).receiptId();
    service.setAssignees(1, receiptId, List.of("Anna"));
    service.setAssignees(2, receiptId, List.of("Kia"));
    return receiptId;
  }

  private ReceiptService serviceWithTwoItems() {
    ReceiptService service = serviceAnswering(parsed(3000, List.of(), item("Pizza", 1, null, 1000),
      item("Pasta", 1, null, 2000)));
    service.upload(JPEG);
    return service;
  }

  private ReceiptService serviceAnswering(ParsedReceipt parsed) {
    return new ReceiptService(jpeg -> parsed, eventStreamProvider, PARTICIPANTS, "Jesper", SWISH_NUMBER, EXPIRY,
      clock);
  }

  private ReceiptService service(List<String> participants, String payee, String swishNumber) {
    return new ReceiptService(jpeg -> null, eventStreamProvider, participants, payee, swishNumber, EXPIRY, clock);
  }

  private static Receipt currentReceipt(ReceiptService service) {
    return service.currentState().map(ReceiptState::receipt).orElseThrow();
  }

  private static Split currentSplit(ReceiptService service) {
    return service.currentState().map(ReceiptState::split).orElseThrow();
  }

  private static ParsedReceipt parsed(long total, List<ParsedReceipt.Adjustment> adjustments,
                                      ParsedReceipt.Item... items) {
    return new ParsedReceipt("Restaurang", total, List.of(items), adjustments);
  }

  private static ParsedReceipt.Item item(String name, int quantity, Long unitPrice, long price) {
    return new ParsedReceipt.Item(name, quantity, Optional.ofNullable(unitPrice), price, Optional.empty());
  }

  /** A clock the test moves forward, for the expiry. In UTC, so the message's date does not depend on the machine. */
  private static final class MovableClock extends Clock {
    private Instant now;

    MovableClock(Instant start) {
      now = start;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
