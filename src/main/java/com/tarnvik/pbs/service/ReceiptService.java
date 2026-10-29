package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptAdjustment;
import com.tarnvik.pbs.model.ReceiptItem;
import com.tarnvik.pbs.model.ReceiptState;
import com.tarnvik.pbs.model.Split;
import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one receipt there is and how far it has been split, in memory: empty at startup, replaced by every upload,
 * emptied by a reset or when it expires, changed by every tick and by "Klar". Every change is broadcast whole to
 * every connected page.
 *
 * <p>Changes are serialised: the methods that change the state are synchronized, change and broadcast together.
 * Only the owner writes, but two quick taps on different rows are two concurrent requests; unserialised, one could
 * overwrite the other's tick, or the older state be broadcast last. Readers need no lock: the state is immutable.
 */
@Slf4j
@Service
public class ReceiptService {
  // Swish's message: at most 50 characters from a-ö, A-Ö, 0-9, space and !?(),.-:; — an iPhone refuses a link with
  // anything else. Other letters lose their accents (é becomes e); å, ä and ö are Swish's own and keep theirs.
  private static final int SWISH_MESSAGE_MAX_LENGTH = 50;
  private static final String SWISH_OWN_LETTERS = "åäöÅÄÖ";
  private static final String NOT_IN_SWISH_MESSAGE = "[^a-zA-ZåäöÅÄÖ0-9 !?(),.:;\\-]";
  private static final String DASHES = "[\\u2010-\\u2015\\u2212]";

  private final ReceiptParser receiptParser;
  private final EventStreamProvider eventStreamProvider;
  private final List<String> participants;
  private final String payee;
  private final String swishNumber;
  private final Duration expiry;
  private final Clock clock;
  private final AtomicReference<ReceiptState> current = new AtomicReference<>();

  /**
   * @param swishNumber the owner's number, from the environment: never logged, and sent to the pages only while the
   *                    split is done
   */
  public ReceiptService(ReceiptParser receiptParser, EventStreamProvider eventStreamProvider,
                        @Value("${pbs.participants}") List<String> participants,
                        @Value("${pbs.payee}") String payee,
                        @Value("${pbs.swish-number}") String swishNumber,
                        @Value("${pbs.receipt-expiry}") Duration expiry,
                        Clock clock) {
    if (participants.isEmpty() || new HashSet<>(participants).size() != participants.size()) {
      // People are identified by first name, so two of the same could not be told apart.
      throw new IllegalStateException("pbs.participants must name at least one person, each once: " + participants);
    }
    if (!participants.contains(payee)) {
      throw new IllegalStateException("pbs.payee must be one of pbs.participants: " + payee);
    }
    if (swishNumber.isBlank()) {
      throw new IllegalStateException("pbs.swish-number (PBS_SWISH_NUMBER) must be set");
    }
    this.receiptParser = receiptParser;
    this.eventStreamProvider = eventStreamProvider;
    this.participants = List.copyOf(participants);
    this.payee = payee;
    this.swishNumber = swishNumber.trim();
    this.expiry = expiry;
    this.clock = clock;
  }

  public Optional<ReceiptState> currentState() {
    return Optional.ofNullable(current.get());
  }

  /**
   * Parses the photo, numbers the items, runs the sum check, and replaces the current receipt with the result,
   * every item unassigned. A failed parse leaves the current receipt as it was. The parse itself, 10–30 s with
   * Claude, runs outside the lock.
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
    Instant now = clock.instant();
    replace(new ReceiptState(receipt, SplitCalculator.calculate(receipt, Map.of(), participants), now, now));
    return receipt;
  }

  /** Empties the receipt for every phone, not only on the owner's screen. */
  public synchronized void reset() {
    log.info("Receipt reset");
    current.set(null);
    eventStreamProvider.broadcastReceipt(null);
  }

  /**
   * Sets the whole set of people for one item — never a toggle, so that a request repeated or arriving late
   * changes nothing it should not. An empty set makes the item unassigned again.
   *
   * @param receiptId the receipt the owner's page holds; the change is refused unless it is the current one
   * @throws StaleReceiptException when it is not, or there is no receipt
   * @throws UnknownItemException when the receipt has no such item
   * @throws UnknownPersonException when a name is not among the participants
   * @throws SplitDoneException when the split is marked done: the amounts must not change under a friend paying
   */
  public synchronized void setAssignees(int itemId, UUID receiptId, List<String> people) {
    ReceiptState state = currentFor(receiptId);
    if (state.split().done()) {
      throw new SplitDoneException();
    }
    if (state.receipt().items().stream().noneMatch(item -> item.id() == itemId)) {
      throw new UnknownItemException(itemId);
    }
    for (String person : people) {
      if (!participants.contains(person)) {
        throw new UnknownPersonException(person);
      }
    }
    Map<Integer, List<String>> assignees = new HashMap<>();
    for (Split.Item item : state.split().items()) {
      assignees.put(item.id(), item.people());
    }
    // In participant order and each once, however the request listed them.
    assignees.put(itemId, participants.stream().filter(people::contains).toList());
    Split split = SplitCalculator.calculate(state.receipt(), assignees, participants);
    change(state, split);
  }

