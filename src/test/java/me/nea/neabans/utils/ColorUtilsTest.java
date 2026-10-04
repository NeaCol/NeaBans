package me.nea.neabans.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para {@link ColorUtils}.
 *
 * <p>{@code ColorUtils} usa {@code net.md_5.bungee.api.ChatColor}, que forma
 * parte de la Paper API ({@code provided}). Dicha clase es puro Java y no
 * requiere un servidor en ejecución, por lo que los tests corren correctamente
 * con la API en el classpath de Maven.</p>
 *
 * <h2>Notas de diseño</h2>
 * <ul>
 *   <li>No se utiliza Mockito aquí porque {@code ChatColor} es una clase
 *       concreta con lógica de string manipulation.</li>
 *   <li>No se verifica el color exacto del carácter {@code §} porque el
 *       resultado depende de la implementación interna de BungeeCord; solo
 *       se comprueba que el string resultante sea no-nulo y no-vacío.</li>
 * </ul>
 */
@DisplayName("ColorUtils")
class ColorUtilsTest {

    // =========================================================================
    // strip()
    // =========================================================================

    @Test
    @DisplayName(
        "strip('&aHola') debe eliminar el código de color y devolver 'Hola'"
    )
    void testStripRemovesAmpersandCodes() {
        String result = ColorUtils.strip("&aHola");
        assertNotNull(result);
        // El texto visible debe ser "Hola", sin caracteres §
        assertEquals(
            "Hola",
            result,
            "strip() debe eliminar los códigos de color &a. Resultado: '" +
                result +
                "'"
        );
    }

    @Test
    @DisplayName("strip('&l&cTexto &rNormal') debe devolver solo texto plano")
    void testStripRemovesMultipleCodes() {
        String result = ColorUtils.strip("&l&cTexto &rNormal");
        assertNotNull(result);
        assertFalse(
            result.contains("§"),
            "El resultado no debe contener el carácter §. Resultado: '" +
                result +
                "'"
        );
        assertFalse(
            result.contains("&"),
            "El resultado no debe contener '&'. Resultado: '" + result + "'"
        );
        assertTrue(
            result.contains("Texto"),
            "El texto visible 'Texto' debe conservarse"
        );
        assertTrue(
            result.contains("Normal"),
            "El texto visible 'Normal' debe conservarse"
        );
    }

    @Test
    @DisplayName("strip(null) debe devolver un string vacío, nunca lanzar NPE")
    void testStripWithNull() {
        String result = ColorUtils.strip(null);
        assertNotNull(result, "strip(null) no debe devolver null");
        assertEquals("", result, "strip(null) debe devolver string vacío");
    }

    @Test
    @DisplayName("strip('') debe devolver string vacío")
    void testStripWithEmpty() {
        String result = ColorUtils.strip("");
        assertNotNull(result);
        assertEquals("", result);
    }

    // =========================================================================
    // color(String)
    // =========================================================================

    @Test
    @DisplayName("color('&aHola') debe devolver un string no-nulo y no-vacío")
    void testColorNotNull() {
        String result = ColorUtils.color("&aHola");
        assertNotNull(result, "color() no debe devolver null");
        assertFalse(
            result.isBlank(),
            "color() no debe devolver un string vacío"
        );
    }

    @Test
    @DisplayName("color(null) debe devolver string vacío, nunca lanzar NPE")
    void testColorWithNull() {
        String result = ColorUtils.color(null);
        assertNotNull(result, "color(null) no debe devolver null");
        assertEquals("", result, "color(null) debe devolver string vacío");
    }

    @Test
    @DisplayName(
        "color('&#FF4D4DHola') con HEX debe devolver un string no-nulo y no-vacío"
    )
    void testColorWithHex() {
        String result = ColorUtils.color("&#FF4D4DHola");
        assertNotNull(result, "color() con HEX no debe devolver null");
        assertFalse(
            result.isBlank(),
            "color() con HEX no debe devolver un string vacío"
        );
        // El texto 'Hola' debe seguir presente después del color HEX
        assertTrue(
            result.contains("Hola"),
            "El texto 'Hola' debe estar presente en el resultado. Resultado: '" +
                result +
                "'"
        );
    }

    @Test
    @DisplayName(
        "color('&#00FF00Verde &#FF0000Rojo') con múltiples HEX devuelve string no-vacío"
    )
    void testColorWithMultipleHex() {
        String result = ColorUtils.color("&#00FF00Verde &#FF0000Rojo");
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("Verde"), "Debe contener 'Verde'");
        assertTrue(result.contains("Rojo"), "Debe contener 'Rojo'");
    }

    // =========================================================================
    // color(String, Map)
    // =========================================================================

    @Test
    @DisplayName(
        "color('{player}', Map) debe reemplazar el placeholder con el valor"
    )
    void testPlaceholderReplacement() {
        Map<String, String> placeholders = Map.of("player", "Steve");
        String result = ColorUtils.color("{player}", placeholders);

        assertNotNull(result, "El resultado no debe ser null");
        assertFalse(result.isBlank(), "El resultado no debe estar vacío");
        assertTrue(
            result.contains("Steve"),
            "El placeholder {player} debe reemplazarse por 'Steve'. Resultado: '" +
                result +
                "'"
        );
        assertFalse(
            result.contains("{player}"),
            "El placeholder {player} no debe quedar en el resultado"
        );
    }

    @Test
    @DisplayName(
        "color con múltiples placeholders reemplaza todos correctamente"
    )
    void testMultiplePlaceholders() {
        Map<String, String> placeholders = Map.of(
            "staff",
            "AdminUser",
            "target",
            "BannedPlayer",
            "reason",
            "Hacking"
        );
        String template = "&c{staff} &7baneó a &e{target} &7por &c{reason}";
        String result = ColorUtils.color(template, placeholders);

        assertNotNull(result);
        assertTrue(result.contains("AdminUser"), "Debe contener 'AdminUser'");
        assertTrue(
            result.contains("BannedPlayer"),
            "Debe contener 'BannedPlayer'"
        );
        assertTrue(result.contains("Hacking"), "Debe contener 'Hacking'");
    }

    @Test
    @DisplayName("color(null, Map) debe devolver string vacío sin NPE")
    void testPlaceholderWithNullText() {
        String result = ColorUtils.color(null, Map.of("key", "value"));
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName(
        "color(text, null) debe aplicar colores aunque el mapa sea null"
    )
    void testColorWithNullPlaceholders() {
        String result = ColorUtils.color("&bHola", null);
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("Hola"));
    }

    // =========================================================================
    // colorList()
    // =========================================================================

    @Test
    @DisplayName("colorList(null) debe devolver lista vacía sin NPE")
    void testColorListWithNull() {
        var result = ColorUtils.colorList(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
