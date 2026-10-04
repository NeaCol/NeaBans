package me.nea.neabans.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * InventoryHolder personalizado para todos los menús de NeaBans.
 *
 * <p>El campo {@code type} diferencia el tipo de menú activo:
 * <ul>
 *   <li>{@code "PUNISHER"}  — Menú principal de categorías de sanción.</li>
 *   <li>{@code "CATEGORY"}  — Sub-menú de niveles de una categoría.</li>
 *   <li>{@code "HISTORY"}   — Menú de historial de sanciones.</li>
 *   <li>{@code "EDITOR"}    — Editor de configuración (reservado).</li>
 * </ul>
 * </p>
 */
public class GuiHolder implements InventoryHolder {

    // ─── Campos ──────────────────────────────────────────────────────────────

    /** Tipo de menú: "PUNISHER", "CATEGORY", "HISTORY", "EDITOR". */
    private final String type;

    /** UUID del jugador que es objetivo de la sanción / consulta. */
    private final UUID targetUuid;

    /** Nombre del jugador objetivo en el momento en que se abrió el menú. */
    private final String targetName;

    /**
     * Clave de la categoría seleccionada (solo relevante en {@code CATEGORY}).
     * {@code null} si el tipo de menú no involucra categorías.
     */
    private final String category;

    /** Página actual del inventario (base 0). */
    private final int page;

    /**
     * Referencia al inventario de Bukkit asociado.
     * Se establece justo después de crear el inventario, antes de abrirlo.
     */
    private Inventory inventory;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * Crea un nuevo holder para un menú de NeaBans.
     *
     * @param type       Tipo de menú (ver constantes de la clase).
     * @param targetUuid UUID del jugador objetivo.
     * @param targetName Nombre del jugador objetivo.
     * @param category   Clave de la categoría (puede ser {@code null}).
     * @param page       Página actual (base 0).
     */
    public GuiHolder(String type, UUID targetUuid, String targetName, String category, int page) {
        this.type       = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.category   = category;
        this.page       = page;
    }

    // ─── InventoryHolder ─────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * Devuelve la instancia del inventario asociada a este holder.
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Asigna el inventario de Bukkit a este holder.
     * Debe llamarse inmediatamente después de {@code Bukkit.createInventory()}.
     *
     * @param inventory El inventario recién creado.
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    /** @return Tipo de menú: "PUNISHER", "CATEGORY", "HISTORY" o "EDITOR". */
    public String getType() {
        return type;
    }

    /** @return UUID del jugador objetivo. */
    public UUID getTargetUuid() {
        return targetUuid;
    }

    /** @return Nombre del jugador objetivo. */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @return Clave de la categoría activa, o {@code null} si no aplica.
     */
    public String getCategory() {
        return category;
    }

    /** @return Página actual del inventario (base 0). */
    public int getPage() {
        return page;
    }
}
