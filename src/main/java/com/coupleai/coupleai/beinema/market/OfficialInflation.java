package com.coupleai.coupleai.beinema.market;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The official inflation rates of the Statistical Center of Iran (amar.org.ir), as its home page
 * shows them: consumer and producer inflation with the month or season they are for.
 *
 * Its server does not send its intermediate certificate (Certum DV TLS G2 R39 CA), which browsers
 * fetch on their own and Java does not. That certificate, as Certum publishes it, is in
 * classpath:certs and is added to the usual trusted authorities for this one client: the whole
 * chain and the host name are still verified; nothing is skipped.
 */
public final class OfficialInflation {

    static final String SOURCE = "https://amar.org.ir/";
    private static final String INTERMEDIATE = "certs/certum-dv-tls-g2-r39.pem";

    /** A rate in percent and the period it is for, as the Statistical Center writes it («مهر 1405»). */
    public record Rate(double percent, String period) {
    }

    public record Rates(Rate consumer, Rate producer) {
    }

    private static final Pattern CONSUMER = rate("نرخ تورم مصرف‌کننده");
    private static final Pattern PRODUCER = rate("نرخ تورم تولیدکننده");

    private final HttpClient http;

    OfficialInflation() throws Exception {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(sslContext())
                .build();
    }

    Rates read() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "Mozilla/5.0 (compatible; Beinema)")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("amar.org.ir answered " + response.statusCode());
        }
        return parse(response.body());
    }

    /** The two rates from the home page; null for one that is not there. */
    static Rates parse(String page) {
        String text = page.replaceAll("(?s)<script.*?</script>|<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");
        return new Rates(find(CONSUMER, text), find(PRODUCER, text));
    }

    private static Pattern rate(String label) {
        // «نرخ تورم مصرف‌کننده %76/8 مهر 1405»: the percent sign on either side, / or . or ٫ as the decimal mark
        return Pattern.compile(Pattern.quote(label) + "\\s*%?\\s*([0-9۰-۹]+(?:[/.٫][0-9۰-۹]+)?)\\s*%?\\s*"
                + "([\\p{IsArabic}\\u200c ]{2,20}?\\s*[0-9۰-۹]{4})");
    }

    private static Rate find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String number = latin(matcher.group(1)).replace('/', '.').replace('٫', '.');
        try {
            double percent = Double.parseDouble(number);
            return percent > 0 && percent < 1000 ? new Rate(percent, matcher.group(2).trim()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String latin(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            out.append(c >= '۰' && c <= '۹' ? (char) ('0' + (c - '۰')) : c);
        }
        return out.toString();
    }

    /** The JVM's trusted authorities plus Certum's intermediate. */
    private static SSLContext sslContext() throws Exception {
        TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaults.init((KeyStore) null);
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        int index = 0;
        for (var manager : defaults.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                for (X509Certificate authority : x509.getAcceptedIssuers()) {
                    store.setCertificateEntry("ca-" + index++, authority);
                }
            }
        }
        try (InputStream in = OfficialInflation.class.getClassLoader().getResourceAsStream(INTERMEDIATE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + INTERMEDIATE);
            }
            Certificate certum = CertificateFactory.getInstance("X.509").generateCertificate(in);
            store.setCertificateEntry("certum-dv-tls-g2-r39", certum);
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        return context;
    }
}
