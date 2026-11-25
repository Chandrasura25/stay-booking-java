package dev.roqeebah.staybooking.payment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentWebhookController {

    private final PaymentWebhookService service;

    public PaymentWebhookController(PaymentWebhookService service) {
        this.service = service;
    }

    /** Raw body on purpose: the signature is over the exact bytes the provider sent. */
    @PostMapping(path = "/webhooks/payments", consumes = "application/json")
    public ResponseEntity<Map<String, String>> receive(@RequestHeader(name = "Payment-Signature", required = false) String signature,
                                                       @RequestBody String rawBody) {
        PaymentWebhookService.Result result = service.handle(signature, rawBody);
        HttpStatus status = result == PaymentWebhookService.Result.BAD_SIGNATURE ? HttpStatus.UNAUTHORIZED : HttpStatus.OK;
        return ResponseEntity.status(status).body(Map.of("result", result.name()));
    }
}
