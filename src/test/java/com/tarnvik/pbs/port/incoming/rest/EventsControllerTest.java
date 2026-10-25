package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Security is tested in SecurityConfigTest; here it is switched off, so the slice tests the controller alone.
@WebMvcTest(controllers = EventsController.class,
            excludeAutoConfiguration = {
              SecurityAutoConfiguration.class,
              SecurityFilterAutoConfiguration.class,
              ServletWebSecurityAutoConfiguration.class,
              OAuth2ClientAutoConfiguration.class,
              OAuth2ClientWebSecurityAutoConfiguration.class
            })
@Import(EventStreamProvider.class)
class EventsControllerTest {
  @Autowired MockMvc mockMvc;

  @Test
  void events_startsStreamWithCurrentStateAsFirstEvent() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/public/events"))
      .andExpect(status().isOk())
      .andExpect(request().asyncStarted())
      .andReturn();

    assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    assertThat(result.getResponse().getContentAsString()).startsWith("event:viewers\ndata:{\"viewers\":1}\n\n");
  }
}
