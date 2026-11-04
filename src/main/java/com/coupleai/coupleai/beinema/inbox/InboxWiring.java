package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Service.ReferralService;
import com.coupleai.coupleai.beinema.Service.SessionBillingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ties purchases, renewals and referral gifts to the inbox, so billing knows neither. */
@Component
@RequiredArgsConstructor
public class InboxWiring {

    private final SessionBillingService billingService;
    private final ReferralService referralService;
    private final InboxService inboxService;
    private final com.coupleai.coupleai.beinema.mail.EmailAuthService emailAuthService;

    @PostConstruct
    void wire() {
        billingService.onPurchase((user, what) -> {
            inboxService.system(user.getId(), "پرداخت موفق بود", "خرید " + what + " انجام شد. جلسه‌هایت آماده است.",
                    "مشاهدهٔ بسته‌ها", "/dashboard/wallet");
            referralService.qualify(user);
        });
        billingService.onSessionSettled((user, title) -> referralService.qualify(user));
        billingService.onFreeUsed((user, ignored) -> emailAuthService.freeSessionUsed(user));
        billingService.onRenewalFailed((user, what) -> inboxService.system(user.getId(), "تمدید اشتراک انجام نشد",
                "اعتبار حسابت برای تمدید " + what + " کافی نبود و اشتراک متوقف شد. هر وقت خواستی، دوباره شروعش کن.",
                "اعتبار حساب", "/dashboard/wallet"));
        referralService.onGift(user -> inboxService.system(user.getId(), "یک جلسهٔ رایگان هدیه گرفتی",
                "یک جلسهٔ رایگان به حسابت اضافه شد. ممنون که بین‌ما را معرفی کردی.", null, null));
    }
}
