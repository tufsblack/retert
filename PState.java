package nl.beneluxpvp;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Hoe een speler was voordat hij een wedstrijd in ging, zodat alles precies teruggezet kan worden. */
final class PState {
    final UUID id;
    boolean hasInv = true;
    final ItemStack[] items = new ItemStack[KitDef.SLOTS];
    String mode = "SURVIVAL";
    double health = 20;
    int food = 20;
    float saturation = 5;
    int level;
    float exp;
    boolean allowFlight;
    Loc loc;
    /** De inventaris en dergelijke zijn al teruggezet (bij afmelden); alleen de plek moet nog. */
    boolean invDone;

    PState(UUID id) {
        this.id = id;
    }
}
