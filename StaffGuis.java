package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Alle menu's voor staff. Bij elke klik wordt opnieuw gecontroleerd of de speler nog staff is. */
final class StaffGuis {
    static final int[] STEPS = {1, 5, 10, 25, 50, 100};

    private final BeneluxPvP pl;
    private final Map<UUID, Integer> stepIdx = new HashMap<>();

    StaffGuis(BeneluxPvP pl) {
        this.pl = pl;
    }

    private Items it() {
        return pl.items();
    }

    private static String t(String s) {
        return "<dark_red><bold>⚔ <gold><bold>Staff: " + s;
    }

    private Gui gui(Player p, int rows, String title) {
        return new Gui(pl, p, rows, t(title), true);
    }

    private static boolean right(ClickType ct) {
        return ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT;
    }

    private static boolean shift(ClickType ct) {
        return ct == ClickType.SHIFT_LEFT || ct == ClickType.SHIFT_RIGHT;
    }

    private boolean allowed(Player p) {
        if (p.hasPermission(BeneluxPvP.ADMIN)) return true;
        pl.say(p, "<red>Je hebt hier geen toestemming voor.");
        return false;
    }

    // ------------------------------------------------------------ hoofdmenu

    void hub(Player p) {
        if (!allowed(p)) return;
        Gui g = gui(p, 5, "Hoofdmenu");
        g.open(p, () -> {
            g.set(10, it().icon(Material.IRON_CHESTPLATE, "<gold><bold>Kits", "", "<gray>Kits maken en aanpassen.", "<gray>Aantal: <white>" + pl.kits().all().size(), "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                kits(pp, 0);
            });
            g.set(12, it().icon(Material.GRASS_BLOCK, "<gold><bold>Arena's", "", "<gray>Arena's maken, startplekken", "<gray>kiezen en herstel instellen.", "<gray>Aantal: <white>" + pl.arenas().all().size(), "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                arenas(pp);
            });
            g.set(14, it().icon(Material.CHEST, "<light_purple><bold>Beloningen", "", "<gray>Hoeveel overwinningen heb je nodig", "<gray>voor welke cosmetic?", "<gray>Ingesteld: <white>" + pl.store().all().size(), "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                rewards(pp);
            });
            g.set(16, it().icon(Material.NETHER_STAR, "<green><bold>Statistieken", "", "<gray>Spelers bekijken, aanpassen", "<gray>of resetten.", "<gray>Spelers: <white>" + pl.stats().size(), "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                stats(pp);
            });
            g.set(20, it().icon(Material.CLOCK, "<aqua><bold>Gevechten en wachtrij", "", "<gray>Lopend: <white>" + pl.matches().all().size(), "<gray>Wachtrij: <white>" + pl.queue().size(), "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                live(pp, false);
            });
            g.set(22, it().icon(Material.COMPARATOR, "<yellow><bold>Instellingen", "", "<gray>Aftellen, tijden, regels tegen farmen.", "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                settings(pp);
            });
            g.set(24, it().icon(Material.COMPASS, "<white><bold>Lobby instellen", "", "<gray>Reserveplek waar spelers heen gaan", "<gray>als hun oude plek niet meer bestaat.",
                    "<gray>Nu: <white>" + (pl.arenas().lobby == null ? "niet ingesteld" : Items.esc(pl.arenas().lobby.world()) + " " + pl.arenas().lobby.pretty()), "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> {
                pl.arenas().lobby = Loc.of(pp.getLocation());
                if (pl.arenas().save()) pl.say(pp, "<green>Lobby ingesteld op je huidige plek.");
                else pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                it().tick(pp);
                g.refresh();
            });
            String problem = pl.bridge().problem();
            g.set(30, it().icon(Material.BOOK, "<white><bold>Status", "",
                    "<gray>Cosmetics: " + (problem == null ? "<green>gekoppeld <dark_gray>(" + pl.bridge().catalog().size() + ")" : "<red>" + Items.esc(problem)),
                    "<gray>Kits klaar: <white>" + pl.kits().enabled().size() + "<gray>/<white>" + pl.kits().all().size(),
                    "<gray>Arena's klaar: <white>" + readyArenas() + "<gray>/<white>" + pl.arenas().all().size()));
            g.set(32, it().icon(Material.REDSTONE, "<white><bold>Instellingen herladen", "", "<gray>Leest config.yml en staff.yml opnieuw.", "", "<yellow>Klik <gray>» herladen"), (pp, ct) -> {
                pl.reloadConfig();
                pl.store().load();
                pl.rewards().evaluateAllSoon();
                pl.say(pp, "<green>Instellingen opnieuw geladen.");
                it().tick(pp);
                g.refresh();
            });
            g.closeButton();
        });
    }

    private int readyArenas() {
        int n = 0;
        for (ArenaDef a : pl.arenas().all()) if (pl.arenas().usable(a)) n++;
        return n;
    }

    // ------------------------------------------------------------ kits

