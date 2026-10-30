package com.tarnvik.pbs.port.outgoing.terminal.items;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserItemTest {
  @Test
  void block_isTheParserInCapitalsPaddedLikeOnOff() {
    assertThat(ReceiptParserItem.block("canned")).isEqualTo(" CANNED ");
    assertThat(ReceiptParserItem.block("claude")).isEqualTo(" CLAUDE ");
  }
}
