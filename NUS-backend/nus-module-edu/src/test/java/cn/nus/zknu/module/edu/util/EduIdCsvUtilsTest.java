package cn.nus.zknu.module.edu.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduIdCsvUtilsTest {

    @Test
    void blankInputGivesEmptySet() {
        assertTrue(EduIdCsvUtils.parseIds(null).isEmpty());
        assertTrue(EduIdCsvUtils.parseIds("").isEmpty());
        assertTrue(EduIdCsvUtils.parseIds("  , ,").isEmpty());
    }

    @Test
    void trimsWhitespaceAndKeepsOrder() {
        assertEquals(List.of(12L, 13L, 14L), List.copyOf(EduIdCsvUtils.parseIds(" 12, 13 ,\t14\n")));
    }

    @Test
    void acceptsFullWidthCommaAndSpace() {
        assertEquals(List.of(12L, 13L, 14L), List.copyOf(EduIdCsvUtils.parseIds("12，13,　14　")));
    }

    @Test
    void dropsDuplicatesEmptyAndInvalidTokens() {
        assertEquals(List.of(12L, 7L, 13L),
                List.copyOf(EduIdCsvUtils.parseIds("12,,abc,12,-3,0,007,1 3,13,+5,99999999999999999999")));
    }

    @Test
    void singleIdWithoutComma() {
        assertEquals(List.of(42L), List.copyOf(EduIdCsvUtils.parseIds("42")));
    }

}
