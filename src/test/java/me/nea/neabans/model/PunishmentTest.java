package me.nea.neabans.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para {@link Punishment}.
 *
 * <p>{@code Punishment} es un POJO puro sin dependencias de Bukkit, por lo que
 * todos los tests corren sin servidor y sin mocks adicionales.</p>
 */
@DisplayName("Punishment")
class PunishmentTest {

    // =========================================================================
    // Helpers de fabricación de instancias
    // =========================================================================

    /**
     * Crea una sanción de BAN con los parámetros esenciales.
     *
     * @param endTime Timestamp de fin en epoch millis; {@code -1} para permanente.
     * @return Nueva instancia de {@link Punishment}.
     */
    private Punishment makePunishment(long endTime) {
        return new Punishment(
                UUID.randomUUID(),          // playerUuid
                "TestPlayer",               // playerName
                PunishmentType.BAN,         // type
                "Razón de prueba",          // reason
                null,                       // category
                0,                          // level
                UUID.randomUUID(),          // staffUuid
                "StaffMember",              // staffName
                "survival",                 // server
                endTime                     // endTime (-1 = permanente)
        );
    }

    /**
     * Crea una sanción con todos los campos (constructor de carga desde BD).
     *
     * @param active  Si la sanción está marcada activa.
     * @param endTime Timestamp de fin; {@code -1} para permanente.
     * @return Instancia de {@link Punishment} cargada desde BD.
     */
    private Punishment makePunishmentFull(boolean active, long endTime) {
        return new Punishment(
                1,                          // id
                UUID.randomUUID(),          // playerUuid
                "TestPlayer",               // playerName
                PunishmentType.BAN,         // type
                "Razón de prueba",          // reason
                null,                       // category
                0,                          // level
                UUID.randomUUID(),          // staffUuid
                "StaffMember",              // staffName
                "survival",                 // server
                System.currentTimeMillis(), // startTime
                endTime,                    // endTime
                active,                     // active
                null,                       // removedBy
                0L                          // removedAt
        );
    }

    // =========================================================================
    // isPermanent()
    // =========================================================================

    @Test
    @DisplayName("isPermanent() debe ser true cuando endTime == -1")
    void testIsPermanentWhenEndTimeMinusOne() {
        Punishment p = makePunishment(-1L);
        assertTrue(p.isPermanent(),
                "Una sanción con endTime=-1 debe ser permanente");
    }

    @Test
    @DisplayName("isPermanent() debe ser false cuando endTime está en el futuro")
    void testIsNotPermanentWithDuration() {
        long futureTime = System.currentTimeMillis() + 3_600_000L; // +1h
        Punishment p = makePunishment(futureTime);
        assertFalse(p.isPermanent(),
                "Una sanción con endTime futuro no debe ser permanente");
    }

    // =========================================================================
    // isExpired()
    // =========================================================================

    @Test
    @DisplayName("isExpired() debe ser true cuando endTime ya pasó")
    void testIsExpiredWhenPast() {
        long pastTime = System.currentTimeMillis() - 1_000L; // -1s
        Punishment p = makePunishment(pastTime);
        assertTrue(p.isExpired(),
                "Una sanción cuyo endTime ya pasó debe estar expirada");
    }

    @Test
    @DisplayName("isExpired() debe ser false cuando endTime está en el futuro")
    void testIsNotExpiredWhenFuture() {
        long futureTime = System.currentTimeMillis() + 3_600_000L; // +1h
        Punishment p = makePunishment(futureTime);
        assertFalse(p.isExpired(),
                "Una sanción con endTime futuro no debe estar expirada");
    }

    @Test
    @DisplayName("isExpired() debe ser false para sanciones permanentes (endTime=-1)")
    void testPermanentNeverExpires() {
        Punishment p = makePunishment(-1L);
        assertFalse(p.isExpired(),
                "Una sanción permanente (endTime=-1) nunca debe expirar");
    }

    // =========================================================================
    // isActive()
    // =========================================================================

    @Test
    @DisplayName("isActive() debe ser true para ban permanente activo")
    void testIsActivePermanentBan() {
        // active=true, endTime=-1 (permanente) → isActive()=true
        Punishment p = makePunishmentFull(true, -1L);
        assertTrue(p.isActive(),
                "Un ban permanente con active=true debe estar activo");
    }

    @Test
    @DisplayName("isActive() debe ser false cuando la sanción ya expiró")
    void testIsNotActiveWhenExpired() {
        long pastTime = System.currentTimeMillis() - 5_000L; // expirado hace 5s
        // active=true pero endTime en el pasado → isExpired()=true → isActive()=false
        Punishment p = makePunishmentFull(true, pastTime);
        assertFalse(p.isActive(),
                "Una sanción expirada no debe estar activa aunque active=true");
    }

