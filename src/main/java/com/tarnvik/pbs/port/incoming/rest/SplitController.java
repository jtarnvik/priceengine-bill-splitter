package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.port.incoming.rest.dto.AssigneesRequest;
import com.tarnvik.pbs.service.ReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner ticking who had what. The answer is only a status: the new split reaches every page, the owner's
 * included, as the stream's {@code split} event.
 */
@RestController
@RequestMapping("/api/protected/items")
@Tag(name = "split")
@RequiredArgsConstructor
public class SplitController {
  private final ReceiptService receiptService;

  @PutMapping("/{id}/assignees")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setItemAssignees(@PathVariable int id, @Valid @RequestBody AssigneesRequest request) {
    receiptService.setAssignees(id, request.receiptId(), request.people());
  }
}