    void kits(Player p, int page) {
        Gui g = gui(p, 6, "Kits");
        g.page = page;
        g.open(p, () -> {
            g.set(4, it().icon(Material.EMERALD, "<green><bold>Nieuwe kit", "", "<gray>Maakt een kit van wat er nu", "<gray>in <white>jouw inventaris <gray>zit", "<gray>(inclusief armor en tweede hand).", "", "<yellow>Klik <gray>» naam typen"), (pp, ct) -> {
                it().tick(pp);
                pl.ask(pp, "<gray>Leg eerst de spullen in je inventaris. Typ nu de <white>naam <gray>van de nieuwe kit:", (p2, text) -> {
                    String[] err = new String[1];
                    KitDef k = pl.kits().create(text, p2, err);
                    if (k == null) {
                        pl.say(p2, "<red>" + err[0]);
                        kits(p2, 0);
                        return;
                    }
                    pl.say(p2, "<green>Kit <white>" + Items.esc(k.name) + "<green> is gemaakt met <white>" + k.itemCount() + "<green> spullen.");
                    kitEditor(p2, k.id);
                }, () -> kits(pp, 0));
            });
            List<KitDef> all = pl.kits().all();
            if (all.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Nog geen kits", "<gray>Klik op de groene knop om er een te maken."));
            g.list(all, k -> it().icon(k.icon, (k.enabled ? "<gold><bold>" : "<gray><bold>") + Items.esc(k.name), "", "<gray>Status: " + (k.enabled ? "<green>aan" : "<red>uit"),
                    "<gray>Spullen: <white>" + k.itemCount(), "", "<yellow>Klik <gray>» bewerken"), (k, ct) -> {
                it().tick(p);
                kitEditor(p, k.id);
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    void kitEditor(Player p, String kitId) {
        Gui g = gui(p, 5, "Kit bewerken");
        boolean[] confirm = new boolean[2]; // 0 = overschrijven, 1 = verwijderen
        g.open(p, () -> {
            KitDef k = pl.kits().get(kitId);
            if (k == null) {
                g.set(22, it().icon(Material.BARRIER, "<red><bold>Deze kit bestaat niet meer"));
                g.backButton(() -> kits(p, 0));
                return;
            }
            g.set(4, it().icon(k.icon, "<gold><bold>" + Items.esc(k.name), "", "<gray>Spullen: <white>" + k.itemCount(), "<gray>Status: " + (k.enabled ? "<green>aan" : "<red>uit")));
            g.set(10, it().icon(k.enabled ? Material.LIME_DYE : Material.RED_DYE, k.enabled ? "<green><bold>Aan" : "<red><bold>Uit", "", "<gray>Alleen kits die aan staan", "<gray>zijn te kiezen.", "", "<yellow>Klik <gray>» wisselen"), (pp, ct) -> {
                k.enabled = !k.enabled;
                if (!pl.kits().save()) {
                    k.enabled = !k.enabled;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            g.set(12, it().icon(Material.NAME_TAG, "<yellow><bold>Naam wijzigen", "", "<gray>Nu: <white>" + Items.esc(k.name), "", "<yellow>Klik <gray>» naam typen"), (pp, ct) -> pl.ask(pp, "<gray>Typ de nieuwe <white>naam <gray>van de kit:", (p2, text) -> {
                String n = text.trim();
                if (n.isEmpty()) {
                    pl.say(p2, "<red>De naam mag niet leeg zijn.");
                } else {
                    String old = k.name;
                    k.name = n.length() > 32 ? n.substring(0, 32) : n;
                    if (!pl.kits().save()) {
                        k.name = old;
                        pl.say(p2, "<red>Opslaan mislukt, kijk in de console.");
                    } else {
                        pl.say(p2, "<green>Naam is nu <white>" + Items.esc(k.name) + "<green>.");
                    }
                }
                kitEditor(p2, kitId);
            }, () -> kitEditor(pp, kitId)));
            g.set(14, it().icon(Material.ITEM_FRAME, "<yellow><bold>Icoon kiezen", "", "<gray>Houd een item in je hand en klik.", "<gray>Dat item wordt het icoon.", "", "<yellow>Klik <gray>» icoon zetten"), (pp, ct) -> {
                ItemStack hand = pp.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir() || it().isMenuItem(hand)) {
                    pl.say(pp, "<red>Houd eerst een item in je hand.");
                    it().deny(pp);
                    return;
                }
                Material old = k.icon;
                k.icon = hand.getType();
                if (!pl.kits().save()) {
                    k.icon = old;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            g.set(16, it().icon(Material.ENDER_EYE, "<aqua><bold>Voorbeeld", "", "<gray>Bekijk wat spelers krijgen.", "", "<yellow>Klik <gray>» bekijken"), (pp, ct) -> {
                it().tick(pp);
                pl.playerGuis().kitPreview(pp, k, () -> kitEditor(pp, kitId));
            });
            g.set(20, it().icon(confirm[0] ? Material.RED_CONCRETE : Material.HOPPER, confirm[0] ? "<red><bold>Klik nogmaals om te overschrijven" : "<gold><bold>Overschrijven met mijn inventaris", "",
                    "<gray>Vervangt de spullen van deze kit door", "<gray>wat jij nu in je inventaris hebt.", "", "<yellow>Klik <gray>» " + (confirm[0] ? "bevestigen" : "starten")), (pp, ct) -> {
                if (!confirm[0]) {
                    confirm[0] = true;
                    confirm[1] = false;
                    g.refresh();
                    return;
                }
                ItemStack[] snap = pl.kits().snapshot(pp);
                boolean any = false;
                for (ItemStack i : snap) if (i != null) any = true;
                if (!any) {
                    pl.say(pp, "<red>Je inventaris is leeg. De kit is niet veranderd.");
                    confirm[0] = false;
                    g.refresh();
                    return;
                }
                ItemStack[] old = k.items.clone();
                k.setItems(snap);
                if (!pl.kits().save()) {
                    k.setItems(old);
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                } else {
                    pl.say(pp, "<green>Kit <white>" + Items.esc(k.name) + "<green> bijgewerkt (" + k.itemCount() + " spullen).");
                }
                confirm[0] = false;
                it().tick(pp);
                g.refresh();
            });
            g.set(24, it().icon(confirm[1] ? Material.RED_CONCRETE : Material.TNT, confirm[1] ? "<red><bold>Klik nogmaals om te verwijderen" : "<red><bold>Kit verwijderen", "",
                    "<gray>Dit kan niet ongedaan gemaakt worden.", "", "<yellow>Klik <gray>» " + (confirm[1] ? "bevestigen" : "starten")), (pp, ct) -> {
                if (!confirm[1]) {
                    confirm[1] = true;
                    confirm[0] = false;
                    g.refresh();
                    return;
                }
                if (pl.kits().delete(kitId)) {
                    pl.say(pp, "<green>Kit verwijderd.");
                    for (Queue.Entry e : pl.queue().all()) if (e.kitId().equals(kitId)) pl.queue().remove(e.id());
                } else {
                    pl.say(pp, "<red>Verwijderen mislukt, kijk in de console.");
                }
                kits(pp, 0);
            });
            g.backButton(() -> kits(p, 0));
        });
    }

    // ------------------------------------------------------------ arena's

    void arenas(Player p) {
        Gui g = gui(p, 6, "Arena's");
        g.open(p, () -> {
            g.set(4, it().icon(Material.EMERALD, "<green><bold>Nieuwe arena", "", "<gray>Maakt een lege arena. Daarna stel je", "<gray>hoeken, startplekken en kijkplek in.", "", "<yellow>Klik <gray>» naam typen"), (pp, ct) -> {
                it().tick(pp);
                pl.ask(pp, "<gray>Typ de <white>naam <gray>van de nieuwe arena:", (p2, text) -> {
                    String[] err = new String[1];
                    ArenaDef a = pl.arenas().create(text, err);
                    if (a == null) {
                        pl.say(p2, "<red>" + err[0]);
                        arenas(p2);
                        return;
                    }
                    pl.say(p2, "<green>Arena <white>" + Items.esc(a.name) + "<green> is gemaakt. <gray>Stel nu de hoeken, startplekken en kijkplek in.");
                    arenaEditor(p2, a.id);
                }, () -> arenas(pp));
            });
            List<ArenaDef> all = pl.arenas().all();
            if (all.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Nog geen arena's", "<gray>Klik op de groene knop om er een te maken."));
            g.list(all, a -> {
                List<String> pr = pl.arenas().problems(a);
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>Status: " + stateText(a));
                lore.add("<gray>Klaar: " + (pr.isEmpty() ? "<green>ja" : "<red>nee <dark_gray>(" + pr.size() + " ontbreekt)"));
                lore.add("");
                lore.add("<yellow>Klik <gray>» bewerken");
                return it().icon(a.icon, (a.enabled ? "<gold><bold>" : "<gray><bold>") + Items.esc(a.name), lore);
            }, (a, ct) -> {
                it().tick(p);
                arenaEditor(p, a.id);
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    private static String stateText(ArenaDef a) {
        return switch (a.state) {
            case IDLE -> "<green>vrij";
            case BUSY -> "<red>bezig";
            case RESETTING -> "<yellow>wordt hersteld";
            case CAPTURING -> "<yellow>wordt opgeslagen";
        };
    }

    private void spotResult(Player p, String err, Gui g, String ok) {
        if (err != null) {
            pl.say(p, "<red>" + err);
            it().deny(p);
        } else {
            pl.say(p, "<green>" + ok);
            it().tick(p);
        }
        g.refresh();
    }

    void arenaEditor(Player p, String arenaId) {
        Gui g = gui(p, 5, "Arena bewerken");
        boolean[] confirm = new boolean[2]; // 0 = origineel overschrijven, 1 = verwijderen
        g.open(p, () -> {
            ArenaDef a = pl.arenas().get(arenaId);
            if (a == null) {
                g.set(22, it().icon(Material.BARRIER, "<red><bold>Deze arena bestaat niet meer"));
                g.backButton(() -> arenas(p));
                return;
            }
            Region r = a.region();
            g.set(4, it().icon(a.icon, "<gold><bold>" + Items.esc(a.name), "", "<gray>Status: " + stateText(a),
                    r == null ? "<gray>Gebied: <red>niet ingesteld" : "<gray>Gebied: <white>" + r.sx() + "×" + r.sy() + "×" + r.sz() + " <dark_gray>(" + r.volume() + " blokken)"));
            g.set(10, it().icon(Material.RED_CONCRETE, "<red><bold>Hoek 1", "", "<gray>Nu: <white>" + (a.p1 == null ? "niet gezet" : a.p1[0] + ", " + a.p1[1] + ", " + a.p1[2]),
                    "<gray>Ga in een hoek van de arena staan.", "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> spotResult(pp, pl.arenas().setCorner(a, true, pp.getLocation()), g, "Hoek 1 gezet. Het oude origineel is gewist; sla het opnieuw op."));
            g.set(11, it().icon(Material.BLUE_CONCRETE, "<blue><bold>Hoek 2", "", "<gray>Nu: <white>" + (a.p2 == null ? "niet gezet" : a.p2[0] + ", " + a.p2[1] + ", " + a.p2[2]),
                    "<gray>De tegenovergestelde hoek", "<gray>(ook hoogte tellen mee).", "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> spotResult(pp, pl.arenas().setCorner(a, false, pp.getLocation()), g, "Hoek 2 gezet. Het oude origineel is gewist; sla het opnieuw op."));
            g.set(12, it().icon(Material.LIME_CONCRETE, "<green><bold>Startplek 1", "", "<gray>Nu: <white>" + (a.s1 == null ? "niet gezet" : a.s1.pretty()),
                    "<gray>Speler 1 verschijnt hier,", "<gray>kijkend zoals jij kijkt.", "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> spotResult(pp, pl.arenas().setSpot(a, 1, pp.getLocation()), g, "Startplek 1 gezet."));
            g.set(13, it().icon(Material.YELLOW_CONCRETE, "<yellow><bold>Startplek 2", "", "<gray>Nu: <white>" + (a.s2 == null ? "niet gezet" : a.s2.pretty()),
                    "<gray>Speler 2 verschijnt hier.", "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> spotResult(pp, pl.arenas().setSpot(a, 2, pp.getLocation()), g, "Startplek 2 gezet."));
            g.set(14, it().icon(Material.SPYGLASS, "<aqua><bold>Kijkplek", "", "<gray>Nu: <white>" + (a.spectator == null ? "niet gezet" : a.spectator.pretty()),
                    "<gray>Hier staan toeschouwers en", "<gray>verliezers tot het einde.", "", "<yellow>Klik <gray>» zet op jouw plek"), (pp, ct) -> spotResult(pp, pl.arenas().setSpot(a, 3, pp.getLocation()), g, "Kijkplek gezet."));
            boolean snap = pl.arenas().hasSnapshot(a);
            boolean needConfirm = snap && !confirm[0];
            g.set(15, it().icon(confirm[0] ? Material.RED_CONCRETE : Material.STRUCTURE_BLOCK, confirm[0] ? "<red><bold>Klik nogmaals om te overschrijven" : "<gold><bold>Origineel opslaan", "",
                    "<gray>Maakt een foto van de arena zoals", "<gray>hij NU is. Na elk gevecht wordt de", "<gray>arena hiernaar teruggezet.",
                    "<gray>Origineel: " + (snap ? "<green>opgeslagen" : "<red>nog niet"), "", "<yellow>Klik <gray>» " + (needConfirm ? "start (met bevestiging)" : "opslaan")), (pp, ct) -> {
                if (snap && !confirm[0]) {
                    confirm[0] = true;
                    g.refresh();
                    return;
                }
                confirm[0] = false;
                String err = pl.arenas().capture(a);
                if (err != null) spotResult(pp, err, g, "");
                else spotResult(pp, null, g, "Bezig met opslaan van het origineel... Je krijgt een bericht als het klaar is.");
            });
            g.set(16, it().icon(Material.WATER_BUCKET, "<aqua><bold>Nu herstellen", "", "<gray>Zet de arena meteen terug naar", "<gray>het opgeslagen origineel.", "", "<yellow>Klik <gray>» herstellen"), (pp, ct) -> {
                String err = pl.arenas().manualReset(a);
                spotResult(pp, err, g, "De arena wordt hersteld...");
            });
            g.set(19, it().icon(a.enabled ? Material.LIME_DYE : Material.RED_DYE, a.enabled ? "<green><bold>Aan" : "<red><bold>Uit", "", "<gray>Alleen arena's die aan staan", "<gray>zijn te kiezen.", "", "<yellow>Klik <gray>» wisselen"), (pp, ct) -> {
                a.enabled = !a.enabled;
                if (!pl.arenas().save()) {
                    a.enabled = !a.enabled;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            g.set(20, it().icon(a.build ? Material.OAK_PLANKS : Material.BARRIER, a.build ? "<green><bold>Bouwen: toegestaan" : "<red><bold>Bouwen: niet toegestaan", "",
                    "<gray>Mogen spelers blokken plaatsen en", "<gray>breken tijdens het gevecht?", "<gray>(De arena wordt altijd hersteld.)", "", "<yellow>Klik <gray>» wisselen"), (pp, ct) -> {
                a.build = !a.build;
                if (!pl.arenas().save()) {
                    a.build = !a.build;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            int def = pl.settings().num("wedstrijd.max-seconden");
            g.set(21, it().icon(Material.CLOCK, "<yellow><bold>Tijdslimiet", "", "<gray>Nu: <white>" + (a.limit > 0 ? Items.time(a.limit) : "standaard (" + (def > 0 ? Items.time(def) : "geen") + ")"),
                    "", "<yellow>Linksklik <gray>» +30 sec", "<yellow>Rechtsklik <gray>» -30 sec", "<yellow>Shift <gray>» ×10", "<gray>0 = standaard"), (pp, ct) -> {
                int step = shift(ct) ? 300 : 30;
                int old = a.limit;
                a.limit = Math.max(0, Math.min(3600, a.limit + (right(ct) ? -step : step)));
                if (!pl.arenas().save()) {
                    a.limit = old;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            g.set(22, it().icon(Material.IRON_CHESTPLATE, "<gold><bold>Toegestane kits", "", "<gray>Nu: <white>" + (a.kits.isEmpty() ? "alle kits" : a.kits.size() + " kit(s)"), "", "<yellow>Klik <gray>» kiezen"), (pp, ct) -> {
                it().tick(pp);
                arenaKits(pp, arenaId);
            });
            g.set(23, it().icon(Material.ITEM_FRAME, "<yellow><bold>Icoon kiezen", "", "<gray>Houd een item in je hand en klik.", "", "<yellow>Klik <gray>» icoon zetten"), (pp, ct) -> {
                ItemStack hand = pp.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir() || it().isMenuItem(hand)) {
                    pl.say(pp, "<red>Houd eerst een item in je hand.");
                    it().deny(pp);
                    return;
                }
                Material old = a.icon;
                a.icon = hand.getType();
                if (!pl.arenas().save()) {
                    a.icon = old;
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            g.set(24, it().icon(Material.ENDER_PEARL, "<light_purple><bold>Ga erheen", "", "<gray>Teleporteert je naar de kijkplek", "<gray>(of startplek 1).", "", "<yellow>Klik <gray>» teleporteren"), (pp, ct) -> {
                Loc to = a.spectator != null ? a.spectator : a.s1;
                Location l = to == null ? null : to.toLocation();
                if (l == null) {
                    pl.say(pp, "<red>Er is nog geen plek ingesteld.");
                    it().deny(pp);
                    return;
                }
                if (pl.busy(pp)) {
                    pl.say(pp, "<red>Je zit in een wedstrijd of kijkt mee.");
                    return;
                }
                pp.closeInventory();
                pp.teleport(l);
            });
            g.set(25, it().icon(confirm[1] ? Material.RED_CONCRETE : Material.TNT, confirm[1] ? "<red><bold>Klik nogmaals om te verwijderen" : "<red><bold>Arena verwijderen", "",
                    "<gray>Verwijdert ook het opgeslagen origineel.", "", "<yellow>Klik <gray>» " + (confirm[1] ? "bevestigen" : "starten")), (pp, ct) -> {
                if (!confirm[1]) {
                    confirm[1] = true;
                    confirm[0] = false;
                    g.refresh();
                    return;
                }
                String err = pl.arenas().delete(a);
                if (err != null) {
                    pl.say(pp, "<red>" + err);
                    confirm[1] = false;
                    g.refresh();
                    return;
                }
                for (Queue.Entry e : pl.queue().all()) if (a.id.equals(e.arenaId())) pl.queue().remove(e.id());
                pl.say(pp, "<green>Arena verwijderd.");
                arenas(pp);
            });
            List<String> pr = pl.arenas().problems(a);
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (pr.isEmpty()) lore.add("<green>Alles is ingesteld.");
            else for (String s : pr) lore.add("<red>✖ <gray>" + s);
            g.set(31, it().icon(pr.isEmpty() ? Material.EMERALD : Material.PAPER, pr.isEmpty() ? "<green><bold>Klaar voor gebruik" : "<yellow><bold>Nog te doen", lore));
            g.backButton(() -> arenas(p));
        });
    }

    void arenaKits(Player p, String arenaId) {
        Gui g = gui(p, 6, "Toegestane kits");
        g.open(p, () -> {
            ArenaDef a = pl.arenas().get(arenaId);
            if (a == null) {
                g.set(22, it().icon(Material.BARRIER, "<red><bold>Deze arena bestaat niet meer"));
                g.backButton(() -> arenas(p));
                return;
            }
            g.set(4, it().icon(Material.NETHER_STAR, "<gold><bold>Alle kits toestaan", "", "<gray>Maakt de lijst leeg: elke kit mag.", "", "<yellow>Klik <gray>» alles toestaan"), (pp, ct) -> {
                Set<String> old = new LinkedHashSet<>(a.kits);
                a.kits.clear();
                if (!pl.arenas().save()) {
                    a.kits.addAll(old);
                    pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(pp);
                g.refresh();
            });
            List<KitDef> all = pl.kits().all();
            if (all.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen kits", "<gray>Maak eerst kits."));
            g.list(all, k -> {
                boolean ok = a.allows(k.id);
                ItemStack ic = it().icon(k.icon, (ok ? "<green><bold>" : "<red><bold>") + Items.esc(k.name), "", "<gray>Toegestaan: " + (ok ? "<green>ja" : "<red>nee"), "", "<yellow>Klik <gray>» wisselen");
                return ok ? it().glow(ic) : ic;
            }, (k, ct) -> {
                Set<String> old = new LinkedHashSet<>(a.kits);
                if (a.kits.isEmpty()) {
                    for (KitDef o : pl.kits().all()) if (!o.id.equals(k.id)) a.kits.add(o.id);
                    if (a.kits.isEmpty()) {
                        pl.say(p, "<red>Er moet minstens één kit toegestaan blijven.");
                        it().deny(p);
                        return;
                    }
                } else if (a.kits.contains(k.id)) {
                    if (a.kits.size() == 1) {
                        pl.say(p, "<red>Er moet minstens één kit toegestaan blijven.");
                        it().deny(p);
                        return;
                    }
                    a.kits.remove(k.id);
                } else {
                    a.kits.add(k.id);
                    boolean everything = true;
                    for (KitDef o : pl.kits().all()) if (!a.kits.contains(o.id)) everything = false;
                    if (everything) a.kits.clear();
                }
                if (!pl.arenas().save()) {
                    a.kits.clear();
                    a.kits.addAll(old);
                    pl.say(p, "<red>Opslaan mislukt, kijk in de console.");
                }
                it().tick(p);
                g.refresh();
            }, p);
            g.backButton(() -> arenaEditor(p, arenaId));
        });
    }

    // ------------------------------------------------------------ beloningen (cosmetics)

    void rewards(Player p) {
        Gui g = gui(p, 6, "Beloningen");
        Filter[] f = {Filter.ALL};
        g.open(p, () -> buildRewards(g, p, f));
    }

    private int step(Player p) {
        return STEPS[Math.max(0, Math.min(STEPS.length - 1, stepIdx.getOrDefault(p.getUniqueId(), 0)))];
    }

    private void buildRewards(Gui g, Player p, Filter[] f) {
        String problem = pl.bridge().problem();
        if (problem != null) {
            g.set(22, it().icon(Material.BARRIER, "<red><bold>Niet beschikbaar", "<gray>" + problem));
            g.backButton(() -> hub(p));
            return;
        }
        for (Filter fl : Filter.values()) {
            ItemStack icon = it().icon(fl.icon, (f[0] == fl ? "<gold><bold>" : "<gray><bold>") + fl.label, "", f[0] == fl ? "<green>Geselecteerd" : "<yellow>Klik <gray>» filteren");
            g.set(1 + fl.ordinal(), f[0] == fl ? it().glow(icon) : icon, (pp, ct) -> {
                f[0] = fl;
                g.page = 0;
                it().tick(pp);
                g.refresh();
            });
        }
        List<Bridge.Row> rows = new ArrayList<>();
        for (Bridge.Row r : pl.bridge().catalog()) if (f[0].matches(r)) rows.add(r);
        rows.sort(Comparator.comparing((Bridge.Row r) -> r.catLabel().toLowerCase(Locale.ROOT)).thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        if (rows.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen cosmetics", "<gray>Cosmetics heeft hier niets in."));
        int st = step(p);
        g.list(rows, r -> {
            int need = pl.store().need(r.key());
            ItemStack ic = it().icon(pl.bridge().icon(p, r), (need > 0 ? "<gold><bold>" : "<gray><bold>") + Items.esc(r.name()), "<dark_gray>" + Items.esc(r.catLabel()), "",
                    "<gray>Eis: " + (need > 0 ? "<red>" + need + " overwinningen" : "<dark_gray>geen"),
                    "<gray>Spelers met deze beloning: <white>" + pl.stats().unlockedCount(r.key()), "",
                    "<yellow>Linksklik <gray>» +" + st, "<yellow>Rechtsklik <gray>» -" + st, "<yellow>Shift <gray>» ×10",
                    "<yellow>Q <gray>» getal typen", "<yellow>Middelste klik <gray>» eis weghalen");
            return need > 0 ? it().glow(ic) : ic;
        }, (r, ct) -> {
            int cur = pl.store().need(r.key());
            int stp = step(p) * (shift(ct) ? 10 : 1);
            if (ct == ClickType.MIDDLE) {
                pl.store().setNeed(r.key(), 0);
            } else if (ct == ClickType.DROP || ct == ClickType.CONTROL_DROP) {
                pl.ask(p, "<gray>Typ het aantal <white>overwinningen <gray>voor <white>" + Items.esc(r.name()) + " <gray>(0 = geen eis):", (p2, text) -> {
                    try {
                        int n = Integer.parseInt(text.trim());
                        if (n < 0 || n > Store.MAX_NEED) {
                            pl.say(p2, "<red>Kies een getal van 0 tot " + Store.MAX_NEED + ".");
                        } else {
                            pl.store().setNeed(r.key(), n);
                            pl.store().save();
                            pl.rewards().evaluateAllSoon();
                            pl.say(p2, "<green>Eis voor <white>" + Items.esc(r.name()) + "<green> is nu " + (n == 0 ? "weggehaald" : "<white>" + n));
                        }
                    } catch (NumberFormatException ex) {
                        pl.say(p2, "<red>Dat is geen getal.");
                    }
                    rewards(p2);
                }, () -> rewards(p));
                return;
            } else if (right(ct)) {
                int n = cur - stp;
                pl.store().setNeed(r.key(), n <= 0 ? 0 : n);
            } else {
                pl.store().setNeed(r.key(), Math.min(Store.MAX_NEED, cur + stp));
            }
            pl.store().save();
            it().tick(p);
            pl.rewards().evaluateAllSoon();
            g.refresh();
        }, p);
        g.set(g.last(3), it().icon(Material.REPEATER, "<yellow><bold>Stapgrootte: " + st, "", "<gray>Zo veel verandert er per klik.", "<gray>Opties: <white>1, 5, 10, 25, 50, 100", "", "<yellow>Klik <gray>» volgende"), (pp, ct) -> {
            stepIdx.put(pp.getUniqueId(), (stepIdx.getOrDefault(pp.getUniqueId(), 0) + 1) % STEPS.length);
            it().tick(pp);
            g.refresh();
        });
        g.set(g.last(5), it().icon(Material.PAPER, "<white><bold>Let op", "", "<gray>Verhoog je een eis, dan houden", "<gray>spelers wat ze al hebben.", "<gray>Zodra iemand genoeg wint, krijgt", "<gray>hij de cosmetic vanzelf."));
        g.backButton(() -> hub(p));
    }

    // ------------------------------------------------------------ statistieken

    void stats(Player p) {
        Gui g = gui(p, 6, "Statistieken");
        g.open(p, () -> {
            g.set(3, it().icon(Material.SPYGLASS, "<aqua><bold>Speler zoeken", "", "<gray>Typ de naam van een speler.", "", "<yellow>Klik <gray>» naam typen"), (pp, ct) -> {
                it().tick(pp);
                pl.ask(pp, "<gray>Typ de <white>naam <gray>van de speler:", (p2, text) -> {
                    Stats.P s = pl.stats().findByName(text);
                    if (s == null) {
                        pl.say(p2, "<red>Die speler ken ik niet. <gray>Hij moet minstens één keer gejoind zijn.");
                        stats(p2);
                    } else {
                        statsEditor(p2, s.id);
                    }
                }, () -> stats(pp));
            });
            g.set(5, it().icon(Material.TNT, "<red><bold>Alles resetten", "", "<gray>Zet de statistieken van ALLE spelers", "<gray>op 0 (ontgrendelde cosmetics blijven).", "", "<yellow>Klik <gray>» openen"), (pp, ct) -> {
                it().tick(pp);
                confirmResetAll(pp);
            });
            List<Stats.P> all = pl.stats().all();
            all.removeIf(Stats.P::empty);
            all.sort((a, b) -> b.kills != a.kills ? Integer.compare(b.kills, a.kills) : a.name.compareToIgnoreCase(b.name));
            if (all.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Nog geen data", "<gray>Er zijn nog geen statistieken."));
            g.list(all, s -> it().head(Bukkit.getOfflinePlayer(s.id), "<gold><bold>" + Items.esc(s.name), List.of(
                    "<gray>Kills <white>" + s.kills + " <dark_gray>• <gray>Deaths <white>" + s.deaths, "<gray>K/D <white>" + s.kdText() + " <dark_gray>• <gray>Reeks <white>" + s.streak, "", "<yellow>Klik <gray>» bewerken")), (s, ct) -> {
                it().tick(p);
                statsEditor(p, s.id);
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    private void confirmResetAll(Player p) {
        Gui g = gui(p, 3, "Alles resetten?");
        g.open(p, () -> {
            g.set(11, it().icon(Material.RED_CONCRETE, "<red><bold>JA, alles op 0", "", "<gray>Kills, deaths, reeksen en gelijkspel", "<gray>van <white>alle <gray>spelers worden 0.", "<red>Dit kan niet ongedaan gemaakt worden."), (pp, ct) -> {
                pl.stats().resetAllNumbers();
                if (pl.stats().save()) pl.say(pp, "<green>Alle statistieken staan weer op 0.");
                else pl.say(pp, "<red>Opslaan mislukt, kijk in de console.");
                pl.refreshOpenMenus();
                stats(pp);
            });
            g.set(15, it().icon(Material.LIME_CONCRETE, "<green><bold>Nee, terug", "", "<gray>Er verandert niets."), (pp, ct) -> stats(pp));
        });
    }

    void statsEditor(Player p, UUID target) {
        Gui g = gui(p, 5, "Speler bewerken");
        boolean[] confirm = new boolean[1];
        g.open(p, () -> {
            Stats.P s = pl.stats().get(target);
            if (s == null) {
                g.set(22, it().icon(Material.BARRIER, "<red><bold>Onbekende speler"));
                g.backButton(() -> stats(p));
                return;
            }
            g.set(4, it().head(Bukkit.getOfflinePlayer(target), "<gold><bold>" + Items.esc(s.name), List.of("<gray>K/D <white>" + s.kdText(), "<gray>Gelijkspel <white>" + s.draws, "<gray>Reeks <white>" + s.streak + " <dark_gray>(beste " + s.best + ")")));
            numberRow(g, p, 10, "Kills", Material.DIAMOND_SWORD, s, true);
            numberRow(g, p, 19, "Deaths", Material.SKELETON_SKULL, s, false);
            g.set(16, it().icon(Material.BLAZE_POWDER, "<yellow><bold>Reeks resetten", "", "<gray>Nu: <white>" + s.streak, "", "<yellow>Klik <gray>» op 0"), (pp, ct) -> {
                s.streak = 0;
                pl.stats().dirty = true;
                pl.stats().save();
                it().tick(pp);
                g.refresh();
            });
            g.set(25, it().icon(Material.BARRIER, "<yellow><bold>Ontgrendel-markeringen wissen", "", "<gray>Wis de lijst van cosmetics die via", "<gray>kills zijn gegeven. Cosmetics zelf", "<gray>blijven bij de speler.", "<gray>Nu: <white>" + s.unlocked.size(), "", "<yellow>Klik <gray>» wissen"), (pp, ct) -> {
                s.unlocked.clear();
                pl.stats().dirty = true;
                pl.stats().save();
                it().tick(pp);
                g.refresh();
            });
            g.set(31, it().icon(confirm[0] ? Material.RED_CONCRETE : Material.TNT, confirm[0] ? "<red><bold>Klik nogmaals om te resetten" : "<red><bold>Alles van deze speler resetten", "",
                    "<gray>Kills, deaths, reeksen en gelijkspel", "<gray>worden 0.", "", "<yellow>Klik <gray>» " + (confirm[0] ? "bevestigen" : "starten")), (pp, ct) -> {
                if (!confirm[0]) {
                    confirm[0] = true;
                    g.refresh();
                    return;
                }
                s.kills = 0;
                s.deaths = 0;
                s.streak = 0;
                s.best = 0;
                s.draws = 0;
                pl.stats().dirty = true;
                pl.stats().save();
                confirm[0] = false;
                pl.say(pp, "<green>Statistieken van <white>" + Items.esc(s.name) + "<green> staan weer op 0.");
                g.refresh();
            });
            g.backButton(() -> stats(p));
        });
    }

    private void numberRow(Gui g, Player p, int base, String label, Material icon, Stats.P s, boolean kills) {
        int[] deltas = {-10, -1, 1, 10};
        int[] slots = {base, base + 1, base + 3, base + 4};
        int value = kills ? s.kills : s.deaths;
        g.set(base + 2, it().icon(icon, "<gold><bold>" + label, "", "<white>" + value));
        for (int i = 0; i < 4; i++) {
            int d = deltas[i];
            g.set(slots[i], it().icon(d < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE, (d < 0 ? "<red><bold>" : "<green><bold>") + (d > 0 ? "+" : "") + d, "", "<yellow>Klik <gray>» " + label.toLowerCase(Locale.ROOT) + " aanpassen"), (pp, ct) -> {
                int now = kills ? s.kills : s.deaths;
                int n = Stats.clamp(now + d);
                if (kills) s.kills = n;
                else s.deaths = n;
                pl.stats().dirty = true;
                pl.stats().save();
                if (kills) {
                    Player online = Bukkit.getPlayer(s.id);
                    if (online != null) pl.rewards().evaluate(online);
                }
                it().tick(pp);
                g.refresh();
            });
        }
    }

    // ------------------------------------------------------------ gevechten en wachtrij

    void live(Player p, boolean queueTab) {
        Gui g = gui(p, 6, "Gevechten en wachtrij");
        boolean[] q = {queueTab};
        g.open(p, () -> {
            ItemStack m = it().icon(Material.IRON_SWORD, (q[0] ? "<gray><bold>" : "<gold><bold>") + "Gevechten <white>(" + pl.matches().all().size() + ")", "", q[0] ? "<yellow>Klik <gray>» bekijken" : "<green>Geselecteerd");
            ItemStack w = it().icon(Material.CLOCK, (q[0] ? "<gold><bold>" : "<gray><bold>") + "Wachtrij <white>(" + pl.queue().size() + ")", "", q[0] ? "<green>Geselecteerd" : "<yellow>Klik <gray>» bekijken");
            g.set(3, q[0] ? m : it().glow(m), (pp, ct) -> {
                q[0] = false;
                g.page = 0;
                g.refresh();
            });
            g.set(5, q[0] ? it().glow(w) : w, (pp, ct) -> {
                q[0] = true;
                g.page = 0;
                g.refresh();
            });
            if (!q[0]) {
                List<Match> ms = pl.matches().all();
                if (ms.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Geen gevechten", "<gray>Er loopt nu niets."));
                g.list(ms, mm -> it().icon(mm.arena.icon, "<gold><bold>" + Items.esc(mm.nameA) + " <red>vs <gold>" + Items.esc(mm.nameB), "",
                        "<gray>Kit: <white>" + Items.esc(mm.kit.name), "<gray>Arena: <white>" + Items.esc(mm.arena.name), "<gray>Fase: <white>" + mm.state.name().toLowerCase(Locale.ROOT),
                        "<gray>Duur: <white>" + Items.time(mm.elapsed), "", "<yellow>Linksklik <gray>» meekijken", "<red>Rechtsklik <gray>» beëindigen (gelijkspel)"), (mm, ct) -> {
                    if (right(ct)) {
                        pl.matches().forceEnd(mm);
                        pl.say(p, "<green>Wedstrijd wordt beëindigd.");
                        g.refresh();
                    } else {
                        String err = pl.matches().watch(p, mm);
                        if (err != null) pl.say(p, "<red>" + err);
                        else p.closeInventory();
                    }
                }, p);
            } else {
                List<Queue.Entry> es = pl.queue().all();
                if (es.isEmpty()) g.set(22, it().icon(Material.BARRIER, "<gray><bold>Niemand wacht", "<gray>De wachtrij is leeg."));
                g.list(es, e -> {
                    KitDef k = pl.kits().get(e.kitId());
                    ArenaDef a = e.arenaId() == null ? null : pl.arenas().get(e.arenaId());
                    return it().head(Bukkit.getOfflinePlayer(e.id()), "<gold><bold>" + Items.esc(e.name()), List.of("<gray>Kit: <white>" + (k == null ? "?" : Items.esc(k.name)),
                            "<gray>Arena: <white>" + (e.arenaId() == null ? "willekeurig" : a == null ? "?" : Items.esc(a.name)),
                            "<gray>Wacht: <white>" + Items.time((int) Math.max(0, (pl.now() - e.since()) / 1000)), "", "<red>Klik <gray>» uit de wachtrij halen"));
                }, (e, ct) -> {
                    pl.queue().remove(e.id());
                    Player v = Bukkit.getPlayer(e.id());
                    if (v != null) pl.say(v, "<yellow>Staff heeft je uit de wachtrij gehaald.");
                    it().tick(p);
                    g.refresh();
                }, p);
            }
            g.backButton(() -> hub(p));
        });
    }

    // ------------------------------------------------------------ instellingen

    void settings(Player p) {
        Gui g = gui(p, 6, "Instellingen");
        g.open(p, () -> {
            g.list(Settings.SPECS, s -> {
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>Nu: " + valueText(s));
                lore.add("");
                lore.add("<dark_gray>" + s.help());
                lore.add("");
                if (s.bool()) lore.add("<yellow>Klik <gray>» wisselen");
                else {
                    lore.add("<yellow>Linksklik <gray>» +" + s.step());
                    lore.add("<yellow>Rechtsklik <gray>» -" + s.step());
                    lore.add("<yellow>Shift <gray>» ×10");
                }
                lore.add("<yellow>Middelste klik <gray>» standaard");
                return it().icon(s.icon(), "<gold><bold>" + s.label(), lore);
            }, (s, ct) -> {
                if (ct == ClickType.MIDDLE) {
                    pl.settings().reset(s.key());
                } else if (s.bool()) {
                    pl.settings().setBool(s.key(), !pl.settings().bool(s.key()));
                } else {
                    int step = s.step() * (shift(ct) ? 10 : 1);
                    pl.settings().setNum(s.key(), pl.settings().num(s.key()) + (right(ct) ? -step : step));
                }
                it().tick(p);
                g.refresh();
            }, p);
            g.backButton(() -> hub(p));
        });
    }

    private String valueText(Settings.Spec s) {
        if (s.bool()) return pl.settings().bool(s.key()) ? "<green>aan" : "<red>uit";
        int v = pl.settings().num(s.key());
        return v == 0 && s.min() == 0 && s.max() > 1 && s.key().contains("seconden") ? "<white>uit / geen" : "<white>" + v;
    }
}
