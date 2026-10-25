package com.tarnvik.pbs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Admits exactly one Google account: the owner's. Any other account is rejected here, during login, so no session is
 * ever created for it. publicbackend checks in its success handler instead, because it has pending users to record;
 * with a single fixed user there is nothing to record.
 *
 * <p>Google lets any account through to pbs (the consent screen's project is in production, not test mode), so this
 * check is the only gate.
 */
@Component
public class OwnerOnlyOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
  private final OidcUserService delegate = new OidcUserService();
  private final String allowedEmail;

  public OwnerOnlyOidcUserService(@Value("${pbs.allowed-email}") String allowedEmail) {
    if (allowedEmail.isBlank()) {
      // An empty PBS_ALLOWED_EMAIL would refuse everyone, which is safe but looks like a broken login.
      throw new IllegalStateException("pbs.allowed-email (PBS_ALLOWED_EMAIL) must be set");
    }
    this.allowedEmail = allowedEmail.trim();
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    return requireOwner(delegate.loadUser(userRequest));
  }

  /**
   * Returns the user unchanged if it is the owner, and throws otherwise. An unverified address is refused even when
   * it matches: it proves nothing about who owns it.
   */
  OidcUser requireOwner(OidcUser user) {
    boolean verified = Boolean.TRUE.equals(user.getEmailVerified());
    if (!verified || !allowedEmail.equalsIgnoreCase(user.getEmail())) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED,
        "Only the owner's Google account may log in", null));
    }
    return user;
  }
}
