package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * New accounts a day, so free first sessions are not collected with made-up accounts: a few from one
 * device (an id the browser keeps), more from one address (many people in Iran share one address on
 * mobile networks, so that limit only stops making accounts in bulk). Each new account keeps where it was
 * made (signup_ip, signup_device), and the last 24 hours are counted from there.
 */
@Component
public class SignupGuard {

    static final String DEVICE_HEADER = "X-Device-Id";
    private static final Pattern DEVICE = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    private final UserRepository users;
    private final int perDevice;
    private final int perAddress;

    public SignupGuard(UserRepository users,
                       @Value("${beinema.signup.per-device-daily:2}") int perDevice,
                       @Value("${beinema.signup.per-ip-daily:20}") int perAddress) {
        this.users = users;
        this.perDevice = perDevice;
        this.perAddress = perAddress;
    }

    /** Before making an account: refused when this device or address made too many in the last day. */
    public void requireRoom() {
        HttpServletRequest request = request();
        if (request == null) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String device = device(request);
        if (device != null && users.countByCreatedAtAfterAndSignupDevice(since, device) >= perDevice) {
            throw new IllegalArgumentException("امروز از این دستگاه چند حساب تازه ساخته شده؛ فردا دوباره امتحان کن، "
                    + "یا اگر حساب داری، با همان وارد شو.");
        }
        if (users.countByCreatedAtAfterAndSignupIp(since, AuthRateLimitFilter.clientAddress(request)) >= perAddress) {
            throw new IllegalArgumentException("از این اینترنت امروز حساب‌های زیادی ساخته شده؛ کمی بعد دوباره امتحان کن.");
        }
    }

    /** A new account keeps where it was made; true when something was written on it (save it then). */
    public boolean stamp(User user) {
        HttpServletRequest request = request();
        if (user == null || request == null) {
            return false;
        }
        if (user.getSignupIp() == null) {
            user.setSignupIp(AuthRateLimitFilter.clientAddress(request));
        }
        if (user.getSignupDevice() == null) {
            user.setSignupDevice(device(request));
        }
        return true;
    }

    static String device(HttpServletRequest request) {
        String value = request.getHeader(DEVICE_HEADER);
        return value != null && DEVICE.matcher(value.trim()).matches() ? value.trim() : null;
    }

    private static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
