package com.tarnvik.pbs.port.incoming.scheduled;

import com.tarnvik.pbs.port.outgoing.terminal.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The board's one clock, as on publicbackend's: an hourly repaint, beside the ones events trigger. */
@Component
@Profile("production")
@RequiredArgsConstructor
public class DashboardRefreshJob {
  private final DashboardService dashboardService;

  @Scheduled(cron = "0 0 * * * *")
  public void refreshDashboard() {
    dashboardService.requestRedraw(true);
  }
}
