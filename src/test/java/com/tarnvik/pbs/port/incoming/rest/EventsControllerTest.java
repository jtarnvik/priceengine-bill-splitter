package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptItem;
import com.tarnvik.pbs.model.ReceiptState;
import com.tarnvik.pbs.model.Split;
import com.tarnvik.pbs.port.outgoing.sse.EventStreamProvider;
import com.tarnvik.pbs.service.ReceiptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
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
  @MockitoBean ReceiptService receiptService;

  /** Even with no receipt: a phone locked through a reset would otherwise reconnect to silence and keep the old one. */
  @Test
  void events_noReceipt_sendsNullReceiptAndNothingElse() throws Exception {
    when(receiptService.currentState()).thenReturn(Optional.empty());

    MvcResult result = mockMvc.perform(get("/api/public/events"))
      .andExpect(status().isOk())
      .andExpect(request().asyncStarted())
      .andReturn();

    assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    // Phase 2's viewer count is gone: the receipt is the whole of the state.
    assertThat(result.getResponse().getContentAsString()).isEqualTo("event:receipt\ndata:{\"receipt\":null}\n\n");
  }

  /** The receipt first, then who had what: the page needs the rows before it can place the ticks. */
  @Test
  void events_withReceipt_startsWithItThenTheSplit() throws Exception {
    Receipt receipt = new Receipt(UUID.randomUUID(), "Trattoria", 18000,
      List.of(new ReceiptItem(1, "Öl", 3, 6000L, 18000, null)), List.of(), 18000, true);
    Split split = new Split(receipt.receiptId(), List.of(new Split.Item(1, List.of("Anna"))),
      List.of(new Split.Person("Anna", List.of(new Split.ItemShare(1, 18000)), 0, 18000)),
      new Split.Unassigned(0, 0), false, null);
    when(receiptService.currentState()).thenReturn(Optional.of(new ReceiptState(receipt, split, Instant.now(), Instant.now())));

    MvcResult result = mockMvc.perform(get("/api/public/events")).andReturn();

    String stream = result.getResponse().getContentAsString();
    assertThat(stream).startsWith(
      "event:receipt\ndata:{\"receipt\":{\"receiptId\":\"" + receipt.receiptId() + "\",\"restaurantName\":\"Trattoria\"");
    assertThat(stream).contains("\n\nevent:split\ndata:{\"receiptId\":\"" + receipt.receiptId()
      + "\",\"items\":[{\"id\":1,\"people\":[\"Anna\"]}]");
  }
}
