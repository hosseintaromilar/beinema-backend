package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.SessionBillingService;
import com.coupleai.coupleai.beinema.Service.TopUpReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Sessions and paying for them: the person's side (/api/billing) and the
 * admins' side for the card-to-card receipts (/api/admin/top-ups, admins only).
 */
@RestController
@RequiredArgsConstructor
public class BillingController {

    private final SessionBillingService billingService;
    private final TopUpReceiptService receiptService;
    private final CurrentUserService currentUserService;

    /** Free sessions left, the session price, the balance and the card to pay to. */
    @GetMapping("/api/billing")
    public SessionBillingService.Offer offer() {
        return billingService.offerFor(currentUserService.getCurrentUser());
    }

    /** Public: the counselor pages show it before anyone signs in. */
    @GetMapping("/api/billing/prices")
    public SessionBillingService.Prices prices() {
        return billingService.prices();
    }

    @GetMapping("/api/billing/receipts")
    public List<TopUpReceiptService.ReceiptView> myReceipts() {
        return receiptService.mine();
    }

    /** Multipart: file (the receipt), amount (toman), trackingCode (optional). */
    @PostMapping("/api/billing/receipts")
    public TopUpReceiptService.ReceiptView send(
            @RequestParam("file") MultipartFile file,
            @RequestParam("amount") Long amount,
            @RequestParam(value = "trackingCode", required = false) String trackingCode
    ) {
        return receiptService.send(file, amount, trackingCode);
    }

    @GetMapping("/api/billing/receipts/{id}/file")
    public ResponseEntity<?> myReceiptFile(@PathVariable Long id) {
        return serve(receiptService.myFile(id));
    }

    @GetMapping("/api/admin/top-ups")
    public List<TopUpReceiptService.ReceiptView> forAdmin(@RequestParam(required = false) String status) {
        return receiptService.forAdmin(status);
    }

    @GetMapping("/api/admin/top-ups/{id}/file")
    public ResponseEntity<?> adminFile(@PathVariable Long id) {
        return serve(receiptService.adminFile(id));
    }

    /** Body: amount (optional, to correct what is charged), note (optional). */
    @PostMapping("/api/admin/top-ups/{id}/approve")
    public TopUpReceiptService.ReceiptView approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Object amount = body == null ? null : body.get("amount");
        Long value = amount == null || String.valueOf(amount).isBlank() ? null : Long.valueOf(String.valueOf(amount));
        return receiptService.approve(id, value, body == null ? null : (String) body.get("note"));
    }

    /** Body: note, the reason the person reads. */
    @PostMapping("/api/admin/top-ups/{id}/reject")
    public TopUpReceiptService.ReceiptView reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return receiptService.reject(id, body.get("note"));
    }

    private static ResponseEntity<?> serve(TopUpReceiptService.ReceiptFile file) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.resource());
    }
}
