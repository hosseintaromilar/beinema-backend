package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.invitation.ReferralService;
import com.coupleai.coupleai.beinema.billing.SessionBillingService;
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
    private final com.coupleai.coupleai.beinema.mail.SmtpMailer mailer;

    @PostConstruct
    void wire() {
        billingService.onPurchase((user, what) -> {
            inboxService.system(user.getId(), "پرداخت موفق بود", "خرید " + what + " انجام شد. جلسه‌هایت آماده است.",
                    "مشاهدهٔ بسته‌ها", "/dashboard/wallet");
            referralService.qualify(user);
        });
        billingService.onSessionSettled((user, title) -> referralService.qualify(user));
        billingService.onFreeUsed((user, ignored) -> emailAuthService.freeSessionUsed(user));
        billingService.onBundleLeft((user, left) -> {
            String title = left == 1 ? "فقط یک جلسه از بسته‌ات مانده" : "جلسه‌های بسته‌ات تمام شد";
            String text = left == 1
                    ? "از جلسه‌های بسته‌ات فقط یکی مانده. اگر می‌خواهی گفتگوها بی‌وقفه ادامه پیدا کند، از بخش «اعتبار حساب» بستهٔ بعدی را انتخاب کن."
                    : "آخرین جلسهٔ بسته‌ات هم برگزار شد. برای جلسهٔ بعد، از بخش «اعتبار حساب» یک بسته یا تک جلسه انتخاب کن؛ گفتگوهایت و هر چه تا حالا گفته‌اید سر جایش می‌ماند.";
            inboxService.system(user.getId(), title, text, "اعتبار حساب", "/dashboard/wallet");
            String to = com.coupleai.coupleai.beinema.mail.AdminEmailService.address(user);
            if (to != null && mailer.configured()) {
                try {
                    mailer.send(to, title + " | بین‌ما", text + "\n\nاعتبار حساب: https://beinema.ir/dashboard/wallet");
                } catch (RuntimeException failure) {
                    // the inbox already has it
                }
            }
        });
        billingService.onRenewalFailed((user, what) -> inboxService.system(user.getId(), "تمدید اشتراک انجام نشد",
                "اعتبار حسابت برای تمدید " + what + " کافی نبود و اشتراک متوقف شد. هر وقت خواستی، دوباره شروعش کن.",
                "اعتبار حساب", "/dashboard/wallet"));
        billingService.onRefundRecorded((user, what) -> inboxService.system(user.getId(), "بسته‌ات لغو شد",
                what + " اگر سؤالی داری، از بخش پشتیبانی بپرس.", "کیف پول", "/dashboard/wallet"));
        referralService.onGift(user -> inboxService.system(user.getId(), "یک جلسهٔ رایگان هدیه گرفتی",
                "یک جلسهٔ رایگان به حسابت اضافه شد. ممنون که بین‌ما را معرفی کردی.", null, null));
    }
}