    @Test
    @DisplayName("isActive() debe ser false cuando active=false, sin importar la expiración")
    void testIsNotActiveWhenDeactivated() {
        long futureTime = System.currentTimeMillis() + 3_600_000L;
        // active=false → isActive()=false aunque no haya expirado
        Punishment p = makePunishmentFull(false, futureTime);
        assertFalse(p.isActive(),
                "Una sanción con active=false no debe estar activa aunque no haya expirado");
    }

    @Test
    @DisplayName("isActive() debe ser false para sanción desactivada Y expirada")
    void testIsNotActiveWhenBothDeactivatedAndExpired() {
        long pastTime = System.currentTimeMillis() - 1_000L;
        Punishment p = makePunishmentFull(false, pastTime);
        assertFalse(p.isActive(),
                "Una sanción expirada y desactivada no debe estar activa");
    }

    // =========================================================================
    // getRemainingMillis()
    // =========================================================================

    @Test
    @DisplayName("getRemainingMillis() debe ser positivo para sanción con endTime futuro")
    void testGetRemainingMillisPositive() {
        long futureTime = System.currentTimeMillis() + 60_000L; // +1 min
        Punishment p = makePunishment(futureTime);
        long remaining = p.getRemainingMillis();
        assertTrue(remaining > 0,
                "El tiempo restante debe ser positivo para una sanción futura. Valor: " + remaining);
    }

    @Test
    @DisplayName("getRemainingMillis() debe devolver Long.MAX_VALUE para sanciones permanentes")
    void testGetRemainingMillisForPermanent() {
        // La implementación real devuelve Long.MAX_VALUE para permanentes
        Punishment p = makePunishment(-1L);
        assertEquals(Long.MAX_VALUE, p.getRemainingMillis(),
                "getRemainingMillis() debe devolver Long.MAX_VALUE para sanciones permanentes");
    }

    @Test
    @DisplayName("getRemainingMillis() debe devolver 0 (no negativo) para sanciones expiradas")
    void testGetRemainingMillisZeroForExpired() {
        long pastTime = System.currentTimeMillis() - 5_000L;
        Punishment p = makePunishment(pastTime);
        long remaining = p.getRemainingMillis();
        assertEquals(0L, remaining,
                "Una sanción expirada debe tener 0 ms restantes, no un valor negativo. Valor: " + remaining);
    }

    // =========================================================================
    // Constructor — startTime
    // =========================================================================

    @Test
    @DisplayName("El constructor debe establecer startTime cercano a System.currentTimeMillis()")
    void testConstructorSetsStartTime() {
        long before = System.currentTimeMillis();
        Punishment p = makePunishment(-1L);
        long after  = System.currentTimeMillis();

        // startTime debe estar entre before y after (tolerancia de 1s para sistemas lentos)
        assertTrue(p.getStartTime() >= before,
                "startTime no debe ser anterior al momento de creación");
        assertTrue(p.getStartTime() <= after + 1_000L,
                "startTime no debe ser significativamente posterior al momento de creación");
    }

    @Test
    @DisplayName("El constructor debe establecer active=true por defecto")
    void testConstructorSetsActiveTrue() {
        Punishment p = makePunishment(-1L);
        assertTrue(p.getActiveFlag(),
                "Una sanción recién creada debe tener active=true");
    }

    @Test
    @DisplayName("El constructor debe establecer id=-1 antes de persistir en BD")
    void testConstructorSetsIdMinusOne() {
        Punishment p = makePunishment(-1L);
        assertEquals(-1, p.getId(),
                "Una sanción no persistida debe tener id=-1");
    }

    @Test
    @DisplayName("setActive(false) debe cambiar el flag y hacer isActive() false")
    void testSetActiveChangesFlag() {
        Punishment p = makePunishment(-1L); // permanente, activa
        assertTrue(p.isActive());

        p.setActive(false);
        assertFalse(p.getActiveFlag(), "getActiveFlag() debe ser false tras setActive(false)");
        assertFalse(p.isActive(),      "isActive() debe ser false tras setActive(false)");
    }

    @Test
    @DisplayName("setRemovedBy y setRemovedAt deben persistir los valores asignados")
    void testSetRemovedByAndAt() {
        Punishment p = makePunishment(-1L);
        long now = System.currentTimeMillis();

        p.setActive(false);
        p.setRemovedBy("AdminConsole");
        p.setRemovedAt(now);

        assertEquals("AdminConsole", p.getRemovedBy());
        assertEquals(now, p.getRemovedAt());
    }
}
