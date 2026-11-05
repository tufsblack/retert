package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/** De wachtrij: wie dezelfde kit (en dezelfde arena, of "willekeurig") kiest, wordt aan elkaar gekoppeld. */
final class Queue {
    record Entry(UUID id, String name, String kitId, String arenaId, long since) {
    }

    private final BeneluxPvP pl;
    private final List<Entry> entries = new ArrayList<>();
    private boolean running;

    Queue(BeneluxPvP pl) {
        this.pl = pl;
    }

    Entry entry(UUID id) {
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    List<Entry> all() {
        return new ArrayList<>(entries);
    }

    int size() {
        return entries.size();
    }

    int waitingFor(String kitId) {
        int n = 0;
        for (Entry e : entries) if (e.kitId.equals(kitId)) n++;
        return n;
    }

    /** Zet een speler in de wachtrij. {@code arena} = null betekent "willekeurige arena". Geeft een foutmelding of null. */
    String join(Player p, KitDef kit, ArenaDef arena) {
        UUID id = p.getUniqueId();
        if (!pl.settings().bool("wachtrij.aan")) return "De wachtrij staat uit.";
        if (pl.matches().inMatch(id)) return "Je zit al in een wedstrijd.";
        if (pl.matches().waitingForRespawn(id) || pl.states().has(id) && !pl.matches().isWatching(id)) return "Je wordt nog teruggezet. Probeer het zo nog eens.";
        if (entry(id) != null) return "Je staat al in de wachtrij. Verlaat hem eerst.";
        if (kit == null || !kit.enabled || kit.itemCount() == 0) return "Deze kit is niet beschikbaar.";
        if (arena != null) {
            if (!pl.arenas().usable(arena)) return "Deze arena is niet beschikbaar.";
            if (!arena.allows(kit.id)) return "Deze kit mag niet in deze arena.";
        } else if (availableArenas(kit).isEmpty()) {
            return "Er is geen arena beschikbaar voor deze kit.";
        }
        if (pl.matches().isWatching(id)) pl.matches().stopWatching(p);
        entries.add(new Entry(id, p.getName(), kit.id, arena == null ? null : arena.id, pl.now()));
        pl.say(p, "<green>Je staat in de wachtrij. <gray>Kit <white>" + Items.esc(kit.name) + " <dark_gray>• <gray>Arena <white>"
                + (arena == null ? "willekeurig" : Items.esc(arena.name)) + "<gray>. Verlaten: <white>/pvp leave");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        pl.refreshOpenMenus();
        tryMatch();
        return null;
    }

    /** Arena's waar deze kit in kan (aan en volledig ingesteld, bezet of niet). */
    List<ArenaDef> availableArenas(KitDef kit) {
        List<ArenaDef> out = new ArrayList<>();
        for (ArenaDef a : pl.arenas().all()) if (pl.arenas().usable(a) && a.allows(kit.id)) out.add(a);
        return out;
    }

    boolean remove(UUID id) {
        boolean r = entries.removeIf(e -> e.id.equals(id));
        if (r) pl.refreshOpenMenus();
        return r;
    }

    /** Probeert wachtenden aan elkaar te koppelen. */
    void tryMatch() {
        if (running) return;
        running = true;
        try {
            boolean again = true;
            int guard = 0;
            while (again && guard++ < 50) {
                again = false;
                outer:
                for (int i = 0; i < entries.size(); i++) {
                    Entry a = entries.get(i);
                    for (int j = i + 1; j < entries.size(); j++) {
                        Entry b = entries.get(j);
                        if (!a.kitId.equals(b.kitId)) continue;
                        if (a.arenaId != null && b.arenaId != null && !a.arenaId.equals(b.arenaId)) continue;
                        KitDef kit = pl.kits().get(a.kitId);
                        ArenaDef arena = pick(a, b, kit);
                        if (arena == null || kit == null) continue;
                        Player pa = Bukkit.getPlayer(a.id), pb = Bukkit.getPlayer(b.id);
                        entries.remove(b);
                        entries.remove(a);
                        if (pa == null || pb == null) {
                            again = true;
                            break outer;
                        }
                        String err = pl.matches().start(pa, pb, kit, arena);
                        if (err != null) {
                            pl.say(pa, "<red>De wedstrijd kon niet starten: " + err);
                            pl.say(pb, "<red>De wedstrijd kon niet starten: " + err);
                        }
                        again = true;
                        break outer;
                    }
                }
            }
        } finally {
            running = false;
        }
    }

    private ArenaDef pick(Entry a, Entry b, KitDef kit) {
        if (kit == null || !kit.enabled) return null;
        String wanted = a.arenaId != null ? a.arenaId : b.arenaId;
        if (wanted != null) {
            ArenaDef x = pl.arenas().get(wanted);
            return x != null && pl.arenas().playable(x) && x.allows(kit.id) ? x : null;
        }
        List<ArenaDef> free = new ArrayList<>();
        for (ArenaDef x : pl.arenas().all()) if (pl.arenas().playable(x) && x.allows(kit.id)) free.add(x);
        return free.isEmpty() ? null : free.get(pl.random().nextInt(free.size()));
    }

    /** Elke seconde: verlopen en tonen hoe lang je al wacht. */
    void tick() {
        int max = pl.settings().num("wachtrij.max-seconden");
        long now = pl.now();
        boolean changed = false;
        for (Entry e : new ArrayList<>(entries)) {
            Player p = Bukkit.getPlayer(e.id);
            if (p == null) {
                entries.remove(e);
                changed = true;
                continue;
            }
            long secs = Math.max(0, (now - e.since) / 1000);
            if (max > 0 && secs >= max) {
                entries.remove(e);
                changed = true;
                pl.say(p, "<yellow>Je hebt te lang gewacht en bent uit de wachtrij gehaald. <gray>Probeer het later nog eens.");
                continue;
            }
            KitDef k = pl.kits().get(e.kitId);
            ArenaDef a = e.arenaId == null ? null : pl.arenas().get(e.arenaId);
            if (k == null || !k.enabled || (e.arenaId != null && (a == null || !pl.arenas().usable(a)))) {
                entries.remove(e);
                changed = true;
                pl.say(p, "<yellow>Je keuze is niet meer beschikbaar, je bent uit de wachtrij gehaald.");
                continue;
            }
            p.sendActionBar(pl.parse("<gold>⏳ Wachtrij <dark_gray>• <white>" + Items.esc(k.name) + " <dark_gray>• <white>"
                    + (a == null ? "willekeurig" : Items.esc(a.name)) + " <dark_gray>• <gray>" + Items.time((int) secs)));
        }
        if (changed) pl.refreshOpenMenus();
        tryMatch();
    }
}
