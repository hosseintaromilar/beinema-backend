package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.User.ChangePasswordRequest;
import com.coupleai.coupleai.beinema.DTO.User.CompleteNameRequest;
import com.coupleai.coupleai.beinema.DTO.User.UpdateProfileRequest;
import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Util.Usernames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;


    /**
     * The person edits their own profile: the name and the username. Leaving
     * the username empty keeps it as it is (an older account may have none).
     */
    @Transactional
    public UserResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = loadByAuthentication(authentication);
        String firstName = normalizeName(request == null ? null : request.getFirstName(), "نام");
        String lastName = normalizeName(request == null ? null : request.getLastName(), "نام خانوادگی");
        String typed = request == null ? null : request.getUsername();

        if (typed != null && !typed.isBlank()) {
            String username = Usernames.normalize(typed);
            if (!username.equals(user.getUsername())) {
                userRepository.findByUsername(username).ifPresent(other -> {
                    if (!other.getId().equals(user.getId())) {
                        throw new IllegalArgumentException("این نام کاربری قبلاً گرفته شده؛ یکی دیگر انتخاب کن.");
                    }
                });
                user.setUsername(username);
            }
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName(firstName + " " + lastName);
        userRepository.save(user);
        return UserResponse.from(user);
    }


    /**
     * Sets or changes the password. With a password of their own the current
     * one is asked; a Google account (or an old SMS one) sets its first
     * password without it, being signed in already. Then they can also sign in
     * with the username and this password.
     */
    @Transactional
    public UserResponse changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = loadByAuthentication(authentication);
        String next = AuthService.requirePassword(request == null ? null : request.getNewPassword());
        if (Boolean.TRUE.equals(user.getPasswordSet())) {
            String current = request == null || request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
            if (!passwordEncoder.matches(current, user.getPassword())) {
                throw new IllegalArgumentException("رمز فعلی درست نیست.");
            }
        }
        if (user.getUsername() == null && !user.hasPhoneNumber()) {
            throw new IllegalArgumentException("اول در پروفایل یک نام کاربری بگذار؛ ورود با رمز با همان نام کاربری است.");
        }
        user.setPassword(passwordEncoder.encode(next));
        user.setPasswordSet(true);
        userRepository.save(user);
        return UserResponse.from(user);
    }


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