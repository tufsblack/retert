package nl.beneluxpvp;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/** Alle lopende wedstrijden: starten, volgen, beëindigen en iedereen netjes terugzetten. */
final class Matches {
    private final BeneluxPvP pl;
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final Map<UUID, Match> watching = new HashMap<>();
    private final List<Match> list = new ArrayList<>();
    private final Set<UUID> restoreOnRespawn = new HashSet<>();
    private int nextId = 1;

    Matches(BeneluxPvP pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------ opvragen

    Match of(UUID id) {
        return byPlayer.get(id);
    }

    Match watchedBy(UUID id) {
        return watching.get(id);
    }

    boolean inMatch(UUID id) {
        return byPlayer.containsKey(id);
    }

    boolean isWatching(UUID id) {
        return watching.containsKey(id);
    }

    boolean waitingForRespawn(UUID id) {
        return restoreOnRespawn.contains(id);
    }

    List<Match> all() {
        return new ArrayList<>(list);
    }

    Match get(int id) {
        for (Match m : list) if (m.id == id) return m;
        return null;
    }

    // ------------------------------------------------------------ starten

    /** Start een wedstrijd. Geeft een foutmelding of null als het gelukt is. */
    String start(Player p1, Player p2, KitDef kit, ArenaDef arena) {
        if (p1 == null || p2 == null || !p1.isOnline() || !p2.isOnline()) return "Een van de spelers is niet meer online.";
        UUID i1 = p1.getUniqueId(), i2 = p2.getUniqueId();
        if (i1.equals(i2)) return "Je kunt niet tegen jezelf spelen.";
        if (inMatch(i1) || inMatch(i2)) return "Een van de spelers zit al in een wedstrijd.";
        if (kit == null || !kit.enabled || kit.itemCount() == 0) return "Deze kit is niet beschikbaar.";
        if (arena == null || !pl.arenas().playable(arena)) return "Deze arena is nu niet beschikbaar.";
        if (!arena.allows(kit.id)) return "Deze kit mag niet in deze arena.";
        Location l1 = arena.s1 == null ? null : arena.s1.toLocation(), l2 = arena.s2 == null ? null : arena.s2.toLocation();
        if (l1 == null || l2 == null) return "De wereld van deze arena is niet geladen.";
        stopWatching(p1);
        stopWatching(p2);
        if (pl.states().has(i1) || pl.states().has(i2)) return "Een van de spelers wordt nog teruggezet. Probeer het zo nog eens.";
        if (!pl.states().capture(p1, true)) return "Je spullen konden niet veilig bewaard worden. Er is niets gestart.";
        if (!pl.states().capture(p2, true)) {
            pl.states().forget(i1);
            return "Je spullen konden niet veilig bewaard worden. Er is niets gestart.";
        }
        pl.queue().remove(i1);
        pl.queue().remove(i2);
        pl.requests().clearFor(i1);
        pl.requests().clearFor(i2);

        Match m = new Match(nextId++, arena, kit, i1, p1.getName(), i2, p2.getName());
        m.countdown = pl.settings().num("wedstrijd.aftellen");
        m.state = m.countdown > 0 ? Match.State.COUNTDOWN : Match.State.FIGHT;
        m.limit = arena.limit > 0 ? arena.limit : pl.settings().num("wedstrijd.max-seconden");
        pl.arenas().markBusy(arena);
        list.add(m);
        byPlayer.put(i1, m);
        byPlayer.put(i2, m);

        boolean ok = true;
        for (int k = 0; k < 2; k++) {
            Player p = k == 0 ? p1 : p2;
            pl.states().prepare(p);
            if (!p.teleport(k == 0 ? l1 : l2)) ok = false;
            pl.kits().apply(kit, p);
            p.updateInventory();
        }
        if (!ok) {
            pl.say(p1, "<red>Teleporteren naar de arena mislukte. De wedstrijd is geannuleerd.");
            pl.say(p2, "<red>Teleporteren naar de arena mislukte. De wedstrijd is geannuleerd.");
            m.declared = true;
            finish(m);
            return null;
        }
        String intro = "<gold>⚔ <white>" + Items.esc(p1.getName()) + " <red>vs <white>" + Items.esc(p2.getName())
                + " <dark_gray>• <gray>Kit <white>" + Items.esc(kit.name) + " <dark_gray>• <gray>Arena <white>" + Items.esc(arena.name);
        pl.say(p1, intro);
        pl.say(p2, intro);
        if (m.state == Match.State.FIGHT) announceGo(m);
        pl.refreshOpenMenus();
        return null;
    }

    // ------------------------------------------------------------ elke seconde

    void tick() {
        for (Match m : new ArrayList<>(list)) {
            try {
                tickOne(m);
            } catch (RuntimeException e) {
                pl.getLogger().severe("Fout in wedstrijd " + m.id + ": " + e);
                m.declared = true;
                finish(m);
            }
        }
    }

    private void tickOne(Match m) {
        Player pa = Bukkit.getPlayer(m.a), pb = Bukkit.getPlayer(m.b);
        switch (m.state) {
            case COUNTDOWN -> {
                if (m.countdown > 0) {
                    for (Player p : new Player[]{pa, pb}) {
                        if (p == null) continue;
                        title(p, "<gold><bold>" + m.countdown, "<gray>Maak je klaar...", 0, 25, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1f);
                    }
                    m.countdown--;
                } else {
                    m.state = Match.State.FIGHT;
                    announceGo(m);
                }
            }
            case FIGHT -> {
                m.elapsed++;
                if (m.limit > 0 && m.elapsed >= m.limit) {
                    declareDraw(m, "de tijd is om");
                    return;
                }
                String left = m.limit > 0 ? " <dark_gray>• <gray>nog <white>" + Items.time(Math.max(0, m.limit - m.elapsed)) : "";
                if (pa != null) pa.sendActionBar(pl.parse("<red>⚔ <white>vs " + Items.esc(m.nameB) + left));
                if (pb != null) pb.sendActionBar(pl.parse("<red>⚔ <white>vs " + Items.esc(m.nameA) + left));
            }
            case ENDING -> {
                m.ending--;
                if (m.ending <= 0) finish(m);
            }
        }
    }

    private void announceGo(Match m) {
        for (UUID id : new UUID[]{m.a, m.b}) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            title(p, "<red><bold>VECHT!", "<gray>Succes!", 0, 20, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.3f);
        }
    }

