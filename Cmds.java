package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** Alle commando's: /pvp, /duel, /pvpstats, /pvptop, /kills en /pvpadmin. */
final class Cmds implements CommandExecutor, TabCompleter {
    private final BeneluxPvP pl;

    Cmds(BeneluxPvP pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        try {
            switch (c.getName().toLowerCase(Locale.ROOT)) {
                case "pvp" -> pvp(s, a);
                case "duel" -> duel(s, a);
                case "pvpstats" -> pvpstats(s, a);
                case "pvptop" -> pvptop(s, a);
                case "kills" -> kills(s, a);
                case "pvpadmin" -> admin(s, a);
                default -> {
                    return false;
                }
            }
        } catch (RuntimeException e) {
            pl.getLogger().warning("Fout in commando /" + c.getName() + ": " + e);
            pl.say(s, "<red>Er ging iets mis. Kijk in de console.");
        }
        return true;
    }

    private boolean canUse(CommandSender s) {
        if (s.hasPermission(BeneluxPvP.USE) || s.hasPermission(BeneluxPvP.ADMIN)) return true;
        pl.say(s, "<red>Je hebt hier geen toestemming voor.");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        pl.say(s, "<red>Dit kan alleen in het spel.");
        return null;
    }

    private KitDef findKit(String s) {
        if (s == null) return null;
        String id = Items.slug(s);
        KitDef k = pl.kits().get(id);
        if (k != null) return k;
        for (KitDef o : pl.kits().all()) if (o.name.equalsIgnoreCase(s.trim())) return o;
        return null;
    }

    private ArenaDef findArena(String s) {
        if (s == null) return null;
        ArenaDef a = pl.arenas().get(Items.slug(s));
        if (a != null) return a;
        for (ArenaDef o : pl.arenas().all()) if (o.name.equalsIgnoreCase(s.trim())) return o;
        return null;
    }

    private static boolean random(String s) {
        return s.equalsIgnoreCase("willekeurig") || s.equalsIgnoreCase("random") || s.equalsIgnoreCase("any") || s.equals("*");
    }

    // ------------------------------------------------------------ /pvp

