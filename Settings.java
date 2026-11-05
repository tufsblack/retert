package nl.beneluxpvp;

import org.bukkit.Material;

import java.util.*;

/** Instellingen die staff in het menu aanpast (staff.yml); anders geldt config.yml, en anders de standaardwaarde. */
final class Settings {
    /** Eén instelling. Bij een aan/uit-instelling zijn min/max/step niet van belang. */
    record Spec(String key, String label, boolean bool, int def, int min, int max, int step, Material icon, String help) {
        static Spec flag(String key, String label, boolean def, Material icon, String help) {
            return new Spec(key, label, true, def ? 1 : 0, 0, 1, 1, icon, help);
        }

        static Spec num(String key, String label, int def, int min, int max, int step, Material icon, String help) {
            return new Spec(key, label, false, def, min, max, step, icon, help);
        }
    }

    static final List<Spec> SPECS = List.of(
            Spec.flag("wachtrij.aan", "Wachtrij", true, Material.CLOCK, "Zonder wachtrij kan alleen nog uitgedaagd worden."),
            Spec.flag("uitdagen.aan", "Uitdagen", true, Material.IRON_SWORD, "Spelers kunnen elkaar uitdagen."),
            Spec.flag("kijken.aan", "Kijken", true, Material.SPYGLASS, "Spelers kunnen wedstrijden bekijken."),
            Spec.num("wedstrijd.aftellen", "Aftellen (sec)", 5, 0, 30, 1, Material.REDSTONE_TORCH, "Zo lang staan spelers stil voor het begin."),
            Spec.num("wedstrijd.verzadiging", "Verzadiging (0-20)", 0, 0, 20, 1, Material.COOKED_BEEF, "0 = trage regeneratie (alleen gouden appels helpen echt). Hoog = heel snel genezen."),
            Spec.num("wedstrijd.einde-vertraging", "Eind-vertraging (sec)", 3, 1, 15, 1, Material.HOPPER, "Wachttijd na het einde voor iedereen terug gaat."),
            Spec.num("wedstrijd.max-seconden", "Max. duur (sec)", 600, 0, 3600, 30, Material.COMPASS, "Daarna is het gelijkspel. 0 = geen limiet."),
            Spec.num("wachtrij.max-seconden", "Max. wachttijd (sec)", 600, 0, 3600, 30, Material.SAND, "Daarna gaat een speler uit de wachtrij. 0 = nooit."),
            Spec.num("uitdagen.verloopt-seconden", "Uitdaging verloopt (sec)", 60, 10, 600, 10, Material.PAPER, "Hoe lang een uitdaging open blijft."),
            Spec.num("ranglijst.min-wedstrijden", "K/D: min. wedstrijden", 5, 0, 1000, 1, Material.NAME_TAG, "Zo veel wedstrijden nodig voor de K/D-ranglijst."),
            Spec.flag("tellen.aan", "Statistieken tellen", true, Material.LEVER, "Uit = niemand krijgt kills of deaths erbij."),
            Spec.flag("tellen.zelfde-ip-telt-niet", "Zelfde IP telt niet", true, Material.BARRIER, "Tegen alt-accounts."),
            Spec.num("tellen.zelfde-slachtoffer-seconden", "Zelfde tegenstander (sec)", 300, 0, 86400, 60, Material.CLOCK, "Dezelfde tegenstander binnen deze tijd telt niet. 0 = uit."),
            Spec.num("tellen.max-per-uur", "Max. winsten per uur", 30, 0, 1000, 5, Material.HOPPER, "Meer winsten per uur tellen niet. 0 = uit."),
            Spec.num("tellen.speler-min-online-seconden", "Min. online (sec)", 0, 0, 3600, 30, Material.SOUL_LANTERN, "Een tegenstander die net online kwam telt niet. 0 = uit."),
            Spec.num("tellen.speler-na-dood-seconden", "Na dood (sec)", 0, 0, 3600, 5, Material.WITHER_SKELETON_SKULL, "Een tegenstander die net verloor telt niet. 0 = uit."));

    private final BeneluxPvP pl;
    private final Map<String, Spec> byKey = new HashMap<>();

    Settings(BeneluxPvP pl) {
        this.pl = pl;
        for (Spec s : SPECS) byKey.put(s.key(), s);
    }

    Spec spec(String key) {
        return byKey.get(key);
    }

    boolean bool(String key) {
        Spec s = byKey.get(key);
        boolean def = s != null && s.def() == 1;
        String raw = pl.store().raw(key);
        if (raw != null) return raw.equalsIgnoreCase("true");
        return pl.getConfig().getBoolean(key, def);
    }

    int num(String key) {
        Spec s = byKey.get(key);
        int def = s == null ? 0 : s.def();
        String raw = pl.store().raw(key);
        if (raw != null) {
            try {
                return clamp(key, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // kapotte waarde: val terug op config
            }
        }
        return clamp(key, pl.getConfig().getInt(key, def));
    }

    void setBool(String key, boolean v) {
        pl.store().setRaw(key, String.valueOf(v));
        pl.store().save();
    }

    void setNum(String key, int v) {
        pl.store().setRaw(key, String.valueOf(clamp(key, v)));
        pl.store().save();
    }

    void reset(String key) {
        pl.store().setRaw(key, null);
        pl.store().save();
    }

    int clamp(String key, int v) {
        Spec s = byKey.get(key);
        if (s == null) return v;
        return Math.max(s.min(), Math.min(s.max(), v));
    }
}
