package com.tarnvik.pbs.port.outgoing.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * The recordings in {@code pbs.recording-dir}: for every real parse, the JPEG as uploaded and a JSON file of the
 * same name holding the {@link Recording}. Each parse costs money, and the recordings are what development runs on.
 *
 * <p>The folder is gitignored and its contents are never committed: the repository is public, and a folder of
 * receipts says where the owner eats.
 */
@Slf4j
@Component
public class RecordingProvider {
  // Sorts in the order the recordings were made, which is the order the canned parser answers with them.
  private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
  private static final String JSON_SUFFIX = ".json";

  private final Path directory;
  private final JsonMapper jsonMapper;

  public RecordingProvider(@Value("${pbs.recording-dir}") Path directory, JsonMapper jsonMapper) {
    this.directory = directory;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Stores a parse. A failure is logged rather than thrown: the parse has been paid for and its answer is good, so
   * the owner should get it even if it could not be kept.
   */
  public void save(byte[] jpeg, Recording recording) {
    String baseName = FILE_NAME_FORMAT.format(LocalDateTime.now());
    try {
      Files.createDirectories(directory);
      Files.write(directory.resolve(baseName + ".jpg"), jpeg);
      jsonMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(baseName + JSON_SUFFIX).toFile(),
        recording);
      log.info("Parse recorded as {}", directory.resolve(baseName));
    } catch (IOException | JacksonException e) {
      log.warn("Could not record the parse in {}", directory, e);
    }
  }

  /** The recordings' JSON files, in name order. Read anew on every call, so a recording copied in is used at once. */
  public List<Path> recordingFiles() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
        .filter(file -> file.getFileName().toString().endsWith(JSON_SUFFIX))
        .sorted()
        .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list the recordings in " + directory, e);
    }
  }

  public Recording read(Path file) {
    return jsonMapper.readValue(file.toFile(), Recording.class);
  }
}
