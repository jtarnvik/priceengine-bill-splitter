package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.incoming.rest.dto.SettingsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** How pbs is set up, for the owner's page. Behind login: the friends have no use for it. */
@RestController
@Tag(name = "settings")
public class SettingsController {
  private final String receiptParser;

  public SettingsController(@Value("${pbs.receipt-parser}") String receiptParser) {
    this.receiptParser = receiptParser;
  }

  // The method name is the operationId, and so the generated hook's name (useGetSettingsQuery).
  @GetMapping("/api/protected/settings")
  public SettingsResponse getSettings() {
    return new SettingsResponse(receiptParser);
  }
}
