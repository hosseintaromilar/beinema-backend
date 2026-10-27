package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.User.CompleteNameRequest;
import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern NAME_PART = Pattern.compile(
            "^[\\p{IsAlphabetic}\\u200c][\\p{IsAlphabetic}\\u200c\\-\\s]{1,39}$"
    );

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;


    public UserResponse getCurrentUser(

            Authentication authentication

    ) {

        return UserResponse.from(loadByAuthentication(authentication));

    }

    @Transactional
    public UserResponse completeName(
            Authentication authentication,
            CompleteNameRequest request
    ) {
        User user = loadByAuthentication(authentication);

        if (!user.hasRegisteredName()) {
            String firstName = normalizeName(
                    request == null ? null : request.getFirstName(),
                    "نام"
            );
            String lastName = normalizeName(
                    request == null ? null : request.getLastName(),
                    "نام خانوادگی"
            );
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setName(firstName + " " + lastName);
        }

        // a Google account gives its number here; it is written down, not confirmed (no SMS for now)
        if (!user.hasPhoneNumber()) {
            String phone = PhoneNumbers.normalize(request == null ? null : request.getPhoneNumber());
            userRepository.findByPhoneNumber(phone).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("این شماره برای حساب دیگری ثبت شده است.");
                }
            });
            user.setPhoneNumber(phone);
            user.setPhoneVerified(false);
        }

        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse uploadAvatar(Authentication authentication, MultipartFile file) {
        User user = loadByAuthentication(authentication);

        String previous = user.getAvatarUrl();
        String storedPath = fileStorageService.storeAvatar(user.getId(), file);
        user.setAvatarUrl(storedPath);
        userRepository.save(user);
        fileStorageService.deleteIfManaged(previous);
        return UserResponse.from(user);
    }

    private User loadByAuthentication(Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new InvalidCredentialsException("برای ادامه باید وارد حساب شوید.");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));
    }

    private static String normalizeName(String raw, String label) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (!NAME_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    label + " را درست وارد کنید. فقط حرف مجاز است و حداقل دو حرف لازم است."
            );
        }
        return value;
    }

}