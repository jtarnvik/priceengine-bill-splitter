package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The owner's settings — the names, the payee, the parser — in memory: a restart brings back the properties. Changing
 * the names resets the receipt (NamesChangedEvent); the parser applies from the next upload.
 */
@Slf4j
@Service
public class SettingsService {
  // The owner's checkbox line and the totals grid are laid out for four.
  private static final int MIN_NAMES = 2;
  private static final int MAX_NAMES = 4;

  private final Map<String, ReceiptParser> parsers;
  private final ApplicationEventPublisher eventPublisher;
  private final AtomicReference<Settings> current = new AtomicReference<>();

  public SettingsService(List<ReceiptParser> parsers, ApplicationEventPublisher eventPublisher,
                         @Value("${pbs.participants}") List<String> names,
                         @Value("${pbs.payee}") String payee,
                         @Value("${pbs.receipt-parser}") String receiptParser) {
    this.parsers = parsers.stream().collect(Collectors.toMap(ReceiptParser::name, Function.identity()));
    this.eventPublisher = eventPublisher;
    try {
      current.set(validated(names, payee, receiptParser));
    } catch (InvalidSettingsException e) {
      throw new IllegalStateException("pbs.participants, pbs.payee or pbs.receipt-parser: " + e.getMessage(), e);
    }
  }

  public Settings current() {
    return current.get();
  }

  /** The parser the setting names, for the next upload. */
  public ReceiptParser receiptParser() {
    return parsers.get(current.get().receiptParser());
  }

  /**
   * Saves the settings as a whole. Each name is trimmed and empty ones dropped, so the dialog can send its field split
   * on commas as it is. A changed name list resets the receipt; the same names in the same order do not.
   *
   * @throws InvalidSettingsException with a Swedish message, when the names, the payee or the parser will not do
   */
  public synchronized Settings update(List<String> names, String payee, String receiptParser) {
    Settings settings = validated(names, payee, receiptParser);
    boolean namesChanged = !settings.names().equals(current.get().names());
    current.set(settings);
    log.info("Settings saved: {} names, parser {}{}", settings.names().size(), settings.receiptParser(),
      namesChanged ? ", names changed" : "");
    if (namesChanged) {
      eventPublisher.publishEvent(new NamesChangedEvent());
    }
    eventPublisher.publishEvent(new SettingsChangedEvent());
    return settings;
  }

  private Settings validated(List<String> names, String payee, String receiptParser) {
    List<String> trimmed = names.stream().map(String::strip).filter(name -> !name.isEmpty()).toList();
    if (trimmed.size() < MIN_NAMES || trimmed.size() > MAX_NAMES) {
      throw new InvalidSettingsException("Ange två till fyra namn, åtskilda med komma.");
    }
    Set<String> seen = new HashSet<>();
    for (String name : trimmed) {
      // People are identified by first name, so two of the same could not be told apart.
      if (!seen.add(name.toLowerCase(Locale.ROOT))) {
        throw new InvalidSettingsException(name + " finns med två gånger.");
      }
    }
    String trimmedPayee = payee == null ? "" : payee.strip();
    if (!trimmed.contains(trimmedPayee)) {
      throw new InvalidSettingsException("Den som får betalt måste vara ett av namnen.");
    }
    if (!parsers.containsKey(receiptParser)) {
      throw new InvalidSettingsException("Okänt sätt att läsa kvitton: " + receiptParser + ".");
    }
    return new Settings(trimmed, trimmedPayee, receiptParser);
  }
}
