package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoGuardTest {

    @Test
    void handingTheirQuestionBackIsCaught() {
        String asked = "چرا همیشه این‌طوری دعوامون میشه؟ واقعاً چی کار کنیم؟";
        String reply = "حسین پرسید چرا همیشه این‌طوری دعوامون میشه و چی کار کنیم؛ یه مفهوم هست به اسم «دایرهٔ حل مسئله».";
        assertTrue(EchoGuard.echoed(reply, List.of(asked)).isPresent());
    }

    @Test
    void aShortEchoAfterSayingTheySaidItIsCaught() {
        String wrote = "من فقط می‌خوام شب‌ها آروم باشه";
        String reply = "حالا می‌خوام دقیق‌تر بدونم وقتی می‌گی شب‌ها آروم باشه، کدومش به اون چیزی که تو می‌خوای نزدیک‌تره.";
        assertEquals("شبها آروم باشه", EchoGuard.echoed(reply, List.of(wrote)).orElseThrow());
    }

    @Test
    void aLongRunOfTheirWordsIsCaughtEvenWithoutACue() {
        String wrote = "هر بار که دیر میاد خونه منو عصبانی می‌کنه";
        String reply = "هر بار که دیر میاد خونه، چیزی توی تو بالا می‌گیره. بیا ببینیم اون لحظه چی کار می‌کنی.";
        assertTrue(EchoGuard.echoed(reply, List.of(wrote)).isPresent());
    }

    @Test
    void anOrdinaryReplyAndTheCardOptionsPass() {
        String wrote = "دیشب دوباره سر اینکه دیر اومد خونه دعوامون شد";
        String reply = "خستگی بعد از یه روز کار واقعیه. [[QUESTION]] کدومش به خواسته‌ات نزدیک‌تره؟ "
                + "[[OPT|i1]] دیشب دوباره سر اینکه دیر اومد خونه دعوامون شد";
        assertTrue(EchoGuard.echoed(reply.substring(0, reply.indexOf("[[QUESTION]]")), List.of(wrote)).isEmpty());
        assertTrue(EchoGuard.echoed("ممنون که گفتی. اون شب، خودت چی کار کردی؟", List.of(wrote)).isEmpty(),
                "a short human sentence is not an echo");
    }
}
