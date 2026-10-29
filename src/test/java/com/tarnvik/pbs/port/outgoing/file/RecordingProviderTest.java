package com.tarnvik.pbs.port.outgoing.file;

import com.tarnvik.pbs.model.ParsedReceipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingProviderTest {
  @TempDir Path directory;

  @Test
  void save_writesJpegAndJsonThatReadBack() throws Exception {
    // A folder that does not exist yet, as on the first parse.
    RecordingProvider provider = new RecordingProvider(directory.resolve("recordings"), JsonMapper.builder().build());
    ParsedReceipt parsed = new ParsedReceipt("Trattoria", 18000, List.of(
      new ParsedReceipt.Item("Öl", 3, Optional.of(6000L), 18000, Optional.empty())), List.of());
    Recording recording = new Recording(parsed, "claude-opus-5-5", "medium", 4000L, 2000L, 15000L);

    provider.save(new byte[] {1, 2, 3}, recording);

    List<Path> files = provider.recordingFiles();
    assertThat(files).hasSize(1);
    assertThat(provider.read(files.getFirst())).isEqualTo(recording);
    Path jpeg = Path.of(files.getFirst().toString().replace(".json", ".jpg"));
    assertThat(Files.readAllBytes(jpeg)).containsExactly(1, 2, 3);
  }

  @Test
  void recordingFiles_missingFolder_isEmpty() {
    RecordingProvider provider = new RecordingProvider(directory.resolve("none"), JsonMapper.builder().build());

    assertThat(provider.recordingFiles()).isEmpty();
  }
}
