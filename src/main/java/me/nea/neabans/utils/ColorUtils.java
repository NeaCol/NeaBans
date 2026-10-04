package me.nea.neabans.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;

/**
 * Utilidad para aplicar colores en cadenas de texto.
 * Soporta:
 *  - Códigos estándar con & (ej. &a, &l, &r)
 *  - Colores HEX en formato &#RRGGBB (ej. &#FF4D4D)
 *  - Reemplazo de placeholders {key}
 */
public final class ColorUtils {

    /** Patrón para capturar colores HEX del tipo &#RRGGBB */
    private static final Pattern HEX_PATTERN = Pattern.compile(
        "&#([A-Fa-f0-9]{6})"
    );

    private ColorUtils() {}

    // ─────────────────────────────────────────
    //  Métodos principales
    // ─────────────────────────────────────────

    /**
     * Traduce códigos &amp; y colores HEX &#RRGGBB en un texto.
     *
     * @param text Texto a colorear
     * @return Texto con los colores aplicados
     */
    public static String color(String text) {
        if (text == null) return "";

        // Procesar colores HEX primero
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            ChatColor hexColor = ChatColor.of("#" + hex);
            matcher.appendReplacement(
                sb,
                Matcher.quoteReplacement(hexColor.toString())
            );
        }
        matcher.appendTail(sb);

        // Luego traducir los códigos & estándar
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /**
     * Reemplaza placeholders {key} por sus valores, luego aplica colores.
     *
     * @param text         Texto con placeholders y/o códigos de color
     * @param placeholders Mapa de clave -> valor a reemplazar
     * @return Texto con placeholders resueltos y colores aplicados
     */
    public static String color(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue() : "";
                // Acepta claves con llaves "{player}" o sin "{" + "player" + "}"
                if (key.startsWith("{") && key.endsWith("}")) {
                    text = text.replace(key, value);
                } else {
                    text = text.replace("{" + key + "}", value);
                }
            }
        }
        return color(text);
    }

    /**
     * Elimina todos los códigos de color de un texto (incluyendo HEX).
     *
     * @param text Texto con colores
     * @return Texto limpio sin códigos de color
     */
    public static String strip(String text) {
        if (text == null) return "";
        // Eliminar HEX primero
        text = HEX_PATTERN.matcher(text).replaceAll("");
        // Eliminar códigos § y &
        return ChatColor.stripColor(
            ChatColor.translateAlternateColorCodes('&', text)
        );
    }

    /**
     * Aplica colores a cada línea de una lista de Strings.
     *
     * @param lines Lista de líneas
     * @return Nueva lista con colores aplicados
     */
    public static List<String> colorList(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }

    /**
     * Aplica colores y reemplaza placeholders en cada línea de una lista.
     *
     * @param lines        Lista de líneas
     * @param placeholders Mapa de clave -> valor
     * @return Nueva lista con colores y placeholders aplicados
     */
    public static List<String> colorList(
        List<String> lines,
        Map<String, String> placeholders
    ) {
        if (lines == null) return new ArrayList<>();
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line, placeholders));
        }
        return colored;
    }
}
