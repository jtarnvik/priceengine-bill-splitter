package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.incoming.rest.dto.MeResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is logged in, which the frontend asks to decide between "Logga in" and "Logga ut". Anonymous requests never
 * get here: the security config answers them with 401, which is the frontend's "not logged in".
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth")
public class AuthController {
  // The method name is the operationId, and so the generated hook's name (useGetMeQuery).
  @GetMapping("/me")
  public MeResponse getMe(@AuthenticationPrincipal OidcUser user) {
    return new MeResponse(user.getEmail());
  }
}
