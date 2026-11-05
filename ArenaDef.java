package nl.beneluxpvp;

import org.bukkit.Material;

import java.util.LinkedHashSet;
import java.util.Set;

/** Een arena: een gebied, twee startplekken en een kijkplek. */
final class ArenaDef {
    enum State { IDLE, BUSY, RESETTING, CAPTURING }

    final String id;
    String name;
    String world;
    int[] p1, p2;
    Loc s1, s2, spectator;
    boolean enabled = true;
    boolean build = false;
    /** Tijdslimiet in seconden; 0 = de standaard uit de instellingen. */
    int limit = 0;
    Material icon = Material.GRASS_BLOCK;
    final Set<String> kits = new LinkedHashSet<>();
    State state = State.IDLE;

    ArenaDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    Region region() {
        if (p1 == null || p2 == null || world == null) return null;
        return new Region(id, world, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
    }

    boolean allows(String kitId) {
        return kits.isEmpty() || kits.contains(kitId);
    }
}
