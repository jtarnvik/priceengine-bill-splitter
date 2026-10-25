package com.tarnvik.pbs.port.incoming.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A placeholder behind login, so that the 401 rule for /api/protected/** guards something real before the paid
 * parse endpoint exists. Goes when phase 4 brings that endpoint, and its test with it.
 */
@RestController
@Tag(name = "protected")
public class ProtectedPingController {
  @GetMapping("/api/protected/ping")
  public String protectedPing() {
    return "ok";
  }
}
