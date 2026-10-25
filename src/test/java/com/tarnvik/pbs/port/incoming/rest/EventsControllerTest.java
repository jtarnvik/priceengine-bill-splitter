package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventsController.class)
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
