package me.nea.neabans.cache;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.nea.neabans.model.Punishment;

/**
 * Caché en memoria de alta concurrencia para sanciones activas.
 *
 * <p>Evita consultas a la base de datos en cada mensaje de chat o acción
 * frecuente, usando {@link ConcurrentHashMap} para seguridad en hilos.</p>
 *
 * <h2>TTL (Time To Live)</h2>
 * Las entradas expiran automáticamente después de {@value #TTL_MS} ms.
 * Pasado ese tiempo, la siguiente consulta va a la BD y refresca la caché.
 *
 * <h2>Thread safety</h2>
 * Todos los métodos son seguros para llamarse desde hilos async.
 */
public final class PunishmentCache {

    /** Tiempo de vida de cada entrada en caché: 10 minutos. */
    private static final long TTL_MS = 10 * 60 * 1000L;

    // UUID -> CacheEntry<Punishment> (ban activo, null = sabemos que no hay ban)
    private final ConcurrentHashMap<UUID, CacheEntry<Punishment>> banCache =
        new ConcurrentHashMap<>();
    // UUID -> CacheEntry<Punishment> (mute activo)
    private final ConcurrentHashMap<UUID, CacheEntry<Punishment>> muteCache =
        new ConcurrentHashMap<>();
    // IP   -> CacheEntry<Punishment> (IP-ban activo)
    private final ConcurrentHashMap<String, CacheEntry<Punishment>> ipBanCache =
        new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────
    //  BAN CACHE
    // ──────────────────────────────────────────────────

    /**
     * Almacena el ban activo de un jugador.
     * Pasar {@code null} para registrar "sabemos que no está baneado" y evitar ir a la BD.
     */
    public void cacheBan(UUID uuid, Punishment ban) {
        banCache.put(uuid, new CacheEntry<>(ban));
    }

    /**
     * Obtiene el ban cacheado.
     * @return La entrada cacheada (puede ser null = "sin ban"). Retorna {@code null}
     *         si NO hay entrada en caché o si la TTL expiró → hay que consultar la BD.
     */
    public CacheResult<Punishment> getBan(UUID uuid) {
        CacheEntry<Punishment> entry = banCache.get(uuid);
        if (entry == null || entry.isExpired()) {
            banCache.remove(uuid);
            return CacheResult.miss();
        }
        return CacheResult.hit(entry.value);
    }

    /** Invalida la entrada (al desbanear o cuando sabemos que cambió). */
    public void invalidateBan(UUID uuid) {
        banCache.remove(uuid);
    }

    // ──────────────────────────────────────────────────
    //  MUTE CACHE
    // ──────────────────────────────────────────────────

    public void cacheMute(UUID uuid, Punishment mute) {
        muteCache.put(uuid, new CacheEntry<>(mute));
    }

    public CacheResult<Punishment> getMute(UUID uuid) {
        CacheEntry<Punishment> entry = muteCache.get(uuid);
        if (entry == null || entry.isExpired()) {
            muteCache.remove(uuid);
            return CacheResult.miss();
        }
        return CacheResult.hit(entry.value);
    }

    public void invalidateMute(UUID uuid) {
        muteCache.remove(uuid);
    }

    // ──────────────────────────────────────────────────
    //  IP-BAN CACHE
    // ──────────────────────────────────────────────────

    public void cacheIpBan(String ip, Punishment ban) {
        if (ip != null) ipBanCache.put(ip, new CacheEntry<>(ban));
    }

    public CacheResult<Punishment> getIpBan(String ip) {
        if (ip == null) return CacheResult.miss();
        CacheEntry<Punishment> entry = ipBanCache.get(ip);
        if (entry == null || entry.isExpired()) {
            ipBanCache.remove(ip);
            return CacheResult.miss();
        }
        return CacheResult.hit(entry.value);
    }

    public void invalidateIpBan(String ip) {
        if (ip != null) ipBanCache.remove(ip);
    }

    // ──────────────────────────────────────────────────
    //  MANTENIMIENTO
    // ──────────────────────────────────────────────────

    /** Elimina todas las entradas expiradas. Llamar periódicamente para evitar memory leaks. */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        banCache.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        muteCache.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        ipBanCache.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    /** Limpia todo. Llamar en onDisable() para liberar memoria correctamente. */
    public void clear() {
        banCache.clear();
        muteCache.clear();
        ipBanCache.clear();
    }

    /** Stats para debug/logging. */
    public String stats() {
        return (
            "Cache{bans=" +
            banCache.size() +
            ", mutes=" +
            muteCache.size() +
            ", ipBans=" +
            ipBanCache.size() +
            "}"
        );
    }

    // ──────────────────────────────────────────────────
    //  Tipos internos
    // ──────────────────────────────────────────────────

    /** Wrapper con TTL para cada entrada en la caché. */
    private static final class CacheEntry<T> {

        final T value;
        final long expiresAt;

        CacheEntry(T value) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Resultado de una consulta a la caché.
     * Distingue entre "no hay entrada" (miss) y "hay entrada con valor null" (hit con null = sin sanción).
     */
    public static final class CacheResult<T> {

        private final boolean present;
        private final T value;

        private CacheResult(boolean present, T value) {
            this.present = present;
            this.value = value;
        }

        public static <T> CacheResult<T> hit(T value) {
            return new CacheResult<>(true, value);
        }

        public static <T> CacheResult<T> miss() {
            return new CacheResult<>(false, null);
        }

        /** @return true si hay una entrada en caché (aunque el valor sea null). */
        public boolean isCached() {
            return present;
        }

        /** @return El valor cacheado (puede ser null si el jugador no tiene sanción activa). */
        public T getValue() {
            return value;
        }
    }
}
