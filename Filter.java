package nl.beneluxpvp;

import org.bukkit.Material;

/** De tabbladen in de beloningen-menu's. */
enum Filter {
    ALL(null, "Alles", Material.NETHER_STAR),
    CROWN("crown", "Kronen", Material.GOLDEN_HELMET),
    HAT("hat", "Hoeden", Material.LEATHER_HELMET),
    KILL("kill", "Kill-effecten", Material.DIAMOND_SWORD),
    BOW("bow", "Boog-effecten", Material.BOW);

    final String catKey;
    final String label;
    final Material icon;

    Filter(String catKey, String label, Material icon) {
        this.catKey = catKey;
        this.label = label;
        this.icon = icon;
    }

    boolean matches(Bridge.Row r) {
        return catKey == null || catKey.equals(r.catKey());
    }
}
