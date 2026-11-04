package com.coupleai.coupleai.beinema.mail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Email addresses as one person (docs: 03-email-login-spec-FA.md, section 4): Gmail ignores dots
 * and anything after +, most providers ignore +tags, Yahoo also -tags. The normalized form decides
 * whether an address is taken and whether its free session was already given.
 */
public final class EmailAddresses {

    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Set<String> DISPOSABLE = load();

    private EmailAddresses() {
    }

    /** Lower case and without spaces: the address mail is sent to. */
    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public static boolean valid(String raw) {
        String email = clean(raw);
        return email.length() <= 150 && SHAPE.matcher(email).matches();
    }

    public static String normalize(String raw) {
        String email = clean(raw);
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (domain.equals("googlemail.com")) {
            domain = "gmail.com";
        }
        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus);
        }
        if (domain.equals("gmail.com")) {
            local = local.replace(".", "");
        }
        if (domain.startsWith("yahoo.")) {
            int dash = local.indexOf('-');
            if (dash > 0) {
                local = local.substring(0, dash);
            }
        }
        return local + "@" + domain;
    }

    /** A throwaway mailbox (the list in resources/mail/disposable-domains.txt). */
    public static boolean disposable(String raw) {
        String email = clean(raw);
        String domain = email.substring(email.lastIndexOf('@') + 1);
        for (String candidate = domain; candidate.contains("."); candidate = candidate.substring(candidate.indexOf('.') + 1)) {
            if (DISPOSABLE.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> load() {
        Set<String> domains = new HashSet<>();
        try (InputStream in = EmailAddresses.class.getResourceAsStream("/mail/disposable-domains.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    String domain = line.trim().toLowerCase(Locale.ROOT);
                    if (!domain.isEmpty() && !domain.startsWith("#")) {
                        domains.add(domain);
                    }
                }
            }
        } catch (java.io.IOException ignored) {
            // without the list nothing is blocked
        }
        return domains;
    }
}
