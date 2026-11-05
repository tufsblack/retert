package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * De koppeling met de Cosmetics-plugin (versie 1.4.0 of nieuwer), via reflectie.
 * Zo hoeft BeneluxPvP niet tegen Cosmetics gebouwd te worden.
 */
final class Bridge {
    /** Eén cosmetic uit de catalogus van Cosmetics. */
    record Row(String key, String name, String catLabel, String catKey, Material icon, String permission) {
    }

    private final BeneluxPvP pl;
    private boolean warned;

    Bridge(BeneluxPvP pl) {
        this.pl = pl;
    }

    private Plugin cos() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Cosmetics");
        return p != null && p.isEnabled() ? p : null;
    }

    private static Method method(Plugin p, String name, Class<?>... params) {
        try {
            return p.getClass().getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Is Cosmetics aanwezig én nieuw genoeg? */
    boolean ready() {
        Plugin p = cos();
        if (p == null) return false;
        Method v = method(p, "apiVersion");
        Method c = method(p, "apiCatalog");
        if (v == null || c == null) {
            if (!warned) {
                warned = true;
                pl.getLogger().warning("Cosmetics is gevonden, maar is te oud voor BeneluxPvP. Gebruik Cosmetics 1.4.0 of nieuwer.");
            }
            return false;
        }
        try {
            return ((Number) v.invoke(p)).intValue() >= 1;
        } catch (Throwable t) {
            return false;
        }
    }

    String problem() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Cosmetics");
        if (p == null) return "De plugin Cosmetics is niet geïnstalleerd.";
        if (!p.isEnabled()) return "De plugin Cosmetics staat uit.";
        return ready() ? null : "Je Cosmetics is te oud. Gebruik versie 1.4.0 of nieuwer.";
    }

    @SuppressWarnings("unchecked")
    List<Row> catalog() {
        List<Row> out = new ArrayList<>();
        if (!ready()) return out;
        try {
            Plugin p = cos();
            for (String[] r : (List<String[]>) method(p, "apiCatalog").invoke(p)) {
                if (r == null || r.length < 6) continue;
                Material m = Material.matchMaterial(r[4]);
                out.add(new Row(r[0], r[1], r[2], r[3], m == null ? Material.PAPER : m, r[5]));
            }
        } catch (Throwable t) {
            fail("catalogus", t);
        }
        return out;
    }

    Row find(String key) {
        if (key == null) return null;
        for (Row r : catalog()) if (r.key().equalsIgnoreCase(key.trim())) return r;
        return null;
    }

    /** Het plaatje van een cosmetic zoals deze speler het ziet (3D-model met resourcepack). Valt terug op het gewone materiaal. */
    ItemStack icon(Player viewer, Row r) {
        try {
            if (viewer != null && ready()) {
                Plugin p = cos();
                Method m = method(p, "apiIcon", Player.class, String.class);
                if (m != null) {
                    Object o = m.invoke(p, viewer, r.key());
                    if (o instanceof ItemStack is && !is.getType().isAir()) return is;
                }
            }
        } catch (Throwable t) {
            // een plaatje is niet belangrijk: gewoon het materiaal
        }
        return new ItemStack(r.icon());
    }

    boolean owns(UUID id, String key) {
        if (!ready()) return false;
        try {
            Plugin p = cos();
            return (boolean) method(p, "apiOwns", UUID.class, String.class).invoke(p, id, key);
        } catch (Throwable t) {
            fail("apiOwns", t);
            return false;
        }
    }

    /** Geeft de cosmetic. True alleen als de speler hem er echt bij kreeg. */
    boolean give(UUID id, String name, String key) {
        if (!ready()) return false;
        try {
            Plugin p = cos();
            return (boolean) method(p, "apiGive", UUID.class, String.class, String.class).invoke(p, id, name, key);
        } catch (Throwable t) {
            fail("apiGive", t);
            return false;
        }
    }

    private void fail(String what, Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        pl.getLogger().warning("Koppeling met Cosmetics (" + what + ") mislukt: " + c);
    }
}
