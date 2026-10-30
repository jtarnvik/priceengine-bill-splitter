package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.model.Settings;
import com.tarnvik.pbs.port.incoming.rest.dto.SettingsRequest;
import com.tarnvik.pbs.service.SettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's settings: the names, the payee, the parser. Behind login: the friends see the names only as they come
 * with the split.
 */
@RestController
@RequestMapping("/api/protected/settings")
@Tag(name = "settings")
@RequiredArgsConstructor
public class SettingsController {
  private final SettingsService settingsService;

  // The method names are the operationIds, and so the generated hooks' names.
  @GetMapping
  public Settings getSettings() {
    return settingsService.current();
  }

  /** Saved as a whole; changed names reset the receipt. Refusals are 400 with a Swedish message. */
  @PutMapping
  public Settings updateSettings(@Valid @RequestBody SettingsRequest request) {
    return settingsService.update(request.names(), request.payee(), request.receiptParser());
  }
}
