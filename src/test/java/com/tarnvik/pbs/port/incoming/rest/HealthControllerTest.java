package com.tarnvik.pbs.port.incoming.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
class HealthControllerTest {
  @Autowired MockMvc mockMvc;

  @Test
  void ping_returnsOk() throws Exception {
    mockMvc.perform(get("/ping"))
      .andExpect(status().isOk())
      .andExpect(content().string("ok"));
  }
}