    void title(Player p, String main, String sub, int in, int stay, int out) {
        try {
            p.showTitle(Title.title(pl.parse(main), pl.parse(sub), Title.Times.times(
                    java.time.Duration.ofMillis(in * 50L), java.time.Duration.ofMillis(stay * 50L), java.time.Duration.ofMillis(out * 50L))));
        } catch (RuntimeException ignored) {
            // titels zijn niet belangrijk
        }
    }

    // ------------------------------------------------------------ einde van een wedstrijd

    /** Dodelijke klap voor een deelnemer. Geeft true als wij het afhandelen (de klap moet dan geannuleerd worden). */
    boolean lethal(Player victim) {
        Match m = byPlayer.get(victim.getUniqueId());
        if (m == null || m.declared || m.state != Match.State.FIGHT) return false;
        Player winner = Bukkit.getPlayer(m.other(victim.getUniqueId()));
        if (winner == null) return false;
        eliminate(m, victim);
        declareWin(m, winner, victim, false);
        return true;
    }

    /** De deelnemer is toch echt doodgegaan (bijvoorbeeld door een andere plugin). */
    void died(Player victim) {
        Match m = byPlayer.get(victim.getUniqueId());
        if (m == null || m.declared) return;
        Player winner = Bukkit.getPlayer(m.other(victim.getUniqueId()));
        if (winner == null) {
            declareDraw(m, "de tegenstander is weg");
            return;
        }
        declareWin(m, winner, victim, false);
    }

    /** Een verliezer wordt toeschouwer in de arena tot het einde. */
    private void eliminate(Match m, Player loser) {
        try {
            loser.setHealth(20.0);
        } catch (IllegalArgumentException ignored) {
            // andere maximale gezondheid
        }
        loser.setFireTicks(0);
        loser.setFallDistance(0f);
        States.clearEffects(loser);
        loser.setGameMode(GameMode.SPECTATOR);
        Location spec = m.arena.spectator == null ? null : m.arena.spectator.toLocation();
        if (spec != null) loser.teleport(spec);
    }

    /** Iemand geeft op (/pvp leave). Geeft false als de speler niet in een wedstrijd zit. */
    boolean forfeit(Player p) {
        Match m = byPlayer.get(p.getUniqueId());
        if (m == null) return false;
        if (m.declared) return true;
        Player winner = Bukkit.getPlayer(m.other(p.getUniqueId()));
        eliminate(m, p);
        if (winner == null) declareDraw(m, "de tegenstander is weg");
        else declareWin(m, winner, p, false);
        return true;
    }

