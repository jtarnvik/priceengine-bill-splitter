package com.tarnvik.pbs.port.outgoing.rest.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.tarnvik.pbs.model.ParsedReceipt;
import com.tarnvik.pbs.port.outgoing.file.Recording;
import com.tarnvik.pbs.port.outgoing.file.RecordingProvider;
import com.tarnvik.pbs.service.ReceiptParseException;
import com.tarnvik.pbs.service.ReceiptParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Reads a receipt photo with Claude ({@code pbs.receipt-parser=claude}, the default), and records every parse. Adapted
 * from publicbackend's ClaudeProvider.
 *
 * <p>The answer is structured output typed as {@link ParsedReceipt}: the schema is generated from that record, and
 * the instructions about each field are its descriptions there. The prompt below holds what concerns the receipt as a
 * whole: which lines are items, which are adjustments, and which are left out.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pbs.receipt-parser", havingValue = "claude", matchIfMissing = true)
public class ClaudeReceiptParser implements ReceiptParser {
  // The most capable model, because a misread price is the error that matters, and at one receipt per dinner the
  // cost does not. Thinking cannot be disabled on it; effort is set explicitly rather than relying on its default.
  static final String MODEL = "claude-opus-5-5";
  private static final OutputConfig.Effort EFFORT = OutputConfig.Effort.MEDIUM;
  // Thinking counts towards maxTokens, so the cap leaves room for it as well as for a long receipt's JSON.
  private static final long MAX_TOKENS = 16_000;
  // Below Cloudflare's 100 s, so that a slow parse ends in an error message rather than a 524.
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private static final String PROMPT = """
    This is a photo of a receipt from a restaurant in Sweden. Read it into the requested structure. Four friends \
    will split the bill by its items, so every amount must be exactly what the receipt says.

    Amounts are in öre, as integers: 1 krona is 100 öre. Swedish receipts write a decimal comma and may group \
    thousands with a space, so "245,50" is 24550, "180" is 18000 and "1 234,00" is 123400.

    Which lines are what:
    - Every line with a price of its own is one item, in the receipt's order. A line with a quantity, such as \
    "3 x Öl 180,00" or "3 st Öl à 60,00 180,00", is one item with quantity 3 — never split it, and never merge \
    separate lines that happen to name the same thing.
    - A line with its own price is an item even when it adds to the dish above it ("Extra ost 15,00").
    - A line without a price belongs to the item above it, as that item's note ("utan lök", "medium").
    - Discounts ("rabatt") and "öresavrundning" are adjustments, not items.
    - Leave out everything that is not what is paid for: the VAT breakdown ("moms", "varav moms", "netto", \
    "brutto"), subtotals, payment lines (card, cash, change), the table, the staff, the date, the organisation \
    number and the like.

    Report what is printed, even where the items do not add up to the total: the sum is checked afterwards, and a \
    difference tells the owner to look closer. Where a character is hard to read, give your best reading.

    If the photo is not of a receipt, answer with an empty restaurant name, total 0 and no items.""";

  private final AnthropicClient client;
  private final RecordingProvider recordingProvider;

  public ClaudeReceiptParser(@Value("${pbs.anthropic-api-key}") String apiKey, RecordingProvider recordingProvider) {
    if (apiKey.isBlank()) {
      throw new IllegalStateException("pbs.anthropic-api-key (ANTHROPIC_API_KEY) must be set for "
        + "pbs.receipt-parser=claude");
    }
    this.client = AnthropicOkHttpClient.builder()
      .apiKey(apiKey)
      .timeout(TIMEOUT)
      // One try only. The SDK retries timeouts too, and a second 90 s attempt would run past Cloudflare's limit;
      // the owner taps the button again instead.
      .maxRetries(0)
      .build();
    this.recordingProvider = recordingProvider;
  }

