package com.coupleai.coupleai.beinema.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bank deposit rates and housing prices, with their source and the day they were checked
 * (classpath:kb/finance-reference.json, or the admin's newer version). No public service sends them
 * in a form a program can read, so they are kept by hand and always shown with their source and date.
 */
@Service
public class FinanceReferenceService {

    static final String FILE = "kb/finance-reference.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One deposit term and its rate, percent a year. */
    public record Rate(String term, int months, double percent) {
    }

    private final FinanceReferenceRepository repository;
    private volatile JsonNode cached;

    public FinanceReferenceService(FinanceReferenceRepository repository) {
        this.repository = repository;
    }

    /** The figures now: the admin's version, else the file's. */
    public JsonNode current() {
        JsonNode now = cached;
        if (now == null) {
            try {
                now = repository.findTopByOrderByIdDesc().map(row -> read(row.getDataJson())).orElseGet(FinanceReferenceService::file);
            } catch (RuntimeException unreadable) {
                now = file();
            }
            cached = now;
        }
        return now;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> view() {
        return JSON.convertValue(current(), Map.class);
    }

    public List<Rate> depositRates() {
        List<Rate> out = new ArrayList<>();
        current().path("deposit").path("rates").forEach(rate -> out.add(
                new Rate(rate.path("term").asText(""), rate.path("months").asInt(0), rate.path("percent").asDouble(0))));
        return out;
    }

    /** The one-year deposit rate, percent; null when it is not known. */
    public Double yearDeposit() {
        return depositRates().stream().filter(rate -> rate.months() == 12).map(Rate::percent).findFirst().orElse(null);
    }

    /** The admin's new version: checked to be whole before it is kept. */
    @Transactional
    public Map<String, Object> save(Map<String, Object> body, String admin) {
        JsonNode node = JSON.valueToTree(body);
        check(node);
        FinanceReference row = repository.findTopByOrderByIdDesc().orElseGet(FinanceReference::new);
        try {
            row.setDataJson(JSON.writeValueAsString(node));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        row.setUpdatedBy(admin);
        repository.save(row);
        cached = null;
        return view();
    }

    /** Back to the file's version. */
    @Transactional
    public Map<String, Object> reset() {
        repository.deleteAll();
        cached = null;
        return view();
    }

    static void check(JsonNode node) {
        JsonNode deposit = node.path("deposit");
        if (deposit.path("source").asText("").isBlank() || deposit.path("asOf").asText("").isBlank()) {
            throw new IllegalArgumentException("منبع و تاریخ نرخ سپرده لازم است.");
        }
        if (!deposit.path("rates").isArray() || deposit.path("rates").isEmpty()) {
            throw new IllegalArgumentException("دست‌کم یک نرخ سپرده لازم است.");
        }
        for (JsonNode rate : deposit.path("rates")) {
            double percent = rate.path("percent").asDouble(-1);
            if (rate.path("term").asText("").isBlank() || percent < 0 || percent > 100) {
                throw new IllegalArgumentException("هر نرخ سپرده یک عنوان و درصدی بین ۰ و ۱۰۰ می‌خواهد.");
            }
        }
        JsonNode housing = node.path("housing");
        if (housing.path("source").asText("").isBlank() || housing.path("asOf").asText("").isBlank()
                || housing.path("tehran").asText("").isBlank()) {
            throw new IllegalArgumentException("منبع، تاریخ و رقم تهران برای مسکن لازم است.");
        }
    }

    private static JsonNode read(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception broken) {
            throw new IllegalStateException(broken);
        }
    }

    static JsonNode file() {
        try (InputStream stream = FinanceReferenceService.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            return JSON.readTree(stream);
        } catch (Exception broken) {
            throw new IllegalStateException("Unreadable " + FILE, broken);
        }
    }
}
