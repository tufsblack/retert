package nl.beneluxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Alle kits (kits.yml). */
final class Kits {
    static final int MAX = 100;

    private final BeneluxPvP pl;
    private final File file;
    private final Map<String, KitDef> map = new LinkedHashMap<>();

    Kits(BeneluxPvP pl, File file) {
        this.pl = pl;
        this.file = file;
    }

    void load() {
        map.clear();
        if (!file.isFile()) return;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            for (String id : y.getStringList("kits")) {
                String b = "k." + id + ".";
                KitDef k = new KitDef(id, y.getString(b + "naam", id));
                Material m = Material.matchMaterial(y.getString(b + "icoon", "IRON_SWORD"));
                if (m != null && !m.isAir()) k.icon = m;
                k.enabled = y.getBoolean(b + "aan", true);
                List<String> raw = y.getStringList(b + "items");
                for (int i = 0; i < KitDef.SLOTS && i < raw.size(); i++) {
                    String s = raw.get(i);
                    if (s == null || s.isEmpty()) continue;
                    try {
                        k.items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
                    } catch (RuntimeException ex) {
                        pl.getLogger().warning("Item " + i + " van kit '" + id + "' is onleesbaar en wordt overgeslagen.");
                    }
                }
                map.put(id, k);
            }
        } catch (Exception e) {
            pl.getLogger().severe("kits.yml is kapot en wordt niet geladen (kopie bewaard): " + e);
            Store.backupBroken(file);
        }
    }

    boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("kits", new ArrayList<>(map.keySet()));
        for (KitDef k : map.values()) {
            String b = "k." + k.id + ".";
            y.set(b + "naam", k.name);
            y.set(b + "icoon", k.icon.name());
            y.set(b + "aan", k.enabled);
            List<String> raw = new ArrayList<>();
            for (ItemStack it : k.items) raw.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            y.set(b + "items", raw);
        }
        return Store.writeAtomic(file, y.saveToString(), pl.getLogger());
    }

    KitDef get(String id) {
        return id == null ? null : map.get(id);
    }

    List<KitDef> all() {
        return new ArrayList<>(map.values());
    }

    List<KitDef> enabled() {
        List<KitDef> out = new ArrayList<>();
        for (KitDef k : map.values()) if (k.enabled && k.itemCount() > 0) out.add(k);
        return out;
    }

    /** Maakt een kit van de inventaris van de speler. Geeft null als het niet kan (fout in {@code err[0]}). */
    KitDef create(String rawName, Player from, String[] err) {
        String name = rawName == null ? "" : rawName.trim();
        String id = Items.slug(name);
        if (id.isEmpty()) {
            err[0] = "Gebruik letters of cijfers in de naam.";
            return null;
        }
        if (map.containsKey(id)) {
            err[0] = "Er is al een kit met die naam.";
            return null;
        }
        if (map.size() >= MAX) {
            err[0] = "Er kunnen maximaal " + MAX + " kits zijn.";
            return null;
        }
        KitDef k = new KitDef(id, name.length() > 32 ? name.substring(0, 32) : name);
        k.setItems(snapshot(from));
        if (k.itemCount() == 0) {
            err[0] = "Je inventaris is leeg. Leg eerst de spullen in je inventaris die de kit moet bevatten.";
            return null;
        }
        map.put(id, k);
        if (!save()) {
            map.remove(id);
            err[0] = "Opslaan is mislukt, kijk in de console.";
            return null;
        }
        return k;
    }

    boolean delete(String id) {
        KitDef k = map.remove(id);
        if (k == null) return false;
        if (!save()) {
            map.put(id, k);
            return false;
        }
        pl.arenas().forgetKit(id);
        return true;
    }

    ItemStack[] snapshot(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] out = new ItemStack[KitDef.SLOTS];
        for (int i = 0; i < KitDef.SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            out[i] = it == null || it.getType().isAir() || pl.items().isMenuItem(it) ? null : it.clone();
        }
        return out;
    }

    /** Geeft de kit aan een speler; de inventaris wordt eerst helemaal leeggemaakt. Menu-items worden nooit meegegeven. */
    void apply(KitDef k, Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (int i = 0; i < KitDef.SLOTS; i++) {
            ItemStack it = k.items[i];
            if (it == null || pl.items().isMenuItem(it)) continue;
            inv.setItem(i, it.clone());
        }
    }
}
