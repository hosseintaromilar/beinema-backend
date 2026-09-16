package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;


    public UserResponse getCurrentUser(

            Authentication authentication

    ) {

        String email = authentication.getName();


        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new RuntimeException(

                                "کاربر پیدا نشد"

                        )

                );


        return UserResponse.from(user);

    }

    @Transactional
    public UserResponse uploadAvatar(Authentication authentication, MultipartFile file) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));

        String previous = user.getAvatarUrl();
        String storedPath = fileStorageService.storeAvatar(user.getId(), file);
        user.setAvatarUrl(storedPath);
        userRepository.save(user);
        fileStorageService.deleteIfManaged(previous);
        return UserResponse.from(user);
    }

}