  /**
   * Marks the split done, or not — a whole state, never a toggle. Done, the split carries the payment, the owner's
   * number included, and every page shows the Swish buttons; undone, the payment is gone from the next event.
   *
   * @throws StaleReceiptException when the receipt is not the current one, or there is none
   * @throws NotAllAssignedException when marking done with something still unassigned
   */
  public synchronized void setDone(UUID receiptId, boolean done) {
    ReceiptState state = currentFor(receiptId);
    if (done && !state.split().allAssigned()) {
      throw new NotAllAssignedException();
    }
    Split.Payment payment = null;
    if (done) {
      LocalDate uploadDate = state.uploadedAt().atZone(clock.getZone()).toLocalDate();
      payment = new Split.Payment(payee, swishNumber, swishMessage(state.receipt().restaurantName(), uploadDate));
    }
    log.info("Split marked {}", done ? "done" : "not done");
    change(state, state.split().withPayment(payment));
  }

  /**
   * Resets the receipt once it has gone {@code pbs.receipt-expiry} without a change, number and all. Without it, a
   * forgotten reset would leave the owner's number on a public page until the next restart, possibly for weeks.
   */
  public synchronized void expireIfIdle() {
    ReceiptState state = current.get();
    if (state != null && !clock.instant().isBefore(state.changedAt().plus(expiry))) {
      log.info("Receipt expired, {} after its last change", expiry);
      current.set(null);
      eventStreamProvider.broadcastReceipt(null);
    }
  }

  /**
   * "&lt;restaurant&gt; &lt;date&gt;" for the Swish payment, within Swish's characters, the name cut so that the date
   * always fits in Swish's 50: accents dropped except on å, ä and ö, dashes made plain, anything else a space.
   */
  static String swishMessage(String restaurantName, LocalDate date) {
    String dateText = date.toString();
    String name = restaurantName == null ? "" : toSwishCharacters(restaurantName);
    int room = SWISH_MESSAGE_MAX_LENGTH - dateText.length() - 1;
    if (name.length() > room) {
      name = name.substring(0, room).strip();
    }
    return name.isEmpty() ? dateText : name + " " + dateText;
  }

  private static String toSwishCharacters(String text) {
    StringBuilder plain = new StringBuilder();
    for (char character : text.toCharArray()) {
      if (SWISH_OWN_LETTERS.indexOf(character) >= 0) {
        plain.append(character);
      } else {
        plain.append(Normalizer.normalize(String.valueOf(character), Normalizer.Form.NFD).replaceAll("\\p{M}", ""));
      }
    }
    return plain.toString()
      .replaceAll(DASHES, "-")
      .replaceAll(NOT_IN_SWISH_MESSAGE, " ")
      .replaceAll("\\s+", " ")
      .strip();
  }

  /** The state, if it is that of the receipt the page holds. */
  private ReceiptState currentFor(UUID receiptId) {
    ReceiptState state = current.get();
    if (state == null || !state.receipt().receiptId().equals(receiptId)) {
      throw new StaleReceiptException();
    }
    return state;
  }

  private void change(ReceiptState state, Split split) {
    current.set(new ReceiptState(state.receipt(), split, state.uploadedAt(), clock.instant()));
    eventStreamProvider.broadcastSplit(split);
  }

  private synchronized void replace(ReceiptState state) {
    current.set(state);
    eventStreamProvider.broadcastReceipt(state.receipt());
    eventStreamProvider.broadcastSplit(state.split());
  }

  /**
   * Items are numbered 1, 2, 3 … in receipt order. The sum check compares the items plus adjustments with the
   * receipt's own total: a misread price is the error that matters in a bill splitter, and this catches it without
   * trusting the model.
   */
  private static Receipt toReceipt(ParsedReceipt parsed) {
    List<ReceiptItem> items = new ArrayList<>();
    for (ParsedReceipt.Item item : parsed.items()) {
      items.add(new ReceiptItem(items.size() + 1, item.name(), item.quantity(), item.unitPrice().orElse(null),
        item.price(), item.note().orElse(null)));
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
