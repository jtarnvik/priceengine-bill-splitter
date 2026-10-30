package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.model.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceTest {
  private static final List<String> NAMES = List.of("Jesper", "Anders", "Anna", "Kia");

  private final List<Object> published = new ArrayList<>();
  private final ReceiptParser canned = parser("canned");
  private final ReceiptParser claude = parser("claude");
  private final SettingsService service = new SettingsService(List.of(canned, claude), published::add, NAMES,
    "Jesper", "canned");

  @Test
  void current_startsFromTheProperties() {
    assertThat(service.current()).isEqualTo(new Settings(NAMES, "Jesper", "canned"));
    assertThat(service.receiptParser()).isSameAs(canned);
  }

  /** The dialog sends its field split on commas as it is: the server trims and drops what is left empty. */
  @Test
  void update_trimsNamesAndDropsEmptyOnes() {
    Settings saved = service.update(List.of("Jesper", " Anders ", "Anna", "  Kia", ""), "Jesper", "canned");

    assertThat(saved.names()).containsExactly("Jesper", "Anders", "Anna", "Kia");
  }

  @Test
  void update_switchesTheParserForTheNextUpload() {
    service.update(NAMES, "Jesper", "claude");

    assertThat(service.receiptParser()).isSameAs(claude);
  }

  /** The receipt's ticks name people who may no longer be at the table. */
  @Test
  void update_changedNames_announceTheChange() {
    service.update(List.of("Jesper", "Anders", "Anna", "Lisa"), "Jesper", "canned");

    assertThat(published).containsExactly(new NamesChangedEvent());
  }

  @Test
  void update_sameNamesOtherParser_announceNothing() {
    service.update(List.of(" Jesper", "Anders", "Anna", "Kia "), "Anna", "claude");

    assertThat(published).isEmpty();
  }

  @Test
  void update_tooFewOrTooMany_isRefused() {
    assertThatThrownBy(() -> service.update(List.of("Jesper"), "Jesper", "canned"))
      .isInstanceOf(InvalidSettingsException.class);
    assertThatThrownBy(() -> service.update(List.of("A", "B", "C", "D", "E"), "A", "canned"))
      .isInstanceOf(InvalidSettingsException.class);
  }

  /** People are identified by first name, so two of the same could not be told apart. */
  @Test
  void update_sameNameTwiceIgnoringCase_isRefused() {
    assertThatThrownBy(() -> service.update(List.of("Anna", "anna"), "Anna", "canned"))
      .isInstanceOf(InvalidSettingsException.class)
      .hasMessage("anna finns med två gånger.");
    assertThat(service.current().names()).isEqualTo(NAMES);
  }

  @Test
  void update_payeeNotAmongTheNames_isRefused() {
    assertThatThrownBy(() -> service.update(NAMES, "Bertil", "canned")).isInstanceOf(InvalidSettingsException.class);
  }

  @Test
  void update_unknownParser_isRefused() {
    assertThatThrownBy(() -> service.update(NAMES, "Jesper", "gpt")).isInstanceOf(InvalidSettingsException.class);
  }

  @Test
  void constructor_invalidProperties_fail() {
    assertThatThrownBy(() -> new SettingsService(List.of(canned), published::add, NAMES, "Bertil", "canned"))
      .isInstanceOf(IllegalStateException.class);
  }

  private static ReceiptParser parser(String name) {
    return new ReceiptParser() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public ParsedReceipt parse(byte[] jpeg) {
        return null;
      }
    };
  }
}
