package me.nea.neabans.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Builder fluido para crear ItemStack de Bukkit/Paper.
 *
 * Ejemplo de uso:
 * <pre>
 *   ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
 *       .name("&#FF4D4D&lEspada Mágica")
 *       .lore("&#808080Línea 1", "&#808080Línea 2")
 *       .glow()
 *       .build();
 * </pre>
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    // ─────────────────────────────────────────
    //  Constructores
    // ─────────────────────────────────────────

    /**
     * Crea un ItemBuilder con la cantidad 1.
     *
     * @param material Material del ítem
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }

    /**
     * Crea un ItemBuilder con la cantidad especificada.
     *
     * @param material Material del ítem
     * @param amount   Cantidad (1-64)
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, Math.min(amount, 64)));
        this.meta = item.getItemMeta();
        if (this.meta == null) {
            throw new IllegalArgumentException(
                "El material " + material + " no soporta ItemMeta."
            );
        }
    }

    // ─────────────────────────────────────────
    //  Nombre
    // ─────────────────────────────────────────

    /** Convierte una cadena coloreada (legacy §) a un Component de Adventure. */
    private static Component toComponent(String legacyColored) {
        return LegacyComponentSerializer.legacySection().deserialize(
            legacyColored
        );
    }

    /**
     * Establece el nombre del ítem aplicando colores (Adventure API).
     *
     * @param name Nombre con códigos de color
     * @return this (fluent)
     */
    public ItemBuilder name(String name) {
        meta.displayName(toComponent(ColorUtils.color(name)));
        return this;
    }

    // ─────────────────────────────────────────
    //  Lore
    // ─────────────────────────────────────────

    /**
     * Establece el lore del ítem a partir de varargs, aplicando colores (Adventure API).
     *
     * @param lines Líneas del lore
     * @return this (fluent)
     */
    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    /**
     * Establece el lore del ítem a partir de una lista, aplicando colores (Adventure API).
     *
     * @param lines Lista de líneas del lore
     * @return this (fluent)
     */
    public ItemBuilder lore(List<String> lines) {
        List<Component> components = lines
            .stream()
            .map(line -> toComponent(ColorUtils.color(line)))
            .collect(Collectors.toList());
        meta.lore(components);
        return this;
    }

    /**
     * Añade líneas adicionales al lore existente (Adventure API).
     *
     * @param lines Líneas a añadir
     * @return this (fluent)
     */
    public ItemBuilder addLore(String... lines) {
        List<Component> current = meta.lore();
        if (current == null) current = new ArrayList<>();
        for (String line : lines) {
            current.add(toComponent(ColorUtils.color(line)));
        }
        meta.lore(current);
        return this;
    }

    // ─────────────────────────────────────────
    //  Glow (brillo mágico sin mostrar encantamiento)
    // ─────────────────────────────────────────

    /**
     * Añade un efecto de brillo al ítem (Unbreaking 1 + flag HIDE_ENCHANTS).
     *
     * @return this (fluent)
     */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    // ─────────────────────────────────────────
    //  Flags
    // ─────────────────────────────────────────

    /**
     * Oculta todos los ItemFlags (atributos, encantamientos, durabilidad, etc.).
     *
     * @return this (fluent)
     */
    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    // ─────────────────────────────────────────
    //  Cantidad
    // ─────────────────────────────────────────

    /**
     * Cambia la cantidad del ítem.
     *
     * @param amount Cantidad (1-64)
     * @return this (fluent)
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, 64)));
        return this;
    }

    // ─────────────────────────────────────────
    //  Custom Model Data
    // ─────────────────────────────────────────

    /**
     * Establece el CustomModelData del ítem (para resource packs).
     *
     * @param data Valor de CustomModelData
     * @return this (fluent)
     */
    public ItemBuilder customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    // ─────────────────────────────────────────
    //  Skull Owner  (Material.PLAYER_HEAD)
    // ─────────────────────────────────────────

    /**
     * Establece el dueño de una cabeza de jugador usando OfflinePlayer.
     * Solo funciona si el material es PLAYER_HEAD.
     *
     * @param player Jugador dueño de la cabeza
     * @return this (fluent)
     */
    public ItemBuilder skullOwner(OfflinePlayer player) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    /**
     * Establece el dueño de una cabeza de jugador por nombre.
     * Usa la API de Paper para crear el perfil de forma no bloqueante.
     * Solo funciona si el material es PLAYER_HEAD.
     *
     * @param playerName Nombre del jugador
     * @return this (fluent)
     */
    public ItemBuilder skullOwner(String playerName) {
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(
                UUID.randomUUID(),
                playerName
            );
            skullMeta.setPlayerProfile(profile);
        }
        return this;
    }

    /**
     * Establece una textura de cabeza personalizada a partir de un valor Base64.
     * Solo funciona si el material es PLAYER_HEAD.
     *
     * @param base64Texture Valor de textura en Base64 (obtenido de minecraft-heads.com)
     * @return this (fluent)
     */
    public ItemBuilder skullTexture(String base64Texture) {
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(
                UUID.randomUUID(),
                "CustomHead"
            );
            profile.setProperty(new ProfileProperty("textures", base64Texture));
            skullMeta.setPlayerProfile(profile);
        }
        return this;
    }

    // ─────────────────────────────────────────
    //  Build
    // ─────────────────────────────────────────

    /**
     * Construye y devuelve el ItemStack final.
     *
     * @return ItemStack configurado
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
