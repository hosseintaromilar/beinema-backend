package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonOverridesTest {

    @AfterEach
    void noEdits() {
        LessonOverrides.replaceAll(Map.of());
    }

    private static ObjectNode original(String id) {
        return (ObjectNode) LessonOverrides.originals().stream()
                .filter(item -> item.id().equals(id)).findFirst().orElseThrow().node().deepCopy();
    }

    @Test
    void everyLibraryIsListed() {
        assertThat(LessonOverrides.originals()).extracting(LessonOverrides.Original::library)
                .contains(LessonOverrides.GENERAL, LessonOverrides.ELIKA, LessonOverrides.RAHA_BELIEF, LessonOverrides.RAHA_PRACTICE);
    }

    @Test
    void anEditTakesThePlaceOfTheFilesTextAtOnce() {
        ObjectNode edited = original("L-WDEP");
        edited.put("fa_title", "مسیر کار (ویرایش)");
        LessonOverrides.set("L-WDEP", edited);
        assertThat(Lessons.find("L-WDEP").orElseThrow().faTitle()).isEqualTo("مسیر کار (ویرایش)");

        LessonOverrides.set("L-WDEP", null);
        assertThat(Lessons.find("L-WDEP").orElseThrow().faTitle()).isNotEqualTo("مسیر کار (ویرایش)");
    }

    @Test
    void anEditThatBreaksALessonIsRefusedAndTheLibraryKeepsWorking() {
        String belief = LessonOverrides.originals().stream()
                .filter(item -> item.library().equals(LessonOverrides.RAHA_BELIEF)).findFirst().orElseThrow().id();
        ObjectNode broken = original(belief);
        ((ObjectNode) broken.path("check")).set("options", new ArrayNode(null));

        assertThatThrownBy(() -> LessonOverrides.set(belief, broken)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TeachKit.lesson(belief)).isPresent();
    }
}