    private void declareWin(Match m, Player winner, Player loser, boolean loserQuit) {
        if (m.declared) return;
        m.declared = true;
        m.state = Match.State.ENDING;
        m.ending = pl.settings().num("wedstrijd.einde-vertraging");
        m.winner = winner.getUniqueId();
        Rules.Verdict v = pl.rules().check(winner, loser);
        pl.rules().died(loser.getUniqueId());
        Stats.P w = pl.stats().getOrCreate(winner.getUniqueId(), winner.getName());
        Stats.P l = pl.stats().getOrCreate(loser.getUniqueId(), loser.getName());
        if (v == Rules.Verdict.OK) {
            pl.rules().record(winner, loser);
            w.kills = Stats.clamp(w.kills + 1);
            w.streak = Stats.clamp(w.streak + 1);
            if (w.streak > w.best) w.best = w.streak;
            l.deaths = Stats.clamp(l.deaths + 1);
            l.streak = 0;
            pl.stats().dirty = true;
            pl.stats().save();
            pl.rewards().evaluate(winner);
        }
        String dur = Items.time(m.elapsed);
        String line = "<gold>🏆 <white>" + Items.esc(winner.getName()) + " <gray>won van <white>" + Items.esc(loser.getName())
                + " <dark_gray>(" + Items.esc(m.kit.name) + " • " + Items.esc(m.arena.name) + " • " + dur + ")";
        for (Player p : audience(m)) pl.say(p, line);
        title(winner, "<gold><bold>OVERWINNING", "<gray>Goed gedaan!", 0, 50, 10);
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        if (!loserQuit) {
            title(loser, "<dark_red><bold>VERLOREN", "<gray>Volgende keer beter", 0, 50, 10);
            loser.playSound(loser.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.3f, 1.4f);
        }
        if (v == Rules.Verdict.OK) {
            winner.sendActionBar(pl.parse("<gold>+1 overwinning <dark_gray>• <gray>totaal <white>" + w.kills + " <dark_gray>• <gray>reeks <white>" + w.streak
                    + nextGoalText(winner, w)));
        } else if (v.tellsWinner()) {
            pl.say(winner, "<red>Deze wedstrijd telt niet mee voor de statistieken: <white>" + why(v));
        }
        pl.refreshOpenMenus();
    }

    private String nextGoalText(Player p, Stats.P s) {
        Rewards.Goal g = pl.rewards().nextGoal(p);
        return g == null ? "" : " <dark_gray>• <gray>nog <white>" + (g.need() - s.kills) + "<gray> voor <white>" + Items.esc(g.row().name());
    }

    static String why(Rules.Verdict v) {
        return switch (v) {
            case SAME_VICTIM -> "je hebt deze speler net al verslagen";
            case SAME_IP -> "dit is dezelfde verbinding als jij";
            case HOURLY_CAP -> "je hebt dit uur het maximum bereikt";
            case VICTIM_NEW -> "deze speler is net pas aangemeld";
            case VICTIM_RECENT_DEATH -> "deze speler verloor net al";
            default -> "";
        };
    }

    private void declareDraw(Match m, String why) {
        if (m.declared) return;
        m.declared = true;
        m.state = Match.State.ENDING;
        m.ending = pl.settings().num("wedstrijd.einde-vertraging");
        m.winner = null;
        for (UUID id : new UUID[]{m.a, m.b}) {
            Stats.P s = pl.stats().getOrCreate(id, m.nameOf(id));
            s.draws = Stats.clamp(s.draws + 1);
        }
        pl.stats().dirty = true;
        for (Player p : audience(m)) {
            pl.say(p, "<yellow>⚖ <gray>Gelijkspel tussen <white>" + Items.esc(m.nameA) + " <gray>en <white>" + Items.esc(m.nameB) + " <dark_gray>(" + why + ")");
            if (m.has(p.getUniqueId())) title(p, "<yellow><bold>GELIJKSPEL", "<gray>" + why, 0, 50, 10);
        }
        pl.refreshOpenMenus();
    }

