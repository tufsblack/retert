package nl.beneluxpvp;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Een kit: wat een speler in een wedstrijd krijgt. Slot 0-35 = inventaris, 36-39 = laarzen/broek/borststuk/helm, 40 = tweede hand. */
final class KitDef {
    static final int SLOTS = 41;

    final String id;
    String name;
    Material icon = Material.IRON_SWORD;
    boolean enabled = true;
    final ItemStack[] items = new ItemStack[SLOTS];

    KitDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    int itemCount() {
        int n = 0;
        for (ItemStack i : items) if (i != null && !i.getType().isAir()) n++;
        return n;
    }

    void setItems(ItemStack[] src) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack it = i < src.length ? src[i] : null;
            items[i] = it == null || it.getType().isAir() ? null : it.clone();
        }
    }
}
