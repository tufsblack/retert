package nl.beneluxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/** Een openstaand menu. De plugin annuleert elke klik en geeft hem door. */
interface Menu extends InventoryHolder {
    void click(InventoryClickEvent e, Player p);
}