    private List<Player> audience(Match m) {
        List<Player> out = new ArrayList<>();
        for (UUID id : new UUID[]{m.a, m.b}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !m.restored.contains(id)) out.add(p);
        }
        for (UUID id : m.spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Staff beëindigt een wedstrijd (gelijkspel). */
    void forceEnd(Match m) {
        if (!m.declared) declareDraw(m, "beëindigd door staff");
        m.ending = Math.min(m.ending, 1);
    }

    /** Alles opruimen: iedereen terugzetten en de arena laten herstellen. */
    void finish(Match m) {
        if (!list.remove(m)) return;
        for (UUID id : new UUID[]{m.a, m.b}) {
            if (byPlayer.get(id) == m) byPlayer.remove(id);
            if (m.restored.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        for (UUID id : new ArrayList<>(m.spectators)) {
            watching.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        m.spectators.clear();
        pl.arenas().requestReset(m.arena, pl.getConfig().getInt("vertraging-ticks", 40));
        pl.refreshOpenMenus();
    }

    private void restore(Player p) {
        if (p.isDead()) {
            restoreOnRespawn.add(p.getUniqueId());
            return;
        }
        pl.states().restore(p);
    }

    // ------------------------------------------------------------ afmelden, doodgaan, opnieuw verschijnen

    void quit(Player p) {
        UUID id = p.getUniqueId();
        restoreOnRespawn.remove(id);
        Match m = byPlayer.get(id);
        if (m != null) {
            byPlayer.remove(id);
            m.restored.add(id);
            if (!m.declared) {
                Player w = Bukkit.getPlayer(m.other(id));
                if (w != null && !m.restored.contains(w.getUniqueId())) declareWin(m, w, p, true);
                else declareDraw(m, "beide spelers zijn weg");
            }
            if (!p.isDead()) pl.states().restoreOnQuit(p);
            return;
        }
        Match wch = watching.remove(id);
        if (wch != null) {
            wch.spectators.remove(id);
            if (!p.isDead()) pl.states().restoreOnQuit(p);
        }
    }

    /** Waar moet een speler heen na het doodgaan? Null = niets bijzonders. */
    Location respawn(Player p) {
        UUID id = p.getUniqueId();
        if (restoreOnRespawn.remove(id)) {
            PState s = pl.states().get(id);
            Location to = s == null || s.loc == null ? null : s.loc.toLocation();
            if (to == null && pl.arenas().lobby != null) to = pl.arenas().lobby.toLocation();
            Bukkit.getScheduler().runTask(pl, () -> {
                Player pp = Bukkit.getPlayer(id);
                if (pp != null && pp.isOnline()) pl.states().restore(pp);
            });
            return to;
        }
        Match m = byPlayer.get(id);
        if (m != null) {
            Location to = m.arena.spectator == null ? null : m.arena.spectator.toLocation();
            Bukkit.getScheduler().runTask(pl, () -> {
                Player pp = Bukkit.getPlayer(id);
                if (pp != null && pp.isOnline() && byPlayer.get(id) == m) pp.setGameMode(GameMode.SPECTATOR);
            });
            return to;
        }
        return null;
    }

    // ------------------------------------------------------------ kijken

    String watch(Player p, Match m) {
        if (!pl.settings().bool("kijken.aan")) return "Kijken staat uit.";
        UUID id = p.getUniqueId();
        if (inMatch(id)) return "Je zit zelf in een wedstrijd.";
        if (restoreOnRespawn.contains(id)) return "Je wordt nog teruggezet. Probeer het zo nog eens.";
        Location spec = m.arena.spectator == null ? null : m.arena.spectator.toLocation();
        if (spec == null) return "Deze arena heeft geen kijkplek.";
        Match cur = watching.get(id);
        if (cur == null) {
            if (pl.states().has(id)) return "Je wordt nog teruggezet. Probeer het zo nog eens.";
            if (!pl.states().capture(p, false)) return "Je plek kon niet bewaard worden.";
            pl.queue().remove(id);
        } else {
            cur.spectators.remove(id);
        }
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(spec);
        watching.put(id, m);
        m.spectators.add(id);
        pl.say(p, "<gray>Je kijkt naar <white>" + Items.esc(m.nameA) + " <red>vs <white>" + Items.esc(m.nameB) + "<gray>. Stoppen: <white>/pvp leave");
        return null;
    }

    boolean stopWatching(Player p) {
        Match m = watching.remove(p.getUniqueId());
        if (m == null) return false;
        m.spectators.remove(p.getUniqueId());
        pl.states().restore(p);
        return true;
    }

    // ------------------------------------------------------------ afsluiten

    /** Plugin gaat uit: alle spelers meteen terugzetten. De arena's worden bij de volgende start hersteld (markeringen blijven staan). */
    void shutdown() {
        for (Match m : new ArrayList<>(list)) {
            for (UUID id : new UUID[]{m.a, m.b}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !m.restored.contains(id) && !p.isDead()) pl.states().restore(p);
            }
            for (UUID id : m.spectators) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !p.isDead()) pl.states().restore(p);
            }
        }
        list.clear();
        byPlayer.clear();
        watching.clear();
    }
}
