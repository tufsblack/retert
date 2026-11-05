package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/** Uitdagingen tussen twee spelers, buiten de wachtrij om. */
final class Requests {
    record Req(UUID from, String fromName, UUID to, String toName, String kitId, String arenaId, long expires) {
    }

    private final BeneluxPvP pl;
    private final List<Req> list = new ArrayList<>();

    Requests(BeneluxPvP pl) {
        this.pl = pl;
    }

    List<Req> incoming(UUID to) {
        List<Req> out = new ArrayList<>();
        for (Req r : list) if (r.to.equals(to)) out.add(r);
        return out;
    }

    Req find(UUID from, UUID to) {
        for (Req r : list) if (r.from.equals(from) && r.to.equals(to)) return r;
        return null;
    }

    int count(UUID to) {
        return incoming(to).size();
    }

    /** Verstuurt een uitdaging. {@code arena} null = willekeurig. Geeft een foutmelding of null. */
    String send(Player from, Player to, KitDef kit, ArenaDef arena) {
        if (!pl.settings().bool("uitdagen.aan")) return "Uitdagen staat uit.";
        if (to == null || !to.isOnline()) return "Die speler is niet online.";
        UUID f = from.getUniqueId(), t = to.getUniqueId();
        if (f.equals(t)) return "Je kunt jezelf niet uitdagen.";
        if (pl.matches().inMatch(f)) return "Je zit al in een wedstrijd.";
        if (pl.matches().inMatch(t)) return "Die speler zit al in een wedstrijd.";
        if (pl.stats().getOrCreate(t, to.getName()).noChallenges && !from.hasPermission(BeneluxPvP.ADMIN)) return "Die speler wil geen uitdagingen ontvangen.";
        if (kit == null || !kit.enabled || kit.itemCount() == 0) return "Deze kit is niet beschikbaar.";
        if (arena != null) {
            if (!pl.arenas().usable(arena)) return "Deze arena is niet beschikbaar.";
            if (!arena.allows(kit.id)) return "Deze kit mag niet in deze arena.";
        } else if (pl.queue().availableArenas(kit).isEmpty()) {
            return "Er is geen arena beschikbaar voor deze kit.";
        }
        if (find(f, t) != null) return "Je hebt deze speler al uitgedaagd. Wacht even op een antwoord.";
        long expires = pl.now() + pl.settings().num("uitdagen.verloopt-seconden") * 1000L;
        list.add(new Req(f, from.getName(), t, to.getName(), kit.id, arena == null ? null : arena.id, expires));
        String an = Items.esc(from.getName());
        String where = arena == null ? "willekeurig" : Items.esc(arena.name);
        pl.say(from, "<green>Uitdaging gestuurd naar <white>" + Items.esc(to.getName()) + "<green>. <gray>Kit <white>" + Items.esc(kit.name) + " <dark_gray>• <gray>Arena <white>" + where);
        pl.say(to, "<gold>⚔ <white>" + an + " <gray>daagt je uit! <dark_gray>• <gray>Kit <white>" + Items.esc(kit.name) + " <dark_gray>• <gray>Arena <white>" + where);
        to.sendMessage(pl.parse("<dark_gray> » <green><click:run_command:'/duel accept " + from.getName() + "'><hover:show_text:'Accepteren'>[ACCEPTEREN]</hover></click> "
                + "<red><click:run_command:'/duel deny " + from.getName() + "'><hover:show_text:'Weigeren'>[WEIGEREN]</hover></click> <dark_gray>of open <white>/pvp"));
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        pl.refreshOpenMenus();
        return null;
    }

    /** De uitgedaagde accepteert. Geeft een foutmelding of null. */
    String accept(Player to, UUID fromId) {
        Req r = find(fromId, to.getUniqueId());
        if (r == null) return "Die uitdaging bestaat niet (meer).";
        Player from = Bukkit.getPlayer(fromId);
        if (from == null) {
            list.remove(r);
            return "Die speler is niet meer online.";
        }
        KitDef kit = pl.kits().get(r.kitId);
        if (kit == null || !kit.enabled) {
            list.remove(r);
            return "Deze kit is niet meer beschikbaar.";
        }
        ArenaDef arena;
        if (r.arenaId != null) {
            arena = pl.arenas().get(r.arenaId);
            if (arena == null || !pl.arenas().usable(arena)) {
                list.remove(r);
                return "Deze arena is niet meer beschikbaar.";
            }
            if (!pl.arenas().playable(arena)) return "Deze arena is nu bezig. Probeer het zo nog eens (de uitdaging blijft open).";
        } else {
            List<ArenaDef> free = new ArrayList<>();
            for (ArenaDef a : pl.arenas().all()) if (pl.arenas().playable(a) && a.allows(kit.id)) free.add(a);
            if (free.isEmpty()) return "Alle arena's zijn nu bezig. Probeer het zo nog eens (de uitdaging blijft open).";
            arena = free.get(pl.random().nextInt(free.size()));
        }
        list.remove(r);
        String err = pl.matches().start(from, to, kit, arena);
        if (err != null) {
            pl.say(from, "<red>De wedstrijd kon niet starten: " + err);
            return err;
        }
        return null;
    }

    boolean deny(Player to, UUID fromId) {
        Req r = find(fromId, to.getUniqueId());
        if (r == null) return false;
        list.remove(r);
        Player from = Bukkit.getPlayer(fromId);
        if (from != null) pl.say(from, "<red>" + Items.esc(to.getName()) + " <gray>heeft je uitdaging geweigerd.");
        pl.refreshOpenMenus();
        return true;
    }

    /** Verwijdert alle uitdagingen van en naar deze speler. */
    void clearFor(UUID id) {
        if (list.removeIf(r -> r.from.equals(id) || r.to.equals(id))) pl.refreshOpenMenus();
    }

    void tick() {
        long now = pl.now();
        boolean changed = false;
        for (Req r : new ArrayList<>(list)) {
            if (now < r.expires) continue;
            list.remove(r);
            changed = true;
            Player from = Bukkit.getPlayer(r.from);
            if (from != null) pl.say(from, "<yellow>Je uitdaging aan <white>" + Items.esc(r.toName) + " <yellow>is verlopen.");
        }
        if (changed) pl.refreshOpenMenus();
    }
}
