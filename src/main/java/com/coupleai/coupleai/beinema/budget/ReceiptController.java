package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/rooms/{roomId}/receipts")
public class ReceiptController {

    private final ReceiptService receipts;
    private final CurrentUserService users;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(@PathVariable Long roomId, @RequestParam("file") MultipartFile file) {
        return receipts.upload(roomId, file, users.getCurrentUser());
    }

    @GetMapping("/pending")
    public List<AttachmentResponse> pending(@PathVariable Long roomId) {
        return receipts.pending(roomId, users.getCurrentUser());
    }

    @PostMapping("/{attachmentId}/preview")
    public ReceiptService.Preview preview(@PathVariable Long roomId, @PathVariable Long attachmentId) {
        return receipts.preview(roomId, attachmentId, users.getCurrentUser());
    }

    @PostMapping("/{attachmentId}/confirm")
    public BudgetService.ReceiptResult confirm(@PathVariable Long roomId, @PathVariable Long attachmentId,
                                              @RequestBody ReceiptService.Confirmation details) {
        return receipts.confirm(roomId, attachmentId, users.getCurrentUser(), details);
    }
}
