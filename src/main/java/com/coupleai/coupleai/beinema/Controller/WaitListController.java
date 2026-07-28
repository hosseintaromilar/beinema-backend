package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.WaitList.WaitListRequest;
import com.coupleai.coupleai.beinema.DTO.WaitList.WaitListResponse;
import com.coupleai.coupleai.beinema.Service.WaitListService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/waitlist")
@AllArgsConstructor
public class WaitListController {

    private final WaitListService waitListService;

    @PostMapping
    public ResponseEntity<WaitListResponse> joinWaitlist(
            @Valid @RequestBody WaitListRequest request
    ) {

        WaitListResponse response = waitListService.join(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
