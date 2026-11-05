package nl.beneluxpvp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/** Kills, deaths en ontgrendelde beloningen per speler. Opgeslagen als eenvoudig tekstbestand (spelers.tsv). */
final class Stats {
    enum Type {
        KILLS("Kills"), DEATHS("Deaths"), KD("K/D"), STREAK("Beste reeks");
        final String label;

        Type(String label) {
            this.label = label;
        }
    }

    static final int MAX = 1_000_000;

    static final class P {
        final UUID id;
        String name;
        int kills, deaths, streak, best, draws;
        boolean noChallenges;
        final Set<String> unlocked = new LinkedHashSet<>();

        P(UUID id, String name) {
            this.id = id;
            this.name = name == null ? "?" : name;
        }

        int games() {
            return kills + deaths;
        }

        double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        String kdText() {
            return Items.kd(kills, deaths);
        }

        boolean empty() {
            return kills == 0 && deaths == 0 && best == 0 && draws == 0 && !noChallenges && unlocked.isEmpty();
        }
    }

    private final Map<UUID, P> map = new LinkedHashMap<>();
    private final File file;
    private final Logger log;
    boolean dirty;

    Stats(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    P get(UUID id) {
        return map.get(id);
    }

    P getOrCreate(UUID id, String name) {
        P p = map.get(id);
        if (p == null) {
            p = new P(id, name);
            map.put(id, p);
            dirty = true;
        } else if (name != null && !name.isBlank() && !name.equals(p.name)) {
            p.name = name;
            dirty = true;
        }
        return p;
    }

    P findByName(String name) {
        if (name == null) return null;
        for (P p : map.values()) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    int size() {
        return map.size();
    }

    List<P> all() {
        return new ArrayList<>(map.values());
    }

    private static double score(P p, Type t) {
        return switch (t) {
            case KILLS -> p.kills;
            case DEATHS -> p.deaths;
            case KD -> p.kd();
            case STREAK -> p.best;
        };
    }

    private boolean eligible(P p, Type t, int minGames) {
        if (score(p, t) <= 0) return false;
        return t != Type.KD || p.games() >= minGames;
    }

    /** De ranglijst voor een soort. {@code minGames} geldt alleen voor K/D. */
    List<P> top(Type t, int n, int minGames) {
        List<P> all = new ArrayList<>();
        for (P p : map.values()) if (eligible(p, t, minGames)) all.add(p);
        all.sort((a, b) -> {
            int c = Double.compare(score(b, t), score(a, t));
            if (c != 0) return c;
            if (a.kills != b.kills) return Integer.compare(b.kills, a.kills);
            return a.name.compareToIgnoreCase(b.name);
        });
        return all.size() > n ? new ArrayList<>(all.subList(0, n)) : all;
    }

    /** Plek in de ranglijst (1 = bovenaan), of 0 als de speler er (nog) niet in staat. */
    int rank(UUID id, Type t, int minGames) {
        P me = map.get(id);
        if (me == null || !eligible(me, t, minGames)) return 0;
        int better = 0;
        double mine = score(me, t);
        for (P p : map.values()) if (eligible(p, t, minGames) && score(p, t) > mine) better++;
        return better + 1;
    }

    int unlockedCount(String key) {
        int n = 0;
        for (P p : map.values()) if (p.unlocked.contains(key)) n++;
        return n;
    }

    /** Zet alle statistieken (kills, deaths, reeksen) op 0. Wat ontgrendeld is blijft. */
    void resetAllNumbers() {
        for (P p : map.values()) {
            p.kills = 0;
            p.deaths = 0;
            p.streak = 0;
            p.best = 0;
            p.draws = 0;
        }
        dirty = true;
    }

    void load() {
        map.clear();
        if (!file.isFile()) return;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                if (f.length < 9) continue;
                try {
                    UUID id = UUID.fromString(f[0].trim());
                    P p = new P(id, f[1]);
                    p.kills = clamp(Integer.parseInt(f[2].trim()));
                    p.deaths = clamp(Integer.parseInt(f[3].trim()));
                    p.streak = clamp(Integer.parseInt(f[4].trim()));
                    p.best = clamp(Integer.parseInt(f[5].trim()));
                    p.draws = clamp(Integer.parseInt(f[6].trim()));
                    p.noChallenges = f[7].trim().equals("1");
                    for (String k : f[8].split(",")) if (!k.isBlank()) p.unlocked.add(k.trim().toLowerCase(Locale.ROOT));
                    map.put(id, p);
                } catch (IllegalArgumentException ex) {
                    log.warning("Regel in spelers.tsv overgeslagen: " + line);
                }
            }
        } catch (IOException e) {
            log.severe("spelers.tsv kon niet gelezen worden: " + e);
        }
        dirty = false;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    boolean save() {
        StringBuilder sb = new StringBuilder("# uuid<TAB>naam<TAB>kills<TAB>deaths<TAB>reeks<TAB>beste reeks<TAB>gelijk<TAB>geen uitdagingen<TAB>ontgrendeld\n");
        for (P p : map.values()) {
            if (p.empty()) continue;
            sb.append(p.id).append('\t').append(p.name.replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(p.kills).append('\t').append(p.deaths).append('\t').append(p.streak).append('\t').append(p.best).append('\t')
                    .append(p.draws).append('\t').append(p.noChallenges ? 1 : 0).append('\t').append(String.join(",", p.unlocked)).append('\n');
        }
        if (Store.writeAtomic(file, sb.toString(), log)) {
            dirty = false;
            return true;
        }
        return false;
    }
}
