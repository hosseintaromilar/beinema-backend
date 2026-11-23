package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/rooms/{roomId}/receipts/{attachmentId}")
public class ReceiptController {

    private final ReceiptService receipts;
    private final CurrentUserService users;

    @PostMapping("/preview")
    public ReceiptService.Preview preview(@PathVariable Long roomId, @PathVariable Long attachmentId) {
        return receipts.preview(roomId, attachmentId, users.getCurrentUser());
    }

    @PostMapping("/confirm")
    public BudgetService.ReceiptResult confirm(@PathVariable Long roomId, @PathVariable Long attachmentId,
                                              @RequestBody ReceiptService.Confirmation details) {
        return receipts.confirm(roomId, attachmentId, users.getCurrentUser(), details);
    }
}
