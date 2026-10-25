package com.tarnvik.pbs.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerOnlyOidcUserServiceTest {
  private final OwnerOnlyOidcUserService service = new OwnerOnlyOidcUserService("owner@example.com");

  @Test
  void requireOwner_ownerWithVerifiedEmail_isAdmitted() {
    OidcUser owner = user("owner@example.com", true);

    assertThat(service.requireOwner(owner)).isSameAs(owner);
  }

  @Test
  void requireOwner_emailInOtherCase_isAdmitted() {
    OidcUser owner = user("Owner@Example.com", true);

    assertThat(service.requireOwner(owner)).isSameAs(owner);
  }

  @Test
  void requireOwner_otherEmail_isRefused() {
    assertThatThrownBy(() -> service.requireOwner(user("friend@example.com", true)))
      .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void requireOwner_ownerEmailNotVerified_isRefused() {
    assertThatThrownBy(() -> service.requireOwner(user("owner@example.com", false)))
      .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void constructor_blankAllowedEmail_fails() {
    assertThatThrownBy(() -> new OwnerOnlyOidcUserService(" "))
      .isInstanceOf(IllegalStateException.class);
  }

  private static OidcUser user(String email, boolean verified) {
    OidcIdToken idToken = OidcIdToken.withTokenValue("token")
      .subject("subject")
      .claim(StandardClaimNames.EMAIL, email)
      .claim(StandardClaimNames.EMAIL_VERIFIED, verified)
      .build();
    return new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken);
  }
}
