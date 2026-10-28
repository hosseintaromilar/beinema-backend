package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.User.ChangePasswordRequest;
import com.coupleai.coupleai.beinema.DTO.User.CompleteNameRequest;
import com.coupleai.coupleai.beinema.DTO.User.UpdateProfileRequest;
import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {


    private final UserService userService;


    @GetMapping("/me")
    public UserResponse getCurrentUser(

            Authentication authentication

    ) {

        return userService.getCurrentUser(

                authentication

        );

    }

    @PutMapping("/me/name")
    public UserResponse completeName(
            Authentication authentication,
            @RequestBody CompleteNameRequest request
    ) {
        return userService.completeName(authentication, request);
    }

    /** Body: firstName, lastName, phoneNumber. */
    @PutMapping("/me")
    public UserResponse updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(authentication, request);
    }

    /** Body: currentPassword (when one is set), newPassword. */
    @PutMapping("/me/password")
    public UserResponse changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request
    ) {
        return userService.changePassword(authentication, request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return userService.uploadAvatar(authentication, file);
    }

}