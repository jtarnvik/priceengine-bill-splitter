package com.tarnvik.pbs.port.outgoing.rest.claude;

import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.tarnvik.pbs.model.ParsedReceipt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No test calls the real API. This one builds the request, which is where the SDK generates the JSON schema from
 * ParsedReceipt and checks locally that structured output supports it, so a change to the record that the API would
 * reject fails here rather than at the table.
 */
class ClaudeReceiptParserTest {
  @Test
  void buildParams_generatesSchemaFromParsedReceipt() {
    StructuredMessageCreateParams<ParsedReceipt> params = ClaudeReceiptParser.buildParams(new byte[] {1, 2, 3});

    String body = params.rawParams()._body().toString();
    assertThat(body).contains("model=claude-opus-5-5", "effort=medium", "mediaType=image/jpeg");
    // Optional is what makes these nullable; a plain Long would be a required integer the model has to make up.
    assertThat(body).contains("unitPrice={type=[integer, null]", "note={type=[string, null]");
  }
}