    private void pvp(CommandSender s, String[] a) {
        if (!canUse(s)) return;
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                Player p = player(s);
                if (p != null) pl.playerGuis().hub(p);
            }
            case "queue", "wachtrij", "q" -> {
                Player p = player(s);
                if (p == null) return;
                if (pl.busy(p)) {
                    pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee. <gray>Stoppen: <white>/pvp leave");
                    return;
                }
                if (a.length == 1) {
                    pl.playerGuis().queueFlow(p);
                    return;
                }
                KitDef k = findKit(a[1]);
                if (k == null) {
                    pl.say(p, "<red>Onbekende kit. <gray>Gebruik <white>/pvp queue <kit> [arena]");
                    return;
                }
                ArenaDef ar = null;
                if (a.length >= 3 && !random(a[2])) {
                    ar = findArena(a[2]);
                    if (ar == null) {
                        pl.say(p, "<red>Onbekende arena.");
                        return;
                    }
                }
                String err = pl.queue().join(p, k, ar);
                if (err != null) pl.say(p, "<red>" + err);
            }
            case "leave", "stop", "verlaat" -> {
                Player p = player(s);
                if (p == null) return;
                if (pl.matches().isWatching(p.getUniqueId())) {
                    pl.matches().stopWatching(p);
                    pl.say(p, "<gray>Je kijkt niet meer mee.");
                } else if (pl.matches().inMatch(p.getUniqueId())) {
                    Match m = pl.matches().of(p.getUniqueId());
                    if (m != null && m.declared) pl.say(p, "<gray>Het gevecht is al afgelopen, je gaat zo terug.");
                    else {
                        pl.matches().forfeit(p);
                        pl.say(p, "<gray>Je hebt opgegeven.");
                    }
                } else if (pl.queue().remove(p.getUniqueId())) {
                    pl.say(p, "<gray>Je hebt de wachtrij verlaten.");
                } else {
                    pl.say(p, "<gray>Je zit nergens in.");
                }
            }
            case "accept", "accepteer" -> accept(s, a, true);
            case "deny", "weiger" -> accept(s, a, false);
            case "stats" -> pvpstats(s, Arrays.copyOfRange(a, 1, a.length));
            case "top" -> pvptop(s, Arrays.copyOfRange(a, 1, a.length));
            case "rewards", "beloningen" -> kills(s, new String[0]);
            case "spectate", "kijken", "watch" -> spectate(s, a);
            default -> help(s);
        }
    }

    private void help(CommandSender s) {
        pl.say(s, "<gray>Commando's:");
        for (String l : List.of(
                "<white>/pvp <dark_gray>- <gray>het hoofdmenu",
                "<white>/pvp queue [kit] [arena] <dark_gray>- <gray>de wachtrij in",
                "<white>/pvp leave <dark_gray>- <gray>wachtrij verlaten of opgeven",
                "<white>/duel <speler> [kit] [arena] <dark_gray>- <gray>uitdagen",
                "<white>/duel accept|deny <speler> <dark_gray>- <gray>antwoorden",
                "<white>/pvpstats [speler] <dark_gray>- <gray>statistieken",
                "<white>/pvptop [kills|deaths|kd|streak] <dark_gray>- <gray>ranglijst",
                "<white>/pvp spectate [speler] <dark_gray>- <gray>meekijken",
                "<white>/kills <dark_gray>- <gray>beloningen"))
            s.sendMessage(pl.parse(" " + l));
    }

    private void accept(CommandSender s, String[] a, boolean yes) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) {
            pl.say(p, "<gray>Gebruik: <white>/duel " + (yes ? "accept" : "deny") + " <speler>");
            return;
        }
        Player from = Bukkit.getPlayerExact(a[1]);
        UUID fromId = from != null ? from.getUniqueId() : null;
        if (fromId == null) {
            for (Requests.Req r : pl.requests().incoming(p.getUniqueId())) if (r.fromName().equalsIgnoreCase(a[1])) fromId = r.from();
        }
        if (fromId == null) {
            pl.say(p, "<red>Van die speler heb je geen uitdaging.");
            return;
        }
        if (yes) {
            if (pl.busy(p)) {
                pl.say(p, "<red>Je zit al in een wedstrijd of kijkt mee.");
                return;
            }
            String err = pl.requests().accept(p, fromId);
            if (err != null) pl.say(p, "<red>" + err);
        } else if (pl.requests().deny(p, fromId)) pl.say(p, "<gray>Uitdaging geweigerd.");
        else pl.say(p, "<red>Van die speler heb je geen uitdaging.");
    }

    private void spectate(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) {
            pl.playerGuis().spectate(p);
            return;
        }
        for (Match m : pl.matches().all()) {
            if (m.nameA.equalsIgnoreCase(a[1]) || m.nameB.equalsIgnoreCase(a[1])) {
                String err = pl.matches().watch(p, m);
                if (err != null) pl.say(p, "<red>" + err);
                return;
            }
        }
        pl.say(p, "<red>Die speler is niet in een gevecht.");
    }

    // ------------------------------------------------------------ /duel

    private void duel(CommandSender s, String[] a) {
        if (!canUse(s)) return;
        Player p = player(s);
        if (p == null) return;
        if (a.length == 0) {
            if (pl.busy(p)) pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee.");
            else pl.playerGuis().challengeFlow(p);
            return;
        }
        String first = a[0].toLowerCase(Locale.ROOT);
        if (first.equals("accept") || first.equals("accepteer")) {
            accept(s, a, true);
            return;
        }
        if (first.equals("deny") || first.equals("weiger")) {
            accept(s, a, false);
            return;
        }
        if (pl.busy(p)) {
            pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee.");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            pl.say(p, "<red>Die speler is niet online.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            pl.say(p, "<red>Je kunt jezelf niet uitdagen.");
            return;
        }
        if (a.length == 1) {
            pl.playerGuis().challengeFlow(p, target.getUniqueId());
            return;
        }
        KitDef k = findKit(a[1]);
        if (k == null) {
            pl.say(p, "<red>Onbekende kit.");
            return;
        }
        ArenaDef ar = null;
        if (a.length >= 3 && !random(a[2])) {
            ar = findArena(a[2]);
            if (ar == null) {
                pl.say(p, "<red>Onbekende arena.");
                return;
            }
        }
        String err = pl.requests().send(p, target, k, ar);
        if (err != null) pl.say(p, "<red>" + err);
    }

    // ------------------------------------------------------------ statistieken

    private void pvpstats(CommandSender s, String[] a) {
        if (!canUse(s)) return;
        Stats.P target = null;
        if (a.length >= 1) {
            target = pl.stats().findByName(a[0]);
            if (target == null) {
                pl.say(s, "<red>Die speler ken ik niet.");
                return;
            }
        }
        if (s instanceof Player p) {
            if (pl.busy(p)) {
                pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee.");
                return;
            }
            UUID id = target == null ? p.getUniqueId() : target.id;
            pl.stats().getOrCreate(p.getUniqueId(), p.getName());
            pl.playerGuis().stats(p, id, () -> pl.playerGuis().hub(p));
        } else if (target != null) {
            pl.say(s, "<white>" + Items.esc(target.name) + "<gray>: <white>" + target.kills + " kills, " + target.deaths + " deaths, K/D " + target.kdText() + ", beste reeks " + target.best);
        } else {
            pl.say(s, "<gray>Gebruik: <white>/pvpstats <speler>");
        }
    }

    private static Stats.Type type(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "deaths", "death", "doden" -> Stats.Type.DEATHS;
            case "kd", "k/d", "ratio" -> Stats.Type.KD;
            case "streak", "reeks" -> Stats.Type.STREAK;
            default -> Stats.Type.KILLS;
        };
    }

    private void pvptop(CommandSender s, String[] a) {
        if (!canUse(s)) return;
        Stats.Type t = a.length >= 1 ? type(a[0]) : Stats.Type.KILLS;
        if (s instanceof Player p) {
            if (pl.busy(p)) {
                pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee.");
                return;
            }
            pl.playerGuis().leaderboard(p, t, () -> pl.playerGuis().hub(p));
            return;
        }
        List<Stats.P> top = pl.stats().top(t, 10, pl.settings().num("ranglijst.min-wedstrijden"));
        if (top.isEmpty()) {
            pl.say(s, "<gray>Er staat nog niemand in deze ranglijst.");
            return;
        }
        pl.say(s, "<gold>Top " + top.size() + " <gray>" + t.label + ":");
        int i = 1;
        for (Stats.P p : top) {
            String v = switch (t) {
                case KILLS -> String.valueOf(p.kills);
                case DEATHS -> String.valueOf(p.deaths);
                case KD -> p.kdText();
                case STREAK -> String.valueOf(p.best);
            };
            s.sendMessage(pl.parse("<dark_gray> " + i++ + ". <white>" + Items.esc(p.name) + " <dark_gray>- <gold>" + v));
        }
    }

    private void kills(CommandSender s, String[] a) {
        if (!canUse(s)) return;
        if (a.length >= 1) {
            Stats.P t = pl.stats().findByName(a[0]);
            if (t == null) pl.say(s, "<red>Die speler ken ik niet.");
            else pl.say(s, "<white>" + Items.esc(t.name) + "<gray> heeft <gold>" + t.kills + " kills<gray>.");
            return;
        }
        Player p = player(s);
        if (p == null) return;
        if (pl.busy(p)) {
            pl.say(p, "<red>Je zit in een wedstrijd of kijkt mee.");
            return;
        }
        pl.playerGuis().rewards(p, () -> pl.playerGuis().hub(p));
    }

    // ------------------------------------------------------------ /pvpadmin

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission(BeneluxPvP.ADMIN)) {
            pl.say(s, "<red>Je hebt hier geen toestemming voor.");
            return;
        }
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu" -> {
                Player p = player(s);
                if (p != null) pl.staffGuis().hub(p);
            }
            case "status" -> {
                String problem = pl.bridge().problem();
                pl.say(s, "<gray>Cosmetics-koppeling: " + (problem == null ? "<green>actief <dark_gray>(" + pl.bridge().catalog().size() + " cosmetics)" : "<red>" + problem));
                s.sendMessage(pl.parse("<gray> Kits: <white>" + pl.kits().all().size() + "<gray>, arena's: <white>" + pl.arenas().all().size()
                        + "<gray>, lopende gevechten: <white>" + pl.matches().all().size() + "<gray>, wachtrij: <white>" + pl.queue().size() + "<gray>, spelers: <white>" + pl.stats().size()));
                for (ArenaDef ar : pl.arenas().all()) {
                    List<String> pr = pl.arenas().problems(ar);
                    s.sendMessage(pl.parse("<dark_gray>  - <white>" + Items.esc(ar.name) + " <gray>" + ar.state.name().toLowerCase(Locale.ROOT)
                            + (pr.isEmpty() ? " <green>klaar" : " <red>" + Items.esc(String.join(", ", pr)))));
                }
            }
            case "reload" -> {
                pl.reloadConfig();
                pl.store().load();
                pl.rewards().evaluateAllSoon();
                pl.say(s, "<green>Instellingen opnieuw geladen.");
            }
            case "lobby" -> {
                Player p = player(s);
                if (p == null) return;
                pl.arenas().lobby = Loc.of(p.getLocation());
                if (pl.arenas().save()) pl.say(p, "<green>Lobby ingesteld op je huidige plek.");
                else pl.say(p, "<red>Opslaan mislukt, kijk in de console.");
            }
            case "eis" -> eis(s, a);
            case "zet", "set" -> setStat(s, a);
            case "reset" -> {
                if (a.length < 2) {
                    pl.say(s, "<gray>Gebruik: <white>/pvpadmin reset <speler>");
                    return;
                }
                Stats.P t = pl.stats().findByName(a[1]);
                if (t == null) {
                    pl.say(s, "<red>Die speler ken ik niet.");
                    return;
                }
                t.kills = 0;
                t.deaths = 0;
                t.streak = 0;
                t.best = 0;
                t.draws = 0;
                pl.stats().dirty = true;
                pl.stats().save();
                pl.refreshOpenMenus();
                pl.say(s, "<green>Statistieken van <white>" + Items.esc(t.name) + "<green> staan weer op 0.");
            }
            default -> {
                pl.say(s, "<gray>Staff-commando's:");
                for (String l : List.of("<white>/pvpadmin gui <dark_gray>- <gray>het staff-menu", "<white>/pvpadmin status", "<white>/pvpadmin reload",
                        "<white>/pvpadmin lobby <dark_gray>- <gray>lobby op jouw plek",
                        "<white>/pvpadmin eis <cosmetic> <aantal|uit> <dark_gray>- <gray>beloning instellen",
                        "<white>/pvpadmin zet <speler> <kills|deaths> <aantal>", "<white>/pvpadmin reset <speler>"))
                    s.sendMessage(pl.parse(" " + l));
            }
        }
    }

    private void eis(CommandSender s, String[] a) {
        if (a.length < 3) {
            pl.say(s, "<gray>Gebruik: <white>/pvpadmin eis <cosmetic, bv. hat:kroon> <aantal|uit>");
            return;
        }
        String problem = pl.bridge().problem();
        if (problem != null) {
            pl.say(s, "<red>" + problem);
            return;
        }
        Bridge.Row row = pl.bridge().find(a[1]);
        if (row == null) {
            List<Bridge.Row> hits = new ArrayList<>();
            for (Bridge.Row r : pl.bridge().catalog()) if (r.key().endsWith(":" + a[1].toLowerCase(Locale.ROOT))) hits.add(r);
            if (hits.size() == 1) row = hits.get(0);
            else if (hits.size() > 1) {
                pl.say(s, "<red>Die naam bestaat in meer soorten. <gray>Gebruik bv. <white>" + hits.get(0).key());
                return;
            }
        }
        if (row == null) {
            pl.say(s, "<red>Onbekende cosmetic. <gray>Tab-aanvullen laat alle sleutels zien.");
            return;
        }
        String v = a[2].toLowerCase(Locale.ROOT);
        if (v.equals("uit") || v.equals("0") || v.equals("weg")) {
            pl.store().setNeed(row.key(), 0);
            pl.store().save();
            pl.say(s, "<green>Geen eis meer voor <white>" + Items.esc(row.name()) + "<green>.");
        } else {
            int n;
            try {
                n = Integer.parseInt(v);
            } catch (NumberFormatException ex) {
                pl.say(s, "<red>Dat is geen getal.");
                return;
            }
            if (n < 1 || n > Store.MAX_NEED) {
                pl.say(s, "<red>Kies een getal van 1 tot " + Store.MAX_NEED + ".");
                return;
            }
            pl.store().setNeed(row.key(), n);
            pl.store().save();
            pl.say(s, "<green><white>" + Items.esc(row.name()) + "<green> kost nu <gold>" + n + " overwinningen<green>.");
        }
        pl.rewards().evaluateAllSoon();
    }

    private void setStat(CommandSender s, String[] a) {
        if (a.length < 4) {
            pl.say(s, "<gray>Gebruik: <white>/pvpadmin zet <speler> <kills|deaths> <aantal>");
            return;
        }
        Stats.P t = pl.stats().findByName(a[1]);
        if (t == null) {
            pl.say(s, "<red>Die speler ken ik niet. <gray>Hij moet minstens één keer gejoind zijn.");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(a[3]);
        } catch (NumberFormatException ex) {
            pl.say(s, "<red>Dat is geen getal.");
            return;
        }
        if (n < 0 || n > Stats.MAX) {
            pl.say(s, "<red>Kies een getal van 0 tot " + Stats.MAX + ".");
            return;
        }
        String what = a[2].toLowerCase(Locale.ROOT);
        if (what.equals("kills")) t.kills = n;
        else if (what.equals("deaths")) t.deaths = n;
        else {
            pl.say(s, "<red>Kies <white>kills <red>of <white>deaths<red>.");
            return;
        }
        pl.stats().dirty = true;
        pl.stats().save();
        Player online = Bukkit.getPlayer(t.id);
        if (online != null) pl.rewards().evaluate(online);
        pl.refreshOpenMenus();
        pl.say(s, "<green><white>" + Items.esc(t.name) + "<green> heeft nu <white>" + n + " " + what + "<green>.");
    }

    // ------------------------------------------------------------ aanvullen

    private static List<String> match(Collection<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }

    private List<String> players() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private List<String> kitIds() {
        List<String> out = new ArrayList<>();
        for (KitDef k : pl.kits().enabled()) out.add(k.id);
        return out;
    }

    private List<String> arenaIds() {
        List<String> out = new ArrayList<>();
        out.add("willekeurig");
        for (ArenaDef x : pl.arenas().all()) out.add(x.id);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "pvp" -> {
                if (a.length == 1) return match(List.of("queue", "leave", "accept", "deny", "stats", "top", "spectate", "rewards", "help"), a[0]);
                String sub = a[0].toLowerCase(Locale.ROOT);
                if (sub.equals("queue") && a.length == 2) return match(kitIds(), a[1]);
                if (sub.equals("queue") && a.length == 3) return match(arenaIds(), a[2]);
                if ((sub.equals("accept") || sub.equals("deny") || sub.equals("stats") || sub.equals("spectate")) && a.length == 2) return match(players(), a[1]);
                if (sub.equals("top") && a.length == 2) return match(List.of("kills", "deaths", "kd", "streak"), a[1]);
            }
            case "duel" -> {
                if (a.length == 1) {
                    List<String> o = new ArrayList<>(players());
                    o.add("accept");
                    o.add("deny");
                    return match(o, a[0]);
                }
                String f = a[0].toLowerCase(Locale.ROOT);
                if ((f.equals("accept") || f.equals("deny")) && a.length == 2) return match(players(), a[1]);
                if (a.length == 2) return match(kitIds(), a[1]);
                if (a.length == 3) return match(arenaIds(), a[2]);
            }
            case "pvpstats", "kills" -> {
                if (a.length == 1) return match(players(), a[0]);
            }
            case "pvptop" -> {
                if (a.length == 1) return match(List.of("kills", "deaths", "kd", "streak"), a[0]);
            }
            case "pvpadmin" -> {
                if (!s.hasPermission(BeneluxPvP.ADMIN)) return List.of();
                if (a.length == 1) return match(List.of("gui", "status", "reload", "lobby", "eis", "zet", "reset"), a[0]);
                String sub = a[0].toLowerCase(Locale.ROOT);
                if (sub.equals("eis") && a.length == 2) {
                    List<String> keys = new ArrayList<>();
                    for (Bridge.Row r : pl.bridge().catalog()) keys.add(r.key());
                    return match(keys, a[1]);
                }
                if (sub.equals("eis") && a.length == 3) return match(List.of("uit", "10", "25", "50", "100"), a[2]);
                if ((sub.equals("zet") || sub.equals("reset")) && a.length == 2) return match(players(), a[1]);
                if (sub.equals("zet") && a.length == 3) return match(List.of("kills", "deaths"), a[2]);
            }
            default -> {
            }
        }
        return List.of();
    }
}
