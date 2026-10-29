package com.tarnvik.pbs.port.incoming.scheduled;

import com.tarnvik.pbs.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceiptExpiryJob {
  private final ReceiptService receiptService;

  // Every minute, so a receipt goes within a minute of its expiry: 30 minutes after its last change, within 31.
  @Scheduled(fixedRate = 60_000)
  public void expireIdleReceipt() {
    receiptService.expireIfIdle();
  }
}