  @Override
  public ParsedReceipt parse(byte[] jpeg) {
    long start = System.nanoTime();
    StructuredMessage<ParsedReceipt> response = send(jpeg);
    long durationMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
    long inputTokens = response.usage().inputTokens();
    long outputTokens = response.usage().outputTokens();
    log.info("Claude answered in {} ms, {} input tokens, {} output tokens, stop reason {}", durationMillis,
      inputTokens, outputTokens, response.stopReason().map(Object::toString).orElse("none"));

    verifyStopReason(response);
    ParsedReceipt receipt = answer(response);
    recordingProvider.save(jpeg, new Recording(receipt, MODEL, EFFORT.asString(), inputTokens, outputTokens,
      durationMillis));
    return receipt;
  }

  /** Package-private so that a test can check the request builds — the schema included — without calling the API. */
  static StructuredMessageCreateParams<ParsedReceipt> buildParams(byte[] jpeg) {
    ImageBlockParam image = ImageBlockParam.builder()
      .source(Base64ImageSource.builder()
        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
        .data(Base64.getEncoder().encodeToString(jpeg))
        .build())
      .build();
    return MessageCreateParams.builder()
      .model(MODEL)
      .maxTokens(MAX_TOKENS)
      // The image before the text, as the API recommends.
      .addUserMessageOfContentBlockParams(List.of(
        ContentBlockParam.ofImage(image),
        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())))
      .outputConfig(StructuredOutputConfig.<ParsedReceipt>builder()
        .format(ParsedReceipt.class)
        .effort(EFFORT)
        .build())
      .build();
  }

  private StructuredMessage<ParsedReceipt> send(byte[] jpeg) {
    try {
      return client.messages().create(buildParams(jpeg));
    } catch (AnthropicIoException e) {
      // A timeout or a network failure: no answer at all.
      log.warn("Claude call failed without an answer (timeout after {} s or network)", TIMEOUT.toSeconds(), e);
      throw new ReceiptParseException("Det tog för lång tid att läsa kvittot. Försök igen.", e);
    } catch (AnthropicServiceException e) {
      log.error("Claude answered HTTP {} ({})", e.statusCode(),
        e.errorType().map(Object::toString).orElse("no error type"), e);
      throw new ReceiptParseException("Kvittot kunde inte läsas just nu. Försök igen om en stund.", e);
    } catch (AnthropicException e) {
      log.error("Claude call failed", e);
      throw new ReceiptParseException("Kvittot kunde inte läsas just nu. Försök igen om en stund.", e);
    }
  }

  /**
   * Checked before the content is read. A refusal is unlikely for a receipt, but must end in a message rather than a
   * crash. No refusal fallback to another model: it needs the beta API, for a case that should not happen here.
   */
  private static void verifyStopReason(StructuredMessage<ParsedReceipt> response) {
    StopReason stopReason = response.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stopReason)) {
      String category = response.stopDetails()
        .flatMap(RefusalStopDetails::category)
        .map(Object::toString)
        .orElse("unknown");
      log.warn("Claude declined to read the receipt, category {}", category);
      throw new ReceiptParseException("Claude ville inte läsa bilden. Ta en ny bild av kvittot.");
    }
    if (StopReason.MAX_TOKENS.equals(stopReason)) {
      log.warn("Claude's answer was cut off at {} tokens", MAX_TOKENS);
      throw new ReceiptParseException("Kvittot kunde inte tolkas. Försök igen.");
    }
  }

  /** The first text block, not the first block: the answer may begin with thinking blocks. */
  private static ParsedReceipt answer(StructuredMessage<ParsedReceipt> response) {
    try {
      return response.content().stream()
        .flatMap(block -> block.text().stream())
        .findFirst()
        .orElseThrow(() -> new ReceiptParseException("Kvittot kunde inte tolkas. Försök igen."))
        .text();
    } catch (AnthropicInvalidDataException e) {
      log.error("Claude's answer did not fit ParsedReceipt", e);
      throw new ReceiptParseException("Kvittot kunde inte tolkas. Försök igen.", e);
    }
  }
}
