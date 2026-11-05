package nl.beneluxpvp;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Bewaart de staat van spelers (inventaris, gamemode, plek, ...) VOORDAT ze een arena in gaan, in een eigen bestand per speler.
 * Zo krijgt iedereen zijn spullen terug, ook na een crash, afmelden of herstart.
 */
final class States {
    private final BeneluxPvP pl;
    private final File dir;
    private final Map<UUID, PState> map = new HashMap<>();

    States(BeneluxPvP pl, File dir) {
        this.pl = pl;
        this.dir = dir;
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    boolean has(UUID id) {
        return map.containsKey(id) || file(id).isFile();
    }

    /** Legt de huidige staat vast en schrijft hem weg. Geeft false als het wegschrijven mislukt (dan mag de speler NIET de arena in). */
    boolean capture(Player p, boolean withInv) {
        PState s = new PState(p.getUniqueId());
        s.hasInv = withInv;
        s.mode = p.getGameMode().name();
        s.loc = Loc.of(p.getLocation());
        s.allowFlight = p.getAllowFlight();
        if (withInv) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < KitDef.SLOTS; i++) {
                ItemStack it = inv.getItem(i);
                s.items[i] = it == null || it.getType().isAir() || pl.items().isMenuItem(it) ? null : it.clone();
            }
            s.health = p.getHealth();
            s.food = p.getFoodLevel();
            s.saturation = p.getSaturation();
            s.level = p.getLevel();
            s.exp = p.getExp();
        }
        if (!write(s)) return false;
        map.put(s.id, s);
        return true;
    }

    private boolean write(PState s) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("inv", s.hasInv);
        y.set("modus", s.mode);
        y.set("gezondheid", s.health);
        y.set("eten", s.food);
        y.set("verzadiging", (double) s.saturation);
        y.set("level", s.level);
        y.set("exp", (double) s.exp);
        y.set("vliegen", s.allowFlight);
        y.set("plek", s.loc == null ? "" : s.loc.serialize());
        y.set("klaar", s.invDone);
        List<String> raw = new ArrayList<>();
        for (ItemStack it : s.items) raw.add(it == null ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        y.set("items", raw);
        return Store.writeAtomic(file(s.id), y.saveToString(), pl.getLogger());
    }

    private PState read(UUID id) {
        PState s = map.get(id);
        if (s != null) return s;
        File f = file(id);
        if (!f.isFile()) return null;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            s = new PState(id);
            s.hasInv = y.getBoolean("inv", true);
            s.mode = y.getString("modus", "SURVIVAL");
            s.health = y.getDouble("gezondheid", 20);
            s.food = y.getInt("eten", 20);
            s.saturation = (float) y.getDouble("verzadiging", 5);
            s.level = y.getInt("level", 0);
            s.exp = (float) y.getDouble("exp", 0);
            s.allowFlight = y.getBoolean("vliegen", false);
            s.loc = Loc.parse(y.getString("plek", ""));
            s.invDone = y.getBoolean("klaar", false);
            List<String> raw = y.getStringList("items");
            for (int i = 0; i < KitDef.SLOTS && i < raw.size(); i++) {
                String r = raw.get(i);
                if (r == null || r.isEmpty()) continue;
                try {
                    s.items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(r));
                } catch (RuntimeException ex) {
                    pl.getLogger().warning("Een bewaard item van " + id + " is onleesbaar en is overgeslagen.");
                }
            }
            map.put(id, s);
            return s;
        } catch (Exception e) {
            pl.getLogger().severe("Bewaarde staat van " + id + " is kapot (bestand blijft staan voor handmatig herstel): " + e);
            return null;
        }
    }

    PState get(UUID id) {
        return read(id);
    }

    /** Zet een speler klaar voor een wedstrijd: schoon, vol leven, overleven. */
    void prepare(Player p) {
        clearEffects(p);
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setLevel(0);
        p.setExp(0f);
        setVitals(p, 20, 20, pl.settings().num("wedstrijd.verzadiging"));
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    private static void setVitals(Player p, double health, int food, float sat) {
        try {
            p.setHealth(Math.max(1.0, Math.min(20.0, health)));
        } catch (IllegalArgumentException ignored) {
            // een andere maximale gezondheid: laat staan
        }
        p.setFoodLevel(Math.max(0, Math.min(20, food)));
        p.setSaturation(Math.max(0f, Math.min(20f, sat)));
    }

    static void clearEffects(Player p) {
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    private static GameMode mode(String s) {
        try {
            return GameMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    /** Zet alles terug en verwijdert het bestand. Alleen aanroepen voor een levende speler. Geeft false als er niets te herstellen was. */
    boolean restore(Player p) {
        PState s = read(p.getUniqueId());
        if (s == null) return false;
        applyAll(p, s, true);
        forget(s.id);
        return true;
    }

    private void applyAll(Player p, PState s, boolean teleport) {
        clearEffects(p);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        if (teleport) teleportBack(p, s);
        p.setGameMode(mode(s.mode));
        if (!s.invDone && s.hasInv) {
            PlayerInventory inv = p.getInventory();
            inv.clear();
            for (int i = 0; i < KitDef.SLOTS; i++) if (s.items[i] != null) inv.setItem(i, s.items[i].clone());
            p.setLevel(s.level);
            p.setExp(Math.max(0f, Math.min(1f, s.exp)));
            setVitals(p, s.health, s.food, s.saturation);
        }
        p.setAllowFlight(s.allowFlight || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
        p.updateInventory();
    }

    private boolean teleportBack(Player p, PState s) {
        Location to = s.loc == null ? null : s.loc.toLocation();
        if (to == null && pl.arenas().lobby != null) to = pl.arenas().lobby.toLocation();
        if (to == null) {
            pl.getLogger().warning("Geen plek om " + p.getName() + " naartoe te sturen (wereld weg en geen lobby ingesteld).");
            return false;
        }
        return p.teleport(to);
    }

    /** Afmelden midden in een wedstrijd: alles terugzetten, maar het bestand blijft staan voor de plek bij het volgende aanmelden. */
    void restoreOnQuit(Player p) {
        PState s = read(p.getUniqueId());
        if (s == null) return;
        applyAll(p, s, true);
        s.invDone = true;
        s.hasInv = false;
        if (!write(s)) pl.getLogger().warning("Kon de plek van " + p.getName() + " niet bewaren voor bij het aanmelden.");
    }

    /** Bij aanmelden: is er nog iets te herstellen (crash of afgemeld)? Geeft true als er iets gedaan is. */
    boolean restoreOnJoin(Player p) {
        PState s = read(p.getUniqueId());
        if (s == null) return false;
        applyAll(p, s, true);
        forget(s.id);
        return true;
    }

    void forget(UUID id) {
        map.remove(id);
        File f = file(id);
        if (f.isFile() && !f.delete()) pl.getLogger().warning("Kon " + f.getName() + " niet verwijderen.");
    }

    /** Alleen uit het geheugen halen (bestand blijft), bij het afsluiten. */
    void dropMemory() {
        map.clear();
    }
}
