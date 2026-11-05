package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Alle menu's voor spelers. */
final class PlayerGuis {
    private final BeneluxPvP pl;

    PlayerGuis(BeneluxPvP pl) {
        this.pl = pl;
    }

    private Items it() {
        return pl.items();
    }

    private static String t(String s) {
        return "<dark_red><bold>⚔ <gold><bold>" + s;
    }

    // ------------------------------------------------------------ hoofdmenu

    void hub(Player p) {
        if (pl.busy(p)) {
            pl.say(p, "<gray>Je zit in een wedstrijd of kijkt mee. Stoppen: <white>/pvp leave");
            return;
        }
        Gui g = new Gui(pl, p, 5, BeneluxPvP.TITLE, false);
        g.open(p, () -> buildHub(g, p));
    }

    private void buildHub(Gui g, Player p) {
        Stats.P s = pl.stats().getOrCreate(p.getUniqueId(), p.getName());
        g.set(4, it().head(p, "<gold><bold>" + Items.esc(p.getName()), List.of(
                "<gray>Kills <white>" + s.kills + " <dark_gray>• <gray>Deaths <white>" + s.deaths,
                "<gray>K/D <white>" + s.kdText() + " <dark_gray>• <gray>Reeks <white>" + s.streak)));
        Queue.Entry q = pl.queue().entry(p.getUniqueId());
        if (q != null) {
            KitDef k = pl.kits().get(q.kitId());
            ArenaDef a = q.arenaId() == null ? null : pl.arenas().get(q.arenaId());
            g.set(10, it().glow(it().icon(Material.CLOCK, "<gold><bold>Je staat in de wachtrij",
                    "", "<gray>Kit: <white>" + (k == null ? "?" : Items.esc(k.name)),
                    "<gray>Arena: <white>" + (a == null ? "willekeurig" : Items.esc(a.name)),
                    "<gray>Wachttijd: <white>" + Items.time((int) Math.max(0, (pl.now() - q.since()) / 1000)),
                    "", "<yellow>Klik <gray>» wachtrij verlaten")), (pp, ct) -> {
                pl.queue().remove(pp.getUniqueId());
                pl.say(pp, "<gray>Je hebt de wachtrij verlaten.");
                it().tick(pp);
                g.refresh();
            });
        } else {
            boolean on = pl.settings().bool("wachtrij.aan");
            g.set(10, it().icon(on ? Material.ENDER_EYE : Material.GRAY_DYE, on ? "<gold><bold>Wachtrij" : "<gray><bold>Wachtrij (uit)",
                    "", "<gray>Kies een kit en een arena.", "<gray>Zodra iemand hetzelfde kiest,", "<gray>begint het gevecht!", "",
                    "<gray>Nu in de wachtrij: <white>" + pl.queue().size(), on ? "<yellow>Klik <gray>» kies kit" : "<red>Staat uit"), (pp, ct) -> {
                if (!on) {
                    it().deny(pp);
                    return;
                }
                it().tick(pp);
                queueFlow(pp);
            });
        }
        boolean duel = pl.settings().bool("uitdagen.aan");
        g.set(12, it().icon(duel ? Material.IRON_SWORD : Material.GRAY_DYE, duel ? "<red><bold>Speler uitdagen" : "<gray><bold>Uitdagen (uit)",
                "", "<gray>Daag een speler uit voor", "<gray>een duel, zonder wachtrij.", "", duel ? "<yellow>Klik <gray>» kies speler" : "<red>Staat uit"), (pp, ct) -> {
            if (!duel) {
                it().deny(pp);
                return;
            }
            it().tick(pp);
            challengeFlow(pp);
        });
        int req = pl.requests().count(p.getUniqueId());
        ItemStack ri = it().icon(Material.WRITABLE_BOOK, "<yellow><bold>Uitdagingen" + (req > 0 ? " <red>(" + req + ")" : ""),
                "", "<gray>Uitdagingen die je hebt gekregen.", "", "<yellow>Klik <gray>» bekijken");
        g.set(14, req > 0 ? it().glow(ri) : ri, (pp, ct) -> {
            it().tick(pp);
            requests(pp);
        });
        g.set(16, it().icon(Material.SPYGLASS, "<aqua><bold>Kijken", "", "<gray>Kijk mee met een lopend gevecht.", "<gray>Nu bezig: <white>" + pl.matches().all().size(), "", "<yellow>Klik <gray>» wedstrijden"), (pp, ct) -> {
            it().tick(pp);
            spectate(pp);
        });
        g.set(20, it().icon(Material.PLAYER_HEAD, "<green><bold>Mijn statistieken", "", "<gray>Kills, deaths, K/D en reeksen.", "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
            it().tick(pp);
            stats(pp, pp.getUniqueId(), () -> hub(pp));
        });
        g.set(22, it().icon(Material.NETHER_STAR, "<gold><bold>Ranglijst", "", "<gray>Wie heeft de meeste kills,", "<gray>deaths of de beste K/D?", "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
            it().tick(pp);
            leaderboard(pp, Stats.Type.KILLS, () -> hub(pp));
        });
        g.set(24, it().icon(Material.CHEST, "<light_purple><bold>Beloningen", "", "<gray>Win gevechten en ontgrendel", "<gray>cosmetics!", "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
            it().tick(pp);
            rewards(pp, () -> hub(pp));
        });
        boolean accept = !s.noChallenges;
        g.set(30, it().icon(accept ? Material.LIME_DYE : Material.RED_DYE, accept ? "<green><bold>Uitdagingen: aan" : "<red><bold>Uitdagingen: uit",
                "", "<gray>Wil je uitdagingen van", "<gray>andere spelers ontvangen?", "", "<yellow>Klik <gray>» wisselen"), (pp, ct) -> {
            Stats.P me = pl.stats().getOrCreate(pp.getUniqueId(), pp.getName());
            me.noChallenges = !me.noChallenges;
            pl.stats().dirty = true;
            pl.stats().save();
            it().tick(pp);
            g.refresh();
        });
        g.set(32, it().icon(Material.BOOK, "<white><bold>Uitleg", "", "<gray>Kies een kit en arena in de wachtrij of",
                "<gray>daag iemand uit. Na het gevecht krijg je",
                "<gray>al je spullen terug en wordt de arena", "<gray>hersteld."));
        g.closeButton();
    }

