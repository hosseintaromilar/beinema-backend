package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounselorStateAccessTest {

    private static User user(String phone, UserRole role) {
        return User.builder().name("x").email("x@users.beinema").password("x").phoneNumber(phone).role(role).build();
    }

    @Test
    void onlyTheListedNumbersSeeTheDecisionPath() {
        CounselingConfig config = new CounselingConfig();
        config.setStatePanelPhones("09914816794, 09379736278, 0937936278");
        CounselorStateService service = new CounselorStateService(null, null, null, null, null, null, config);

        assertTrue(service.canView(user("09914816794", UserRole.USER)));
        assertTrue(service.canView(user("+989379736278", UserRole.USER)), "the same number in another form");
        assertFalse(service.canView(user("09121234567", UserRole.ADMIN)), "being an admin is not enough");
        assertFalse(service.canView(user(null, UserRole.USER)));
        assertFalse(service.canView(null));

        config.setStatePanelPhones("");
        assertFalse(service.canView(user("09914816794", UserRole.USER)), "an empty list shows it to nobody");
    }
}
