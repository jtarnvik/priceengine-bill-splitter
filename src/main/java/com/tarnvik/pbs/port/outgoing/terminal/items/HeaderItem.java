package com.tarnvik.pbs.port.outgoing.terminal.items;

import com.tarnvik.pbs.port.outgoing.terminal.DashboardItem;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Header: which build is running, as on publicbackend's and overlord's boards. The deployment is tag-driven, so this
 * is the quickest confirmation that a deploy moved to the version intended; the build time tells two builds of one
 * version apart.
 * <p>
 * The one difference from theirs, made in all three: the blue heading spans the terminal's full width, and follows
 * a resize — the board repaints with the new size, and the heading pads itself to it.
 */
@Component
@Profile("production")
@Order(10)
public class HeaderItem extends DashboardItem {
  static final String HEADING = "PriceEngine Bill Splitter";

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Stockholm"));
  private static final String UNKNOWN = "unknown";

  /** From the JVM rather than bean creation, so it is the process's real start. Formatted once. */
  private static final String STARTED_AT = TIMESTAMP_FORMAT.format(
    Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));

  /** Optional, as in publicbackend: a build without build-info shows "unknown" rather than no board. */
  private final ObjectProvider<BuildProperties> buildProperties;

  public HeaderItem(ObjectProvider<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Override
  public int rowCount() {
    // Heading, version, built, started, and a blank spacer row.
    return 5;
  }

  @Override
  public void redraw(Terminal terminal, int startRow, int maxRows, int maxCols) {
    BuildProperties build = buildProperties.getIfAvailable();

    printAt(terminal, headingLine(maxCols), startRow, 1, maxRows, maxCols,
      AttributedStyle.WHITE, AttributedStyle.BLUE, true);

    printAt(terminal, "Version:", startRow + 1, 1, maxRows, maxCols);
    printAt(terminal, build == null ? UNKNOWN : build.getVersion(), startRow + 1, VALUE_COLUMN, maxRows, maxCols);

    printAt(terminal, "Built:", startRow + 2, 1, maxRows, maxCols);
    printAt(terminal, build == null || build.getTime() == null ? UNKNOWN : TIMESTAMP_FORMAT.format(build.getTime()),
      startRow + 2, VALUE_COLUMN, maxRows, maxCols);

    printAt(terminal, "Started:", startRow + 3, 1, maxRows, maxCols);
    printAt(terminal, STARTED_AT, startRow + 3, VALUE_COLUMN, maxRows, maxCols);
  }

  /** The heading, exactly as wide as the terminal: padded with spaces, or cut on a terminal narrower than it. */
  static String headingLine(int width) {
    if (width <= HEADING.length()) {
      return HEADING.substring(0, Math.max(width, 0));
    }
    return HEADING + " ".repeat(width - HEADING.length());
  }
}
