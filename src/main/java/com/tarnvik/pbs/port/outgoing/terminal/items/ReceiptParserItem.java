package com.tarnvik.pbs.port.outgoing.terminal.items;

import com.tarnvik.pbs.port.outgoing.terminal.DashboardItem;
import com.tarnvik.pbs.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Which parser reads the receipts, as a coloured block like publicbackend's ON/OFF: it decides whether an upload costs
 * money, so it should be seen at a glance. Read from the setting in memory, which costs nothing, so it is current on
 * every repaint; a save of the settings repaints the board.
 */
@Component
@Profile("production")
@Order(20)
@RequiredArgsConstructor
public class ReceiptParserItem extends DashboardItem {
  static final String CLAUDE = "claude";

  private final SettingsService settingsService;

  @Override
  public int rowCount() {
    return 2;
  }

  @Override
  public void redraw(Terminal terminal, int startRow, int maxRows, int maxCols) {
    String parser = settingsService.current().receiptParser();
    printAt(terminal, "Receipt parser:", startRow, 1, maxRows, maxCols);
    printAt(terminal, block(parser), startRow, VALUE_COLUMN, maxRows, maxCols,
      AttributedStyle.BLACK, CLAUDE.equals(parser) ? AttributedStyle.GREEN : AttributedStyle.YELLOW, true);
  }

  /** " CANNED " or " CLAUDE ": the name, padded like the ON/OFF blocks on the other boards. */
  static String block(String parser) {
    return " " + parser.toUpperCase(Locale.ROOT) + " ";
  }
}
