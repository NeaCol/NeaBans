package me.nea.neabans.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para parsear y formatear tiempos en el plugin.
 *
 * Unidades soportadas en parse():
 *   s  -> segundos
 *   m  -> minutos
 *   h  -> horas
 *   d  -> días
 *   w  -> semanas
 *   mo -> meses (30 días)
 *   y  -> años  (365 días)
 *
 * Ejemplos: "10s", "5m", "2h", "7d", "2w", "1mo", "1y", "1d12h30m"
 */
public final class TimeUtils {

    // ─── Constantes de tiempo (en milisegundos) ──────────────────────────
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    /** Patrón para capturar pares (número)(unidad) dentro de una cadena de duración */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
        "(\\d+)(mo|[smhdwy])",
        Pattern.CASE_INSENSITIVE
    );

    /** Formato de fecha corta */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
        "dd/MM/yyyy HH:mm"
    );

    /** Formato de fecha largo en español */
    private static final SimpleDateFormat DATE_FORMAT_FULL =
        new SimpleDateFormat(
            "dd 'de' MMMM 'de' yyyy, HH:mm",
            Locale.of("es", "ES")
        );

    /** Nombres en español de las unidades, ordenados de mayor a menor */
    private static final LinkedHashMap<Long, String[]> UNIT_NAMES =
        new LinkedHashMap<>();

    static {
        // [singular, plural]
        UNIT_NAMES.put(YEAR, new String[] { "año", "años" });
        UNIT_NAMES.put(MONTH, new String[] { "mes", "meses" });
        UNIT_NAMES.put(WEEK, new String[] { "semana", "semanas" });
        UNIT_NAMES.put(DAY, new String[] { "día", "días" });
        UNIT_NAMES.put(HOUR, new String[] { "hora", "horas" });
        UNIT_NAMES.put(MINUTE, new String[] { "minuto", "minutos" });
        UNIT_NAMES.put(SECOND, new String[] { "segundo", "segundos" });
    }

    /** Nombres cortos para formatShort() */
    private static final LinkedHashMap<Long, String> UNIT_SHORT =
        new LinkedHashMap<>();

    static {
        UNIT_SHORT.put(YEAR, "a");
        UNIT_SHORT.put(MONTH, "mo");
        UNIT_SHORT.put(WEEK, "sem");
        UNIT_SHORT.put(DAY, "d");
        UNIT_SHORT.put(HOUR, "h");
        UNIT_SHORT.put(MINUTE, "m");
        UNIT_SHORT.put(SECOND, "s");
    }

    private TimeUtils() {}

    // ─────────────────────────────────────────
    //  parse
    // ─────────────────────────────────────────

    /**
     * Parsea una cadena de duración a milisegundos.
     * Soporta combinaciones como "1d12h30m".
     *
     * @param input Cadena de duración (p.ej. "7d", "1mo", "1d12h")
     * @return Duración en milisegundos, o -1 si no se pudo parsear
     */
    public static long parse(String input) {
        if (input == null || input.isBlank()) return -1;

        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        long total = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            total += switch (unit) {
                case "s" -> amount * SECOND;
                case "m" -> amount * MINUTE;
                case "h" -> amount * HOUR;
                case "d" -> amount * DAY;
                case "w" -> amount * WEEK;
                case "mo" -> amount * MONTH;
                case "y" -> amount * YEAR;
                default -> 0;
            };
        }

        return found ? total : -1;
    }

    // ─────────────────────────────────────────
    //  format  (legible en español)
    // ─────────────────────────────────────────

    /**
     * Formatea milisegundos a una cadena legible en español.
     * Muestra hasta 2 unidades (p.ej. "2 horas 30 minutos").
     * Devuelve "Permanente" si millis es <= 0.
     *
     * @param millis Duración en milisegundos
     * @return Texto legible
     */
    public static String format(long millis) {
        if (millis <= 0) return "Permanente";

        StringBuilder sb = new StringBuilder();
        long remaining = millis;
        int parts = 0;

        for (Map.Entry<Long, String[]> entry : UNIT_NAMES.entrySet()) {
            long unit = entry.getKey();
            if (remaining >= unit) {
                long count = remaining / unit;
                remaining %= unit;
                String name = (count == 1)
                    ? entry.getValue()[0]
                    : entry.getValue()[1];
                if (sb.length() > 0) sb.append(" ");
                sb.append(count).append(" ").append(name);
                if (++parts == 2) break;
            }
        }

        return sb.isEmpty() ? "menos de un segundo" : sb.toString();
    }

    // ─────────────────────────────────────────
    //  formatShort
    // ─────────────────────────────────────────

    /**
     * Formatea milisegundos a formato corto (p.ej. "7d", "2h30m", "Perm").
     *
     * @param millis Duración en milisegundos
     * @return Texto corto
     */
    public static String formatShort(long millis) {
        if (millis <= 0) return "Perm";

        StringBuilder sb = new StringBuilder();
        long remaining = millis;
        int parts = 0;

        for (Map.Entry<Long, String> entry : UNIT_SHORT.entrySet()) {
            long unit = entry.getKey();
            if (remaining >= unit) {
                long count = remaining / unit;
                remaining %= unit;
                sb.append(count).append(entry.getValue());
                if (++parts == 3) break;
            }
        }

        return sb.isEmpty() ? "0s" : sb.toString();
    }

    // ─────────────────────────────────────────
    //  formatDate
    // ─────────────────────────────────────────

    /**
     * Formatea un timestamp (millis desde epoch) a "dd/MM/yyyy HH:mm".
     *
     * @param timestamp Timestamp en milisegundos
     * @return Cadena con la fecha formateada
     */
    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    // ─────────────────────────────────────────
    //  formatDateFull
    // ─────────────────────────────────────────

    /**
     * Formatea un timestamp a "dd de MMMM de yyyy, HH:mm" en español.
     * Ejemplo: "15 de enero de 2025, 14:30"
     *
     * @param timestamp Timestamp en milisegundos
     * @return Cadena con la fecha formateada en español completo
     */
    public static String formatDateFull(long timestamp) {
        return DATE_FORMAT_FULL.format(new Date(timestamp));
    }

    // ─────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────

    /**
     * Devuelve si una cadena de duración es válida (parseable).
     *
     * @param input Cadena a verificar
     * @return true si es válida
     */
    public static boolean isValid(String input) {
        return parse(input) >= 0;
    }

    /**
     * Calcula el timestamp de expiración a partir de ahora + duración.
     *
     * @param durationMillis Duración en milisegundos
     * @return Timestamp de expiración, o -1 si es permanente
     */
    public static long expireAt(long durationMillis) {
        if (durationMillis <= 0) return -1;
        return System.currentTimeMillis() + durationMillis;
    }

    /**
     * Comprueba si un timestamp de expiración ya ha pasado.
     *
     * @param expireTimestamp Timestamp de expiración (-1 = permanente)
     * @return true si ha expirado
     */
    public static boolean hasExpired(long expireTimestamp) {
        if (expireTimestamp <= 0) return false; // permanente
        return System.currentTimeMillis() >= expireTimestamp;
    }

    /**
     * Calcula los milisegundos restantes hasta un timestamp de expiración.
     *
     * @param expireTimestamp Timestamp de expiración (-1 = permanente)
     * @return Milisegundos restantes, o -1 si es permanente, o 0 si ya expiró
     */
    public static long remaining(long expireTimestamp) {
        if (expireTimestamp <= 0) return -1;
        long rem = expireTimestamp - System.currentTimeMillis();
        return Math.max(rem, 0);
    }
}
