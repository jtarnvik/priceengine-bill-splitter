package com.tarnvik.pbs.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security rules, against the real filter chain. A logged-in owner is simulated with oidcLogin(), whose principal
 * is an OidcUser like the one Google login produces; that is what AuthController reads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {
  private static final String OWNER_EMAIL = "owner@example.com";

  @Autowired MockMvc mockMvc;

  /**
   * The parse endpoint is the only one on the internet that costs money per call. Cheap insurance against a security
   * matcher reordered later.
   */
  @Test
  void receiptUpload_anonymous_gets401NotRedirect() throws Exception {
    mockMvc.perform(multipart("/api/protected/receipt").file(jpeg()).accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void assignees_anonymous_gets401() throws Exception {
    mockMvc.perform(put("/api/protected/items/1/assignees").contentType(MediaType.APPLICATION_JSON)
        .content("{\"receiptId\": \"" + UUID.randomUUID() + "\", \"people\": [\"Anna\"]}"))
      .andExpect(status().isUnauthorized());
  }

  /** Through to the service: the current receipt's id is accepted, anything else is refused with its own status. */
  @Test
  void assignees_owner_getThroughAndAreChecked() throws Exception {
    MvcResult upload = mockMvc.perform(multipart("/api/protected/receipt").file(jpeg()).with(ownerLogin()))
      .andExpect(status().isOk())
      .andReturn();
    String receiptId = JsonPath.read(upload.getResponse().getContentAsString(), "$.receiptId");

    mockMvc.perform(assignees(1, receiptId, "[\"Anna\", \"Kia\"]")).andExpect(status().isNoContent());
    mockMvc.perform(assignees(1, UUID.randomUUID().toString(), "[\"Anna\"]")).andExpect(status().isConflict());
    mockMvc.perform(assignees(999, receiptId, "[\"Anna\"]")).andExpect(status().isNotFound());
    mockMvc.perform(assignees(1, receiptId, "[\"Bertil\"]"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("Bertil är inte med och delar."));
    // Bean Validation: the receipt id is required.
    mockMvc.perform(put("/api/protected/items/1/assignees").with(ownerLogin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"people\": []}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void receiptReset_anonymous_gets401() throws Exception {
    mockMvc.perform(delete("/api/protected/receipt"))
      .andExpect(status().isUnauthorized());
  }

  /**
   * Every visitor's page calls /api/auth/me, so a session per 401 would hold one in memory for each friend and bot.
   * Asserted on the session rather than a Set-Cookie header: MockMvc does not write the JSESSIONID cookie.
   */
  @Test
  void protectedEndpoint_anonymous_createsNoSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized())
      .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  /** Through to the canned parser and the hand-written receipts in src/test/resources/recordings. */
  @Test
  void receiptUploadAndReset_owner_getThrough() throws Exception {
    mockMvc.perform(multipart("/api/protected/receipt").file(jpeg()).with(ownerLogin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id").value(1))
      .andExpect(jsonPath("$.items[1].id").value(2));
    mockMvc.perform(delete("/api/protected/receipt").with(ownerLogin()))
      .andExpect(status().isNoContent());
  }

  @Test
  void receiptUpload_ownerWithNonJpeg_gets400() throws Exception {
    MockMultipartFile png = new MockMultipartFile("image", "receipt.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1});
    mockMvc.perform(multipart("/api/protected/receipt").file(png).with(ownerLogin()))
      .andExpect(status().isBadRequest());
  }

  @Test
  void me_anonymous_gets401() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void me_owner_getsEmail() throws Exception {
    mockMvc.perform(get("/api/auth/me").with(ownerLogin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(OWNER_EMAIL));
  }

  /** Even for a browser navigation, which anywhere outside /api/** would be redirected to Google. */
  @Test
  void unknownApiPath_anonymousBrowser_gets401NotRedirect() throws Exception {
    mockMvc.perform(get("/api/anything").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void publicEndpoints_anonymous_areOpen() throws Exception {
    mockMvc.perform(get("/ping"))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/public/events"))
      .andExpect(status().isOk());
  }

  /**
   * Whether the files exist depends on whether the frontend was built (not with -Dskip.npm), so the test asserts
   * only what security decides: no redirect to Google.
   */
  @Test
  void staticFiles_anonymous_areNotRedirected() throws Exception {
    for (String path : new String[] {"/", "/index.html", "/assets/index.js", "/icon.svg"}) {
      MvcResult result = mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
      assertThat(result.getResponse().getStatus()).as(path).isIn(200, 404);
    }
  }

  @Test
  void favicon_anonymous_gets404() throws Exception {
    mockMvc.perform(get("/favicon.ico").accept(MediaType.TEXT_HTML))
      .andExpect(status().isNotFound());
  }

  /** A browser navigation (Accept: text/html): oauth2Login redirects only those, and answers anything else 401. */
  @Test
  void otherPath_anonymousBrowser_isRedirectedToGoogleLogin() throws Exception {
    mockMvc.perform(get("/somewhere").accept(MediaType.TEXT_HTML))
      .andExpect(status().isFound())
      .andExpect(header().string("Location", "/oauth2/authorization/google"));
  }

  @Test
  void logout_owner_getsOkNotRedirect() throws Exception {
    mockMvc.perform(post("/logout").with(ownerLogin()))
      .andExpect(status().isOk())
      .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void openApiSpec_isOffOutsideLocal() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
      .andExpect(status().isNotFound());
  }

  private static MockHttpServletRequestBuilder assignees(int itemId, String receiptId, String people) {
    return put("/api/protected/items/" + itemId + "/assignees").with(ownerLogin())
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"receiptId\": \"" + receiptId + "\", \"people\": " + people + "}");
  }

  private static MockMultipartFile jpeg() {
    return new MockMultipartFile("image", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  private static RequestPostProcessor ownerLogin() {
    return oidcLogin().idToken(token -> token.claim(StandardClaimNames.EMAIL, OWNER_EMAIL));
  }
}
