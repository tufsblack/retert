package nl.beneluxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bouwstenen voor alle menu's. Elk item dat hier gemaakt wordt krijgt een markering, zodat het nooit echt in een inventaris kan blijven. */
final class Items {
    private final BeneluxPvP pl;
    private NamespacedKey mark;

    Items(BeneluxPvP pl) {
        this.pl = pl;
    }

    NamespacedKey markKey() {
        if (mark == null) mark = new NamespacedKey(pl, "menu_item");
        return mark;
    }

    boolean isMenuItem(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(markKey(), PersistentDataType.BYTE);
    }

    Component c(String mini) {
        return pl.parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    /** Markeert een bestaand item (bijvoorbeeld een kit-voorbeeld) als menu-item. */
    ItemStack mark(ItemStack it) {
        it.editMeta(meta -> meta.getPersistentDataContainer().set(markKey(), PersistentDataType.BYTE, (byte) 1));
        return it;
    }

    ItemStack icon(Material m, String name, String... lore) {
        return icon(new ItemStack(m), name, lore);
    }

    /** Zelfde, maar met een kant-en-klaar item als basis (bijv. een 3D-kroon uit Cosmetics). */
    ItemStack icon(ItemStack base, String name, String... lore) {
        ItemStack it = base.clone();
        it.setAmount(1);
        it.editMeta(meta -> {
            meta.displayName(c(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                meta.lore(l);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(markKey(), PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    ItemStack icon(ItemStack base, String name, List<String> lore) {
        return icon(base, name, lore.toArray(new String[0]));
    }

    ItemStack icon(Material m, String name, List<String> lore) {
        return icon(m, name, lore.toArray(new String[0]));
    }

    ItemStack glow(ItemStack it) {
        it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return it;
    }

    ItemStack filler(Material m) {
        return icon(m, " ");
    }

    ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, meta -> {
            try {
                meta.setOwningPlayer(owner);
            } catch (RuntimeException ignored) {
                // Een hoofd zonder skin is prima.
            }
            meta.displayName(c(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
            meta.getPersistentDataContainer().set(markKey(), PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    void tick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    void deny(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("<", "‹").replace(">", "›");
    }

    static int pageCount(int n, int per) {
        return Math.max(1, (n + per - 1) / per);
    }

    /** Voortgangsbalk van 10 blokjes. */
    static String bar(int have, int need) {
        int total = 10;
        int full = need <= 0 ? total : (int) Math.min(total, Math.floor((double) Math.max(0, have) * total / need));
        StringBuilder sb = new StringBuilder("<green>");
        for (int i = 0; i < full; i++) sb.append('▰');
        sb.append("<dark_gray>");
        for (int i = full; i < total; i++) sb.append('▰');
        return sb.toString();
    }

    static String time(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return (seconds / 3600) + " uur";
        if (seconds >= 60 && seconds % 60 == 0) return (seconds / 60) + " min";
        if (seconds >= 60) return (seconds / 60) + " min " + (seconds % 60) + " sec";
        return seconds + " sec";
    }

    static String kd(int kills, int deaths) {
        double v = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Veilige id: kleine letters, cijfers, - en _ (max. 24 tekens). Leeg als er niets overblijft. */
    static String slug(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replaceAll("[^a-z0-9_-]", "");
        return t.length() > 24 ? t.substring(0, 24) : t;
    }
}
