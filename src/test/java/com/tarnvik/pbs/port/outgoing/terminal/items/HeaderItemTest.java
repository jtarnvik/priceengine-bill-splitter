package com.tarnvik.pbs.port.outgoing.terminal.items;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderItemTest {
  /** The blue line spans the terminal, whatever its width, and follows a resize. */
  @Test
  void headingLine_isExactlyAsWideAsTheTerminal() {
    assertThat(HeaderItem.headingLine(80)).hasSize(80).startsWith(HeaderItem.HEADING);
    assertThat(HeaderItem.headingLine(200)).hasSize(200).startsWith(HeaderItem.HEADING);
  }

  @Test
  void headingLine_narrowerThanTheHeading_isCut() {
    assertThat(HeaderItem.headingLine(10)).isEqualTo("PriceEngin");
    assertThat(HeaderItem.headingLine(0)).isEmpty();
  }
}
