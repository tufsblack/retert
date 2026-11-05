package nl.beneluxpvp;

import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.LongSupplier;

/** Bepaalt of een gewonnen wedstrijd meetelt voor de statistieken. Hier zitten alle maatregelen tegen farmen. */
final class Rules {
    enum Verdict {
        OK, SELF, OFF, SAME_VICTIM, SAME_IP, HOURLY_CAP, VICTIM_NEW, VICTIM_RECENT_DEATH;

        /** Krijgt de winnaar hier een melding van? */
        boolean tellsWinner() {
            return this != OK && this != SELF && this != OFF;
        }
    }

    private final BeneluxPvP pl;
    private final LongSupplier clock;
    private final Map<UUID, Map<UUID, Long>> pair = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> hourly = new HashMap<>();
    private final Map<UUID, Long> joined = new HashMap<>();
    private final Map<UUID, Long> died = new HashMap<>();

    Rules(BeneluxPvP pl, LongSupplier clock) {
        this.pl = pl;
        this.clock = clock;
    }

    void joined(UUID id) {
        joined.put(id, clock.getAsLong());
    }

    void died(UUID id) {
        died.put(id, clock.getAsLong());
    }

    void forget(UUID id) {
        pair.remove(id);
        hourly.remove(id);
        joined.remove(id);
        died.remove(id);
    }

    private static String ip(Player p) {
        try {
            InetSocketAddress a = p.getAddress();
            return a == null || a.getAddress() == null ? null : a.getAddress().getHostAddress();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Controleert of deze winst meetelt. Verandert niets; roep {@link #record} aan als hij telt. Roep dit aan VOORDAT {@link #died} de nederlaag vastlegt. */
    Verdict check(Player winner, Player loser) {
        if (winner == null || loser == null) return Verdict.SELF;
        if (winner.getUniqueId().equals(loser.getUniqueId())) return Verdict.SELF;
        if (!pl.settings().bool("tellen.aan")) return Verdict.OFF;
        long now = clock.getAsLong();
        if (pl.settings().bool("tellen.zelfde-ip-telt-niet")) {
            String a = ip(winner), b = ip(loser);
            if (a != null && a.equals(b)) return Verdict.SAME_IP;
        }
        int cool = pl.settings().num("tellen.zelfde-slachtoffer-seconden");
        if (cool > 0) {
            Map<UUID, Long> m = pair.get(winner.getUniqueId());
            Long last = m == null ? null : m.get(loser.getUniqueId());
            if (last != null && now - last < cool * 1000L) return Verdict.SAME_VICTIM;
        }
        int minOnline = pl.settings().num("tellen.speler-min-online-seconden");
        Long j = joined.get(loser.getUniqueId());
        if (j != null && minOnline > 0 && now - j < minOnline * 1000L) return Verdict.VICTIM_NEW;
        int afterDeath = pl.settings().num("tellen.speler-na-dood-seconden");
        Long d = died.get(loser.getUniqueId());
        if (d != null && afterDeath > 0 && now - d < afterDeath * 1000L) return Verdict.VICTIM_RECENT_DEATH;
        int cap = pl.settings().num("tellen.max-per-uur");
        if (cap > 0) {
            ArrayDeque<Long> q = hourly.get(winner.getUniqueId());
            if (q != null) {
                while (!q.isEmpty() && now - q.peekFirst() >= 3_600_000L) q.pollFirst();
                if (q.size() >= cap) return Verdict.HOURLY_CAP;
            }
        }
        return Verdict.OK;
    }

    void record(Player winner, Player loser) {
        long now = clock.getAsLong();
        Map<UUID, Long> m = pair.computeIfAbsent(winner.getUniqueId(), k -> new HashMap<>());
        m.put(loser.getUniqueId(), now);
        long maxAge = Math.max(1, pl.settings().num("tellen.zelfde-slachtoffer-seconden")) * 1000L;
        m.values().removeIf(t -> now - t >= maxAge);
        hourly.computeIfAbsent(winner.getUniqueId(), k -> new ArrayDeque<>()).addLast(now);
    }
}
