package com.coupleai.coupleai.beinema.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyCardServiceTest {

    private static SafetyRule rule(String category, String audience, String pattern) {
        SafetyRule rule = new SafetyRule();
        rule.setCategory(category);
        rule.setAudience(audience);
        rule.setPattern(pattern);
        return rule;
    }

    private final List<SafetyRule> rules = List.of(
            rule(SafetyRule.SELF_HARM, SafetyRule.SELF, "میخوام بمیرم"),
            rule(SafetyRule.VIOLENCE, SafetyRule.OTHERS, "میکشمت"),
            rule(SafetyRule.CHILD_ABUSE, SafetyRule.SELF, "با کمربند میزنه"));

    @Test
    void spellingsAndHalfSpacesMatchTheSame() {
        assertEquals(List.of(new SafetyCardService.Match(SafetyRule.SELF_HARM, SafetyRule.SELF)),
                SafetyCardService.matches("دیگه واقعاً می‌خوام بمیرم", rules));
        assertEquals(List.of(new SafetyCardService.Match(SafetyRule.CHILD_ABUSE, SafetyRule.SELF)),
                SafetyCardService.matches("بابای بچه‌ها با كمربند مي زنه", rules));
    }

    @Test
    void aThreatGoesToTheOthers() {
        assertEquals(List.of(new SafetyCardService.Match(SafetyRule.VIOLENCE, SafetyRule.OTHERS)),
                SafetyCardService.matches("یه بار دیگه بگی می کشمت", rules));
    }

    @Test
    void anOrdinaryMessageMatchesNothing() {
        assertTrue(SafetyCardService.matches("امروز با هم شام خوردیم و خوب بود", rules).isEmpty());
    }

    @Test
    void theCardNeverNamesTheServiceOrCredit() {
        String card = SafetyCardService.CARD;
        assertTrue(card.contains("۱۲۳") && card.contains("۱۴۸۰") && card.contains("۱۱۰") && card.contains("۱۱۵"));
        assertTrue(!card.contains("متیس") && !card.contains("اعتبار"));
    }
}
