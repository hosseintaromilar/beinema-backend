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

    /** The pricing cards: every plan, its steps and their real counters. Public; nothing personal. */
    @GetMapping("/api/pricing")
    public List<SessionBillingService.PlanView> pricing() {
        // signed in: the prices this person would pay; otherwise the general ones
        com.coupleai.coupleai.beinema.Entity.User buyer = null;
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                buyer = currentUserService.getCurrentUser();
            }
        } catch (RuntimeException notSignedIn) {
            buyer = null;
        }
        return billingService.pricing(buyer);
    }

    /**
     * Body: tier (TRIAL, MAIN, DIAMOND or MONTHLY) and expectedPrice (the price on the card).
     * The server decides the price; when it is not the expected one, 409 with the new price.
     */
    @PostMapping("/api/billing/packages")
    public SessionBillingService.OwnedPackage buyPackage(@RequestBody Map<String, Object> body) {
        Object expected = body == null ? null : body.get("expectedPrice");
        Long expectedPrice = expected instanceof Number number ? number.longValue() : null;
        return billingService.buyPackage(currentUserService.getCurrentUser(),
                body == null ? null : String.valueOf(body.get("tier")), expectedPrice);
    }

    /** Cancels a bundle (refund recorded for the admin) or stops a subscription renewing. */
    @PostMapping("/api/billing/packages/{id}/cancel")
    public SessionBillingService.OwnedPackage cancel(@PathVariable Long id) {
        return billingService.cancel(currentUserService.getCurrentUser(), id);
    }

    /** The admin cancels a bundle after a refund ticket; the refund is recorded to be paid back. */
    @PostMapping("/api/admin/packages/{id}/cancel")
    public SessionBillingService.OwnedPackage cancelForRefund(@PathVariable Long id,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return billingService.cancelForRefund(id);
        }
        Object amount = body.get("amount");
        Long value = amount == null || String.valueOf(amount).isBlank() ? null : Long.valueOf(String.valueOf(amount).replaceAll("[^0-9]", ""));
        return billingService.cancelForRefund(id, value, (String) body.get("method"), (String) body.get("note"));
    }

    @GetMapping("/api/admin/refunds")
    public List<Map<String, Object>> refunds() {
        return billingService.pendingRefunds();
    }

    @PostMapping("/api/admin/refunds/{id}/paid")
    public void refundPaid(@PathVariable Long id) {
        billingService.refundPaid(id);
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
