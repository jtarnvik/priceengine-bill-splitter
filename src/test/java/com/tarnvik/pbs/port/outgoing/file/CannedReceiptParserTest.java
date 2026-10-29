package com.tarnvik.pbs.port.outgoing.file;

import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.service.ReceiptParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CannedReceiptParserTest {
  private static final byte[] JPEG = {1, 2, 3};

  @TempDir Path directory;

  /** One recording per upload in name order, then from the start again, so a reset and an upload show a new one. */
  @Test
  void parse_answersWithRecordingsInNameOrderRoundAndRound() throws Exception {
    writeRecording("2026-01-02T20-00-00", "Andra");
    writeRecording("2026-01-01T19-00-00", "Första");
    // The JPEG beside a recording is not an answer of its own.
    Files.write(directory.resolve("2026-01-01T19-00-00.jpg"), JPEG);
    CannedReceiptParser parser = parser();

    assertThat(parser.parse(JPEG).restaurantName()).isEqualTo("Första");
    assertThat(parser.parse(JPEG).restaurantName()).isEqualTo("Andra");
    assertThat(parser.parse(JPEG).restaurantName()).isEqualTo("Första");
  }

  @Test
  void parse_readsNullsAsEmpty() throws Exception {
    writeRecording("2026-01-01T19-00-00", "Första");

    ParsedReceipt.Item item = parser().parse(JPEG).items().getFirst();

    assertThat(item.unitPrice()).isEmpty();
    assertThat(item.note()).contains("utan lök");
  }

  @Test
  void parse_noRecordings_fails() {
    assertThatThrownBy(() -> parser().parse(JPEG)).isInstanceOf(ReceiptParseException.class);
  }

  private CannedReceiptParser parser() {
    return new CannedReceiptParser(new RecordingProvider(directory, JsonMapper.builder().build()), Duration.ZERO);
  }

  private void writeRecording(String baseName, String restaurantName) throws Exception {
    Files.writeString(directory.resolve(baseName + ".json"), """
      {"parsedReceipt": {"restaurantName": "%s", "total": 1000, "adjustments": [],
        "items": [{"name": "Pizza", "quantity": 1, "unitPrice": null, "price": 1000, "note": "utan lök"}]}}
      """.formatted(restaurantName));
  }
}
