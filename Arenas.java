package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/** Alle arena's, het opslaan van het "origineel" en het herstellen na een wedstrijd. */
final class Arenas {
    static final int MAX = 50;

    private final BeneluxPvP pl;
    private final File file;
    private final Map<String, ArenaDef> map = new LinkedHashMap<>();
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Map<String, Integer> waited = new HashMap<>();
    private Job current;
    Loc lobby;

    Arenas(BeneluxPvP pl, File file) {
        this.pl = pl;
        this.file = file;
    }

    Logger getLogger() {
        return pl.getLogger();
    }

    // ------------------------------------------------------------ opslaan en laden

    File snapFile(String id) {
        return new File(pl.getDataFolder(), "snapshots/" + id + ".snap");
    }

    private File pendingFile(String id) {
        return new File(pl.getDataFolder(), "pending/" + id);
    }

    private static int[] ints(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(int[] a) {
        return a == null ? "" : a[0] + ";" + a[1] + ";" + a[2];
    }

    void load() {
        map.clear();
        lobby = null;
        if (!file.isFile()) return;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            lobby = Loc.parse(y.getString("lobby", ""));
            for (String id : y.getStringList("arenas")) {
                String b = "a." + id + ".";
                ArenaDef a = new ArenaDef(id, y.getString(b + "naam", id));
                String w = y.getString(b + "wereld", "");
                a.world = w.isEmpty() ? null : w;
                a.p1 = ints(y.getString(b + "hoek1", ""));
                a.p2 = ints(y.getString(b + "hoek2", ""));
                a.s1 = Loc.parse(y.getString(b + "start1", ""));
                a.s2 = Loc.parse(y.getString(b + "start2", ""));
                a.spectator = Loc.parse(y.getString(b + "kijkplek", ""));
                a.enabled = y.getBoolean(b + "aan", true);
                a.build = y.getBoolean(b + "bouwen", false);
                a.limit = Math.max(0, Math.min(3600, y.getInt(b + "limiet", 0)));
                Material m = Material.matchMaterial(y.getString(b + "icoon", "GRASS_BLOCK"));
                if (m != null && !m.isAir()) a.icon = m;
                for (String k : y.getStringList(b + "kits")) a.kits.add(k);
                map.put(id, a);
            }
        } catch (Exception e) {
            pl.getLogger().severe("arenas.yml is kapot en wordt niet geladen (kopie bewaard): " + e);
            Store.backupBroken(file);
        }
    }

    boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("lobby", lobby == null ? "" : lobby.serialize());
        y.set("arenas", new ArrayList<>(map.keySet()));
        for (ArenaDef a : map.values()) {
            String b = "a." + a.id + ".";
            y.set(b + "naam", a.name);
            y.set(b + "wereld", a.world == null ? "" : a.world);
            y.set(b + "hoek1", str(a.p1));
            y.set(b + "hoek2", str(a.p2));
            y.set(b + "start1", a.s1 == null ? "" : a.s1.serialize());
            y.set(b + "start2", a.s2 == null ? "" : a.s2.serialize());
            y.set(b + "kijkplek", a.spectator == null ? "" : a.spectator.serialize());
            y.set(b + "aan", a.enabled);
            y.set(b + "bouwen", a.build);
            y.set(b + "limiet", a.limit);
            y.set(b + "icoon", a.icon.name());
            y.set(b + "kits", new ArrayList<>(a.kits));
        }
        return Store.writeAtomic(file, y.saveToString(), pl.getLogger());
    }

    // ------------------------------------------------------------ opvragen

    ArenaDef get(String id) {
        return id == null ? null : map.get(id);
    }

    List<ArenaDef> all() {
        return new ArrayList<>(map.values());
    }

    boolean hasSnapshot(ArenaDef a) {
        return snapFile(a.id).isFile();
    }

    /** Wat er nog ontbreekt om de arena te kunnen gebruiken. Leeg = klaar. */
    List<String> problems(ArenaDef a) {
        List<String> out = new ArrayList<>();
        if (a.p1 == null || a.p2 == null) out.add("Hoeken van het gebied zijn nog niet gezet");
        if (a.s1 == null) out.add("Startplek 1 ontbreekt");
        if (a.s2 == null) out.add("Startplek 2 ontbreekt");
        if (a.spectator == null) out.add("Kijkplek ontbreekt");
        if (a.p1 != null && a.p2 != null && !hasSnapshot(a)) out.add("Origineel is nog niet opgeslagen");
        if (a.world != null && Bukkit.getWorld(a.world) == null) out.add("Wereld is niet geladen");
        return out;
    }

    /** Kan er nu een wedstrijd starten? */
    boolean playable(ArenaDef a) {
        return a.enabled && a.state == ArenaDef.State.IDLE && problems(a).isEmpty();
    }

    /** Is de arena in te stellen voor spelers (aan en volledig ingesteld), ook als hij nu bezet is? */
    boolean usable(ArenaDef a) {
        return a.enabled && problems(a).isEmpty();
    }

    ArenaDef arenaAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        String w = l.getWorld().getName();
        for (ArenaDef a : map.values()) {
            Region r = a.region();
            if (r != null && r.contains(w, l.getX(), l.getY(), l.getZ())) return a;
        }
        return null;
    }

    void forgetKit(String kitId) {
        boolean changed = false;
        for (ArenaDef a : map.values()) changed |= a.kits.remove(kitId);
        if (changed) save();
    }

    // ------------------------------------------------------------ maken en instellen

    ArenaDef create(String rawName, String[] err) {
        String name = rawName == null ? "" : rawName.trim();
        String id = Items.slug(name);
        if (id.isEmpty()) {
            err[0] = "Gebruik letters of cijfers in de naam.";
            return null;
        }
        if (map.containsKey(id)) {
            err[0] = "Er is al een arena met die naam.";
            return null;
        }
        if (map.size() >= MAX) {
            err[0] = "Er kunnen maximaal " + MAX + " arena's zijn.";
            return null;
        }
        ArenaDef a = new ArenaDef(id, name.length() > 32 ? name.substring(0, 32) : name);
        map.put(id, a);
        if (!save()) {
            map.remove(id);
            err[0] = "Opslaan is mislukt, kijk in de console.";
            return null;
        }
        return a;
    }

    /** Verwijdert een arena. Geeft een foutmelding of null. */
    String delete(ArenaDef a) {
        if (a.state != ArenaDef.State.IDLE) return "Deze arena is nu bezig.";
        map.remove(a.id);
        if (!save()) {
            map.put(a.id, a);
            return "Opslaan is mislukt, kijk in de console.";
        }
        snapFile(a.id).delete();
        pendingFile(a.id).delete();
        return null;
    }

    /** Zet een hoek van het gebied. Een nieuw gebied maakt het oude origineel ongeldig. Geeft een foutmelding of null. */
    String setCorner(ArenaDef a, boolean first, Location l) {
        if (a.state != ArenaDef.State.IDLE) return "Deze arena is nu bezig.";
        if (l.getWorld() == null) return "Onbekende wereld.";
        String w = l.getWorld().getName();
        World world = l.getWorld();
        int y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, l.getBlockY()));
        int[] c = {l.getBlockX(), y, l.getBlockZ()};
        String oldWorld = a.world;
        int[] o1 = a.p1, o2 = a.p2;
        if (a.world != null && !a.world.equals(w)) {
            // andere wereld: het hele gebied begint opnieuw
            a.p1 = null;
            a.p2 = null;
            a.s1 = null;
            a.s2 = null;
            a.spectator = null;
        }
        a.world = w;
        if (first) a.p1 = c;
        else a.p2 = c;
        Region r = a.region();
        long max = pl.getConfig().getInt("max-blokken", 3_000_000);
        if (r != null && r.volume() > max) {
            a.world = oldWorld;
            a.p1 = o1;
            a.p2 = o2;
            return "Dit gebied is te groot (" + r.volume() + " blokken, maximum " + max + ").";
        }
        snapFile(a.id).delete();
        if (!save()) return "Opslaan is mislukt, kijk in de console.";
        return null;
    }

    /** Zet een startplek of de kijkplek. kind: 1, 2 of 3 (kijkplek). Moet binnen het gebied liggen als dat al gezet is. */
    String setSpot(ArenaDef a, int kind, Location l) {
        if (a.state != ArenaDef.State.IDLE) return "Deze arena is nu bezig.";
        if (l.getWorld() == null) return "Onbekende wereld.";
        if (a.world == null || a.region() == null) return "Zet eerst de twee hoeken van het gebied.";
        Region r = a.region();
        if (!r.contains(l.getWorld().getName(), l.getX(), l.getY(), l.getZ())) return "Deze plek ligt buiten het gebied van de arena.";
        Loc v = Loc.of(l);
        if (kind == 1) a.s1 = v;
        else if (kind == 2) a.s2 = v;
        else a.spectator = v;
        if (!save()) return "Opslaan is mislukt, kijk in de console.";
        return null;
    }

    // ------------------------------------------------------------ origineel opslaan

    /** Start het opslaan van het origineel. Geeft een foutmelding of null. */
    String capture(ArenaDef a) {
        if (a.state != ArenaDef.State.IDLE) return "Deze arena is nu bezig.";
        Region r = a.region();
        if (r == null) return "Zet eerst de twee hoeken van het gebied.";
        World w = Bukkit.getWorld(r.world);
        if (w == null) return "De wereld van deze arena is niet geladen.";
        for (ArenaDef o : map.values()) {
            if (o == a) continue;
            Region q = o.region();
            if (q != null && q.world.equals(r.world) && r.x1 <= q.x2 && r.x2 >= q.x1 && r.y1 <= q.y2 && r.y2 >= q.y1 && r.z1 <= q.z2 && r.z2 >= q.z1)
                return "Dit gebied overlapt met arena '" + o.name + "'. Arena's mogen elkaar niet raken.";
        }
        a.state = ArenaDef.State.CAPTURING;
        jobs.add(new CaptureJob(this, r, w));
        return null;
    }

    void captureDone(Region r, boolean ok, long ms, int tiles) {
        ArenaDef a = map.get(r.name);
        if (a != null && a.state == ArenaDef.State.CAPTURING) a.state = ArenaDef.State.IDLE;
        if (ok) pl.getLogger().info("Arena '" + r.name + "' opgeslagen (" + r.volume() + " blokken, " + tiles + " kist(en)) in " + ms + " ms.");
        else pl.getLogger().severe("Opslaan van arena '" + r.name + "' is mislukt.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(BeneluxPvP.ADMIN))
                pl.say(p, ok ? "<green>Arena <white>" + Items.esc(a == null ? r.name : a.name) + "<green> is opgeslagen. <gray>Dit is nu het 'origineel'."
                        : "<red>Opslaan van arena <white>" + Items.esc(r.name) + "<red> is mislukt, kijk in de console.");
        }
        pl.refreshOpenMenus();
        pl.queue().tryMatch();
    }

    // ------------------------------------------------------------ herstellen

    /** Een wedstrijd start: de arena is vanaf nu "vies". Het bestandje zorgt dat een crash geen vieze arena achterlaat. */
    void markBusy(ArenaDef a) {
        a.state = ArenaDef.State.BUSY;
        try {
            File f = pendingFile(a.id);
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), "x");
        } catch (Exception e) {
            pl.getLogger().warning("Kon herstel-markering niet schrijven: " + e);
        }
    }

    /** De wedstrijd is klaar: zet het herstel in de rij. */
    void requestReset(ArenaDef a, int delayTicks) {
        a.state = ArenaDef.State.RESETTING;
        waited.put(a.id, 0);
        Bukkit.getScheduler().runTaskLater(pl, () -> readyToRestore(a), Math.max(1, delayTicks));
    }

    private void readyToRestore(ArenaDef a) {
        if (map.get(a.id) != a) return;
        Region r = a.region();
        World w = r == null ? null : Bukkit.getWorld(r.world);
        if (r == null || w == null || !hasSnapshot(a)) {
            pl.getLogger().warning("Herstel van '" + a.id + "' kan niet: gebied, wereld of opslag ontbreekt.");
            finishReset(a);
            return;
        }
        int w0 = waited.getOrDefault(a.id, 0);
        if (w0 < pl.getConfig().getInt("max-wachten-op-spelers-ticks", 600) && playersIn(r, w)) {
            waited.put(a.id, w0 + 20);
            Bukkit.getScheduler().runTaskLater(pl, () -> readyToRestore(a), 20L);
            return;
        }
        Snap s;
        try {
            s = Snap.read(snapFile(a.id), r.volume());
        } catch (Exception e) {
            pl.getLogger().severe("Opslag van '" + a.id + "' kan niet gelezen worden: " + e);
            finishReset(a);
            return;
        }
        cleanEntities(r);
        jobs.add(new RestoreJob(this, r, w, s));
    }

    private boolean playersIn(Region r, World w) {
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (r.contains(r.world, l.getX(), l.getY(), l.getZ())) return true;
        }
        return false;
    }

    void restoreDone(Region r, boolean ok, RestoreJob job, long ms) {
        ArenaDef a = map.get(r.name);
        if (ok) {
            cleanEntities(r);
            pendingFile(r.name).delete();
            pl.getLogger().info("Arena '" + r.name + "' hersteld: " + job.changed + " blokken en " + job.tilesFixed + " kist(en) aangepast in " + ms + " ms."
                    + (job.skipped > 0 ? " (" + job.skipped + " overgeslagen)" : ""));
        } else {
            pl.getLogger().severe("Herstel van '" + r.name + "' is mislukt; het wordt bij de volgende serverstart opnieuw geprobeerd.");
        }
        if (a != null) finishReset(a);
    }

    private void finishReset(ArenaDef a) {
        if (a.state == ArenaDef.State.RESETTING) a.state = ArenaDef.State.IDLE;
        waited.remove(a.id);
        pl.refreshOpenMenus();
        pl.queue().tryMatch();
    }

    /** Herstel op verzoek van staff. Geeft een foutmelding of null. */
    String manualReset(ArenaDef a) {
        if (a.state != ArenaDef.State.IDLE) return "Deze arena is nu bezig.";
        if (!hasSnapshot(a) || a.region() == null) return "Er is nog geen origineel opgeslagen.";
        markBusy(a);
        requestReset(a, 1);
        return null;
    }

    /** Na een herstart: onafgemaakte herstelwerken afmaken. */
    void recover() {
        File[] files = new File(pl.getDataFolder(), "pending").listFiles();
        if (files == null) return;
        for (File f : files) {
            ArenaDef a = map.get(f.getName());
            if (a == null || !hasSnapshot(a)) {
                f.delete();
                continue;
            }
            pl.getLogger().info("Onafgemaakt herstel van '" + a.id + "' wordt nu afgemaakt.");
            a.state = ArenaDef.State.RESETTING;
            waited.put(a.id, 0);
            Bukkit.getScheduler().runTaskLater(pl, () -> readyToRestore(a), 20L);
        }
    }

    /** Verwijdert pijlen, items enz. uit het gebied. */
    void cleanEntities(Region r) {
        World w = Bukkit.getWorld(r.world);
        if (w == null) return;
        Set<String> kinds = new HashSet<>();
        for (String s : pl.getConfig().getStringList("verwijder-entiteiten")) kinds.add(s.trim().toUpperCase(Locale.ROOT));
        if (kinds.isEmpty()) return;
        Location c = new Location(w, (r.x1 + r.x2 + 1) / 2.0, (r.y1 + r.y2 + 1) / 2.0, (r.z1 + r.z2 + 1) / 2.0);
        for (Entity e : w.getNearbyEntities(c, r.sx() / 2.0 + 1, r.sy() / 2.0 + 1, r.sz() / 2.0 + 1)) {
            if (e instanceof Player) continue;
            Location l = e.getLocation();
            if (!r.contains(r.world, l.getX(), l.getY(), l.getZ())) continue;
            if (kinds.contains(String.valueOf(e.getType()).toUpperCase(Locale.ROOT))) e.remove();
        }
    }

    // ------------------------------------------------------------ werk in kleine stapjes

    void tick() {
        if (current == null) {
            current = jobs.poll();
            if (current == null) return;
        }
        long ms = Math.max(1, Math.min(45, pl.getConfig().getInt("ms-per-tick", 20)));
        long deadline = System.nanoTime() + ms * 1_000_000L;
        boolean done;
        try {
            done = current.step(deadline);
        } catch (Throwable t) {
            pl.getLogger().severe("Fout bij arena '" + current.name() + "': " + t);
            Job j = current;
            current = null;
            j.finish(false);
            return;
        }
        if (done) {
            Job j = current;
            current = null;
            j.finish(true);
        }
    }

    boolean busy() {
        return current != null || !jobs.isEmpty();
    }
}