    // ------------------------------------------------------------ kit en arena kiezen

    private void pickKit(Player p, String title, Runnable back, Consumer<KitDef> then) {
        Gui g = new Gui(pl, p, 6, t(title), false);
        g.open(p, () -> {
            List<KitDef> kits = pl.kits().enabled();
            if (kits.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<red><bold>Geen kits", "<gray>Er zijn nog geen kits gemaakt."));
            g.list(kits, k -> {
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>Spullen: <white>" + k.itemCount());
                lore.add("<gray>In de wachtrij: <white>" + pl.queue().waitingFor(k.id));
                lore.add("");
                lore.add("<yellow>Linksklik <gray>» kiezen");
                lore.add("<yellow>Rechtsklik <gray>» voorbeeld");
                return it().icon(k.icon, "<gold><bold>" + Items.esc(k.name), lore);
            }, (k, ct) -> {
                it().tick(p);
                if (ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT) kitPreview(p, k, () -> pickKit(p, title, back, then));
                else then.accept(k);
            }, p);
            g.backButton(back);
        });
    }

    private void pickArena(Player p, KitDef kit, Runnable back, Consumer<ArenaDef> then) {
        Gui g = new Gui(pl, p, 6, t("Kies een arena"), false);
        g.open(p, () -> {
            List<ArenaDef> opts = new ArrayList<>();
            opts.add(null);
            for (ArenaDef a : pl.arenas().all()) if (pl.arenas().usable(a) && a.allows(kit.id)) opts.add(a);
            g.list(opts, a -> {
                if (a == null) return it().icon(Material.ENDER_PEARL, "<light_purple><bold>Willekeurige arena", "", "<gray>Je krijgt een vrije arena.", "<gray>Dit gaat het snelst!", "", "<yellow>Klik <gray>» kiezen");
                String status = switch (a.state) {
                    case IDLE -> "<green>Vrij";
                    case BUSY -> "<red>Bezig";
                    case RESETTING -> "<yellow>Wordt hersteld";
                    case CAPTURING -> "<yellow>Wordt opgeslagen";
                };
                int limit = a.limit > 0 ? a.limit : pl.settings().num("wedstrijd.max-seconden");
                return it().icon(a.icon, "<gold><bold>" + Items.esc(a.name), "", "<gray>Status: " + status,
                        "<gray>Bouwen: " + (a.build ? "<green>ja" : "<red>nee"),
                        "<gray>Tijdslimiet: <white>" + (limit > 0 ? Items.time(limit) : "geen"), "", "<yellow>Klik <gray>» kiezen");
            }, (a, ct) -> {
                it().tick(p);
                then.accept(a);
            }, p);
            g.backButton(back);
        });
    }

    void kitPreview(Player p, KitDef k, Runnable back) {
        Gui g = new Gui(pl, p, 6, t("Kit: " + Items.esc(k.name)), false);
        g.open(p, () -> {
            for (int i = 9; i < 36; i++) if (k.items[i] != null) g.set(i, it().mark(k.items[i].clone()));
            for (int i = 0; i < 9; i++) if (k.items[i] != null) g.set(36 + i, it().mark(k.items[i].clone()));
            int[] armor = {1, 2, 3, 4}; // helm, borststuk, broek, laarzen
            int[] from = {39, 38, 37, 36};
            for (int i = 0; i < 4; i++) if (k.items[from[i]] != null) g.set(armor[i], it().mark(k.items[from[i]].clone()));
            if (k.items[40] != null) g.set(6, it().mark(k.items[40].clone()));
            g.set(0, it().icon(Material.PAPER, "<gold><bold>" + Items.esc(k.name), "<gray>Dit krijg je in een gevecht."));
            g.backButton(back);
        });
    }

    // ------------------------------------------------------------ wachtrij en uitdagen

    void queueFlow(Player p) {
        pickKit(p, "Kies een kit", () -> hub(p), kit ->
                pickArena(p, kit, () -> queueFlow(p), arena -> {
                    String err = pl.queue().join(p, kit, arena);
                    if (err != null) {
                        pl.say(p, "<red>" + err);
                        it().deny(p);
                        return;
                    }
                    if (p.isOnline() && pl.matches().inMatch(p.getUniqueId())) {
                        p.closeInventory();
                    } else {
                        hub(p);
                    }
                }));
    }

    void challengeFlow(Player p) {
        Gui g = new Gui(pl, p, 6, t("Kies een speler"), false);
        g.open(p, () -> {
            List<Player> ps = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.getUniqueId().equals(p.getUniqueId()) || pl.matches().inMatch(o.getUniqueId())) continue;
                if (pl.stats().getOrCreate(o.getUniqueId(), o.getName()).noChallenges && !p.hasPermission(BeneluxPvP.ADMIN)) continue;
                ps.add(o);
            }
            ps.sort(Comparator.comparing(o -> o.getName().toLowerCase(Locale.ROOT)));
            if (ps.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<red><bold>Niemand beschikbaar", "<gray>Er is nu geen speler om uit te dagen."));
            g.list(ps, o -> {
                Stats.P s = pl.stats().getOrCreate(o.getUniqueId(), o.getName());
                return it().head(o, "<gold><bold>" + Items.esc(o.getName()), List.of("<gray>Kills <white>" + s.kills + " <dark_gray>• <gray>K/D <white>" + s.kdText(), "", "<yellow>Klik <gray>» uitdagen"));
            }, (o, ct) -> {
                it().tick(p);
                challengeFlow(p, o.getUniqueId());
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    /** Kies kit en arena en daag deze speler uit. */
    void challengeFlow(Player p, UUID targetId) {
        Runnable again = () -> challengeFlow(p);
        pickKit(p, "Kies een kit", again, kit ->
                pickArena(p, kit, () -> challengeFlow(p, targetId), arena -> {
                    Player target = Bukkit.getPlayer(targetId);
                    String err = pl.requests().send(p, target, kit, arena);
                    if (err != null) {
                        pl.say(p, "<red>" + err);
                        it().deny(p);
                        return;
                    }
                    p.closeInventory();
                }));
    }

    void requests(Player p) {
        Gui g = new Gui(pl, p, 6, t("Uitdagingen"), false);
        g.open(p, () -> {
            List<Requests.Req> rs = pl.requests().incoming(p.getUniqueId());
            if (rs.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen uitdagingen", "<gray>Niemand heeft je uitgedaagd."));
            g.list(rs, r -> {
                KitDef k = pl.kits().get(r.kitId());
                ArenaDef a = r.arenaId() == null ? null : pl.arenas().get(r.arenaId());
                int left = (int) Math.max(0, (r.expires() - pl.now()) / 1000);
                return it().head(Bukkit.getOfflinePlayer(r.from()), "<gold><bold>" + Items.esc(r.fromName()), List.of(
                        "<gray>Kit: <white>" + (k == null ? "?" : Items.esc(k.name)),
                        "<gray>Arena: <white>" + (r.arenaId() == null ? "willekeurig" : a == null ? "?" : Items.esc(a.name)),
                        "<gray>Verloopt over: <white>" + Items.time(left), "",
                        "<green>Linksklik <gray>» accepteren", "<red>Rechtsklik <gray>» weigeren"));
            }, (r, ct) -> {
                if (ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT) {
                    pl.requests().deny(p, r.from());
                    g.refresh();
                } else {
                    String err = pl.requests().accept(p, r.from());
                    if (err != null) {
                        pl.say(p, "<red>" + err);
                        it().deny(p);
                        g.refresh();
                    } else {
                        p.closeInventory();
                    }
                }
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    void spectate(Player p) {
        Gui g = new Gui(pl, p, 6, t("Kijken"), false);
        g.open(p, () -> {
            List<Match> ms = pl.matches().all();
            if (ms.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen gevechten", "<gray>Er is nu niets te zien."));
            g.list(ms, m -> it().icon(m.arena.icon, "<gold><bold>" + Items.esc(m.nameA) + " <red>vs <gold>" + Items.esc(m.nameB), "",
                    "<gray>Kit: <white>" + Items.esc(m.kit.name), "<gray>Arena: <white>" + Items.esc(m.arena.name),
                    "<gray>Duur: <white>" + Items.time(m.elapsed), "<gray>Kijkers: <white>" + m.spectators.size(), "", "<yellow>Klik <gray>» meekijken"),
                    (m, ct) -> {
                        String err = pl.matches().watch(p, m);
                        if (err != null) {
                            pl.say(p, "<red>" + err);
                            it().deny(p);
                            g.refresh();
                        } else {
                            p.closeInventory();
                        }
                    }, p);
            g.backButton(() -> hub(p));
        });
    }

    // ------------------------------------------------------------ statistieken

    void stats(Player viewer, UUID target, Runnable back) {
        Stats.P s0 = pl.stats().get(target);
        Gui g = new Gui(pl, viewer, 5, t(s0 == null ? "Statistieken" : "Stats: " + Items.esc(s0.name)), false);
        g.open(viewer, () -> buildStats(g, viewer, target, back));
    }

    private void buildStats(Gui g, Player viewer, UUID target, Runnable back) {
        Stats.P s = pl.stats().get(target);
        if (s == null) {
            g.set(22, it().icon(Material.BARRIER, "<red><bold>Onbekende speler"));
            g.backButton(back);
            return;
        }
        boolean self = target.equals(viewer.getUniqueId());
        int min = pl.settings().num("ranglijst.min-wedstrijden");
        g.set(13, it().head(Bukkit.getOfflinePlayer(target), "<gold><bold>" + Items.esc(s.name), List.of(
                "<gray>Wedstrijden: <white>" + (s.games() + s.draws), "<gray>Gelijkspel: <white>" + s.draws)));
        g.set(19, it().icon(Material.DIAMOND_SWORD, "<green><bold>Kills", "", "<white>" + s.kills, "<gray>Plek: " + rankText(s.id, Stats.Type.KILLS, min)));
        g.set(21, it().icon(Material.SKELETON_SKULL, "<red><bold>Deaths", "", "<white>" + s.deaths, "<gray>Plek: " + rankText(s.id, Stats.Type.DEATHS, min)));
        g.set(23, it().icon(Material.NETHER_STAR, "<gold><bold>K/D", "", "<white>" + s.kdText(), "<gray>Plek: " + rankText(s.id, Stats.Type.KD, min),
                "<dark_gray>(vanaf " + min + " wedstrijden)"));
        g.set(25, it().icon(Material.BLAZE_POWDER, "<yellow><bold>Reeks", "", "<gray>Nu: <white>" + s.streak, "<gray>Beste: <white>" + s.best, "<gray>Plek: " + rankText(s.id, Stats.Type.STREAK, min)));
        if (self) {
            Rewards.Goal next = pl.rewards().nextGoal(viewer);
            if (next == null) g.set(31, it().icon(Material.CHEST, "<light_purple><bold>Beloningen", "", "<gray>Je hebt alles gehaald of er zijn", "<gray>geen beloningen ingesteld.", "", "<yellow>Klik <gray>» bekijken"), (p, ct) -> rewards(p, back));
            else g.set(31, it().icon(pl.bridge().icon(viewer, next.row()), "<light_purple><bold>Volgende: " + Items.esc(next.row().name()), "",
                    Items.bar(s.kills, next.need()) + " <white>" + s.kills + "<gray>/<white>" + next.need(), "<gray>Nog <white>" + (next.need() - s.kills) + " <gray>overwinningen", "", "<yellow>Klik <gray>» alle beloningen"), (p, ct) -> rewards(p, back));
        }
        g.set(29, it().icon(Material.NETHER_STAR, "<gold><bold>Ranglijst", "", "<yellow>Klik <gray>» openen"), (p, ct) -> leaderboard(p, Stats.Type.KILLS, back));
        g.backButton(back);
    }

    private String rankText(UUID id, Stats.Type t, int min) {
        int r = pl.stats().rank(id, t, min);
        return r == 0 ? "<dark_gray>-" : "<white>#" + r;
    }

    // ------------------------------------------------------------ ranglijst

    void leaderboard(Player p, Stats.Type start, Runnable back) {
        Gui g = new Gui(pl, p, 6, t("Ranglijst"), false);
        Stats.Type[] tab = {start};
        g.page = 0;
        g.open(p, () -> {
            int min = pl.settings().num("ranglijst.min-wedstrijden");
            Material[] icons = {Material.DIAMOND_SWORD, Material.SKELETON_SKULL, Material.NETHER_STAR, Material.BLAZE_POWDER};
            Stats.Type[] types = Stats.Type.values();
            for (int i = 0; i < types.length; i++) {
                Stats.Type ty = types[i];
                ItemStack icon = it().icon(icons[i], (tab[0] == ty ? "<gold><bold>" : "<gray><bold>") + ty.label, "", tab[0] == ty ? "<green>Geselecteerd" : "<yellow>Klik <gray>» bekijken");
                g.set(2 + i, tab[0] == ty ? it().glow(icon) : icon, (pp, ct) -> {
                    tab[0] = ty;
                    g.page = 0;
                    it().tick(pp);
                    g.refresh();
                });
            }
            List<Stats.P> top = pl.stats().top(tab[0], 100, min);
            if (top.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Nog niemand", "<gray>Er staat nog niemand in deze ranglijst."));
            g.page = Math.max(0, Math.min(g.page, Items.pageCount(top.size(), g.slots().length) - 1));
            int offset = g.page * g.slots().length;
            int[] idx = {0};
            g.list(top, s -> {
                int rank = offset + idx[0]++ + 1;
                String color = rank == 1 ? "<gold>" : rank == 2 ? "<white>" : rank == 3 ? "<#cd7f32>" : "<gray>";
                String value = switch (tab[0]) {
                    case KILLS -> s.kills + " kills";
                    case DEATHS -> s.deaths + " deaths";
                    case KD -> s.kdText() + " K/D";
                    case STREAK -> s.best + " op rij";
                };
                ItemStack head = it().head(Bukkit.getOfflinePlayer(s.id), color + "<bold>#" + rank + " " + Items.esc(s.name), List.of(
                        "<white>" + value, "", "<gray>Kills <white>" + s.kills + " <dark_gray>• <gray>Deaths <white>" + s.deaths, "<gray>K/D <white>" + s.kdText()));
                return s.id.equals(p.getUniqueId()) ? it().glow(head) : head;
            }, (s, ct) -> {
                it().tick(p);
                stats(p, s.id, () -> leaderboard(p, tab[0], back));
            }, p);
            int mine = pl.stats().rank(p.getUniqueId(), tab[0], min);
            g.set(g.last(2), it().icon(Material.BOOK, "<yellow><bold>Jouw plek", "", mine == 0 ? "<gray>Je staat er nog niet in." : "<white>#" + mine,
                    tab[0] == Stats.Type.KD ? "<dark_gray>K/D telt vanaf " + min + " wedstrijden" : "<dark_gray>Top 100 wordt getoond"));
            g.backButton(back);
        });
    }

    // ------------------------------------------------------------ beloningen (cosmetics)

    void rewards(Player p, Runnable back) {
        Gui g = new Gui(pl, p, 6, t("Beloningen"), false);
        Filter[] f = {Filter.ALL};
        g.open(p, () -> buildRewards(g, p, f, back));
    }

    private void buildRewards(Gui g, Player p, Filter[] f, Runnable back) {
        String problem = pl.bridge().problem();
        if (problem != null) {
            g.set(22, it().icon(Material.BARRIER, "<red><bold>Niet beschikbaar", "<gray>" + problem));
            g.backButton(back);
            return;
        }
        Stats.P s = pl.stats().getOrCreate(p.getUniqueId(), p.getName());
        for (Filter fl : Filter.values()) {
            ItemStack icon = it().icon(fl.icon, (f[0] == fl ? "<gold><bold>" : "<gray><bold>") + fl.label, "", f[0] == fl ? "<green>Geselecteerd" : "<yellow>Klik <gray>» filteren");
            g.set(1 + fl.ordinal(), f[0] == fl ? it().glow(icon) : icon, (pp, ct) -> {
                f[0] = fl;
                g.page = 0;
                it().tick(pp);
                g.refresh();
            });
        }
        List<Rewards.Goal> goals = new ArrayList<>();
        for (Rewards.Goal gl : pl.rewards().goals()) if (f[0].matches(gl.row())) goals.add(gl);
        if (goals.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen beloningen", "<gray>Staff heeft hier nog niets ingesteld."));
        g.list(goals, gl -> {
            boolean has = s.unlocked.contains(gl.row().key()) || pl.bridge().owns(p.getUniqueId(), gl.row().key());
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + Items.esc(gl.row().catLabel()));
            lore.add("");
            if (has) {
                lore.add("<green>✔ Ontgrendeld");
                lore.add("<gray>Dragen via <white>/cosmetics");
            } else {
                lore.add(Items.bar(s.kills, gl.need()) + " <white>" + s.kills + "<gray>/<white>" + gl.need());
                lore.add("<gray>Nog <white>" + Math.max(0, gl.need() - s.kills) + " <gray>overwinningen");
            }
            ItemStack ic = it().icon(pl.bridge().icon(p, gl.row()), (has ? "<green><bold>" : "<gold><bold>") + Items.esc(gl.row().name()), lore);
            return has ? it().glow(ic) : ic;
        }, (gl, ct) -> it().tick(p), p);
        g.set(g.last(2), it().icon(Material.DIAMOND_SWORD, "<green><bold>Jouw kills", "", "<white>" + s.kills));
        Rewards.Goal next = pl.rewards().nextGoal(p);
        g.set(g.last(6), next == null ? it().icon(Material.NETHER_STAR, "<gold><bold>Volgende beloning", "", "<gray>Niets meer te halen.")
                : it().icon(pl.bridge().icon(p, next.row()), "<gold><bold>Volgende: " + Items.esc(next.row().name()), "", Items.bar(s.kills, next.need()) + " <white>" + s.kills + "<gray>/<white>" + next.need()));
        g.backButton(back);
    }
}
