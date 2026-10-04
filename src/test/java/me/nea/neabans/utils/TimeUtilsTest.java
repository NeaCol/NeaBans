package me.nea.neabans.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para {@link TimeUtils}.
 *
 * <p>TimeUtils es puro Java (sin dependencias de Bukkit), por lo que todos
 * los tests corren sin necesidad de un servidor Minecraft.</p>
 */
@DisplayName("TimeUtils")
class TimeUtilsTest {

    // =========================================================================
    // parse()
    // =========================================================================

    @Test
    @DisplayName("parse('30s') debe devolver 30_000 ms")
    void testParseSeconds() {
        assertEquals(30_000L, TimeUtils.parse("30s"));
    }

    @Test
    @DisplayName("parse('5m') debe devolver 300_000 ms")
    void testParseMinutes() {
        assertEquals(300_000L, TimeUtils.parse("5m"));
    }

    @Test
    @DisplayName("parse('2h') debe devolver 7_200_000 ms")
    void testParseHours() {
        assertEquals(7_200_000L, TimeUtils.parse("2h"));
    }

    @Test
    @DisplayName("parse('7d') debe devolver 604_800_000 ms")
    void testParseDays() {
        assertEquals(604_800_000L, TimeUtils.parse("7d"));
    }

    @Test
    @DisplayName("parse('2w') debe devolver 1_209_600_000 ms")
    void testParseWeeks() {
        assertEquals(1_209_600_000L, TimeUtils.parse("2w"));
    }

    @Test
    @DisplayName("parse('1mo') debe devolver 2_592_000_000 ms (30 días)")
    void testParseMonths() {
        // 30 dias * 24h * 60min * 60s * 1000ms
        assertEquals(2_592_000_000L, TimeUtils.parse("1mo"));
    }

    @Test
    @DisplayName("parse('1d12h') debe devolver la suma de 1 día + 12 horas")
    void testParseCombined() {
        long expected = 86_400_000L  // 1 día
                      + 43_200_000L; // 12 horas
        assertEquals(expected, TimeUtils.parse("1d12h"),
                "La combinación 1d12h debe sumar exactamente 1 día y 12 horas");
    }

    @Test
    @DisplayName("parse('abc') debe devolver -1 (entrada inválida)")
    void testParseInvalid() {
        assertEquals(-1L, TimeUtils.parse("abc"));
    }

    @Test
    @DisplayName("parse(null) debe devolver -1")
    void testParseNull() {
        assertEquals(-1L, TimeUtils.parse(null));
    }

    @Test
    @DisplayName("parse('') debe devolver -1")
    void testParseEmpty() {
        assertEquals(-1L, TimeUtils.parse(""));
    }

    // =========================================================================
    // format()
    // =========================================================================

    @Test
    @DisplayName("format(0) o format(negativo) debe devolver 'Permanente'")
    void testFormatPermanent() {
        assertEquals("Permanente", TimeUtils.format(0L));
        assertEquals("Permanente", TimeUtils.format(-1L));
        assertEquals("Permanente", TimeUtils.format(-999L));
    }

    @Test
    @DisplayName("format(86_400_000) debe contener 'día' en el resultado")
    void testFormatDays() {
        String result = TimeUtils.format(86_400_000L);
        assertNotNull(result);
        assertTrue(result.contains("día") || result.contains("dias") || result.contains("días"),
                "El formato de 1 día debe contener 'día'. Resultado: " + result);
    }

    @Test
    @DisplayName("format(7_200_000) debe contener 'hora' en el resultado")
    void testFormatHours() {
        String result = TimeUtils.format(7_200_000L);
        assertNotNull(result);
        assertTrue(result.contains("hora") || result.contains("horas"),
                "El formato de 2 horas debe contener 'hora'. Resultado: " + result);
    }

    @Test
    @DisplayName("format(1d + 12h) debe contener tanto 'día' como 'hora'")
    void testFormatMixed() {
        long millis = 86_400_000L + 43_200_000L; // 1 día + 12 horas
        String result = TimeUtils.format(millis);
        assertNotNull(result);
        // Debe mencionar el día
        assertTrue(result.contains("día") || result.contains("días"),
                "El resultado debe contener 'día'. Resultado: " + result);
        // Debe mencionar las horas (muestra hasta 2 unidades)
        assertTrue(result.contains("hora") || result.contains("horas"),
                "El resultado debe contener 'hora'. Resultado: " + result);
    }

    // =========================================================================
    // formatDate()
    // =========================================================================

    @Test
    @DisplayName("formatDate(timestamp) no debe devolver null ni string vacío")
    void testFormatDateNotNull() {
        String result = TimeUtils.formatDate(System.currentTimeMillis());
        assertNotNull(result, "formatDate no debe devolver null");
        assertFalse(result.isBlank(), "formatDate no debe devolver un string vacío");
    }

    @Test
    @DisplayName("formatDate(timestamp) debe contener '/' y ':' (formato dd/MM/yyyy HH:mm)")
    void testFormatDateFormat() {
        // Usamos un timestamp fijo para tener resultado determinista
        // 2024-06-30 16:00:00 UTC → 1719763200000
        long fixedTimestamp = 1_719_763_200_000L;
        String result = TimeUtils.formatDate(fixedTimestamp);

        assertTrue(result.contains("/"),
                "El formato debe contener '/' entre día, mes y año. Resultado: " + result);
        assertTrue(result.contains(":"),
                "El formato debe contener ':' entre horas y minutos. Resultado: " + result);
    }

    // =========================================================================
    // isValid()
    // =========================================================================

    @Test
    @DisplayName("isValid('7d') debe devolver true")
    void testIsValidTrue() {
        assertTrue(TimeUtils.isValid("7d"));
        assertTrue(TimeUtils.isValid("30s"));
        assertTrue(TimeUtils.isValid("1mo"));
        assertTrue(TimeUtils.isValid("2w"));
        assertTrue(TimeUtils.isValid("1d12h30m"));
    }

    @Test
    @DisplayName("isValid('xyz') debe devolver false")
    void testIsValidFalse() {
        assertFalse(TimeUtils.isValid("xyz"),  "Letras sin número no son válidas");
        assertFalse(TimeUtils.isValid("abc"),  "Texto aleatorio no es válido");
        assertFalse(TimeUtils.isValid(""),     "String vacío no es válido");
        assertFalse(TimeUtils.isValid(null),   "null no es válido");
        assertFalse(TimeUtils.isValid("123"),  "Solo número sin unidad no es válido");
    }
}
