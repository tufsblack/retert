package nl.beneluxpvp;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Cosmetics die je krijgt als je genoeg wedstrijden wint ("kills"). De eisen stelt staff in. */
final class Rewards {
    /** Een cosmetic met zijn eis. */
    record Goal(Bridge.Row row, int need) {
    }

    private final BeneluxPvP pl;

    Rewards(BeneluxPvP pl) {
        this.pl = pl;
    }

    /** Alle beloningen met een eis (alleen cosmetics die Cosmetics kent). Gesorteerd op eis. */
    List<Goal> goals() {
        List<Goal> out = new ArrayList<>();
        for (Bridge.Row r : pl.bridge().catalog()) {
            int n = pl.store().need(r.key());
            if (n > 0) out.add(new Goal(r, n));
        }
        out.sort((a, b) -> a.need != b.need ? Integer.compare(a.need, b.need) : a.row.name().compareToIgnoreCase(b.row.name()));
        return out;
    }

    /** Het eerstvolgende doel dat de speler nog niet heeft (laagste eis). Null als er niets meer te halen is. */
    Goal nextGoal(Player p) {
        Stats.P s = pl.stats().getOrCreate(p.getUniqueId(), p.getName());
        for (Goal g : goals()) {
            if (s.kills < g.need && !pl.bridge().owns(p.getUniqueId(), g.row.key())) return g;
        }
        return null;
    }

    /** Deelt alle beloningen uit waar de speler genoeg kills voor heeft. Veilig om vaak aan te roepen. */
    void evaluate(Player p) {
        if (p == null || !p.isOnline() || !pl.bridge().ready()) return;
        Stats.P s = pl.stats().getOrCreate(p.getUniqueId(), p.getName());
        for (Goal g : goals()) {
            String key = g.row.key();
            if (s.kills < g.need || s.unlocked.contains(key)) continue;
            if (pl.bridge().owns(p.getUniqueId(), key)) {
                s.unlocked.add(key); // had hem al (van staff of een rang): niets uit te delen
                pl.stats().dirty = true;
                continue;
            }
            if (pl.bridge().give(p.getUniqueId(), p.getName(), key)) {
                s.unlocked.add(key);
                pl.stats().dirty = true;
                announce(p, g);
            }
        }
        if (pl.stats().dirty) pl.stats().save();
    }

    void evaluateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) evaluate(p);
        pl.refreshOpenMenus();
    }

    void evaluateAllSoon() {
        Bukkit.getScheduler().runTask(pl, this::evaluateAll);
    }

    private void announce(Player p, Goal g) {
        String name = Items.esc(g.row.name());
        String kills = String.valueOf(g.need);
        pl.say(p, pl.msg("ontgrendeld-chat", "<green>Ontgrendeld! <gray>Je kreeg <white>%cosmetic%<gray> voor <gold>%kills% overwinningen<gray>. Open <white>/cosmetics<gray> om hem te dragen.")
                .replace("%cosmetic%", name).replace("%kills%", kills));
        try {
            p.showTitle(Title.title(
                    pl.parse(pl.msg("ontgrendeld-titel", "<dark_red>⚔ <gold><bold>ONTGRENDELD <dark_red>⚔")),
                    pl.parse(pl.msg("ontgrendeld-ondertitel", "<white>%cosmetic%").replace("%cosmetic%", name).replace("%kills%", kills))));
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        } catch (RuntimeException ignored) {
            // een titel is leuk maar niet belangrijk
        }
    }
}
