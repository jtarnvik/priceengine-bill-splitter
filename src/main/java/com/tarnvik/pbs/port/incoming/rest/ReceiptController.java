package com.tarnvik.pbs.port.incoming.rest;

import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.port.incoming.rest.dto.ErrorMessageResponse;
import com.tarnvik.pbs.service.ReceiptParseException;
import com.tarnvik.pbs.service.ReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * The owner's receipt: upload a photo to have it parsed, or reset. Both change what every connected page shows. The
 * upload is the only endpoint that costs money per call; it is behind login, and SecurityConfigTest says so.
 */
@RestController
@RequestMapping("/api/protected/receipt")
@Tag(name = "receipt")
@RequiredArgsConstructor
public class ReceiptController {
  private final ReceiptService receiptService;

  /**
   * The browser downscales the photo and re-encodes it as JPEG before uploading, so anything else is refused here
   * rather than sent to Claude under the wrong media type. A parse takes perhaps 10–30 s and is answered when done.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Receipt uploadReceipt(@RequestParam("image") MultipartFile image) throws IOException {
    if (image.isEmpty() || !MediaType.IMAGE_JPEG_VALUE.equals(image.getContentType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image part must be a non-empty image/jpeg");
    }
    return receiptService.upload(image.getBytes());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetReceipt() {
    receiptService.reset();
  }

  /** 502: the failure was Claude's, or on the way to it, not the request's. */
  @ExceptionHandler(ReceiptParseException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ErrorMessageResponse handleParseFailure(ReceiptParseException e) {
    return new ErrorMessageResponse(e.getMessage());
  }
}
