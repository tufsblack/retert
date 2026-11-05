package nl.beneluxpvp;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Eén lopende wedstrijd tussen twee spelers. */
final class Match {
    enum State { COUNTDOWN, FIGHT, ENDING }

    final int id;
    final ArenaDef arena;
    final KitDef kit;
    final UUID a, b;
    final String nameA, nameB;
    State state = State.COUNTDOWN;
    int countdown;
    int elapsed;
    int ending;
    int limit;
    /** Winnaar (null bij gelijkspel of zolang de wedstrijd loopt). */
    UUID winner;
    boolean declared;
    final Set<UUID> spectators = new LinkedHashSet<>();
    /** Wie al terug is gezet (afgemeld of klaar). */
    final Set<UUID> restored = new LinkedHashSet<>();

    Match(int id, ArenaDef arena, KitDef kit, UUID a, String nameA, UUID b, String nameB) {
        this.id = id;
        this.arena = arena;
        this.kit = kit;
        this.a = a;
        this.b = b;
        this.nameA = nameA;
        this.nameB = nameB;
    }

    boolean has(UUID id) {
        return a.equals(id) || b.equals(id);
    }

    UUID other(UUID id) {
        return a.equals(id) ? b : a;
    }

    String nameOf(UUID id) {
        return a.equals(id) ? nameA : nameB;
    }
}
