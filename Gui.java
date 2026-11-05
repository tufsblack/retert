package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Eén openstaand menu. Elke knop heeft een eigen actie; het menu wordt na elke klik opnieuw opgebouwd waar nodig. */
final class Gui implements Menu {
    interface Click {
        void on(Player p, ClickType ct);
    }

    static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    final BeneluxPvP pl;
    final UUID viewer;
    final int rows;
    final boolean staff;
    final String title;
    Inventory inv;
    Runnable builder;
    int page = 0;
    private final Map<Integer, Click> actions = new HashMap<>();

    Gui(BeneluxPvP pl, Player viewer, int rows, String title, boolean staff) {
        this.pl = pl;
        this.viewer = viewer.getUniqueId();
        this.rows = Math.max(3, Math.min(6, rows));
        this.title = title;
        this.staff = staff;
    }

    int[] slots() {
        return Arrays.copyOf(CONTENT, (rows - 2) * 7);
    }

    int last(int col) {
        return (rows - 1) * 9 + col;
    }

    void open(Player p, Runnable builder) {
        this.builder = builder;
        inv = Bukkit.createInventory(this, rows * 9, pl.parse(title));
        refresh();
        p.openInventory(inv);
    }

    void refresh() {
        inv.clear();
        actions.clear();
        ItemStack black = pl.items().filler(Material.BLACK_STAINED_GLASS_PANE), red = pl.items().filler(Material.RED_STAINED_GLASS_PANE);
        int n = rows * 9;
        for (int i = 0; i < n; i++) inv.setItem(i, (i < 9 || i >= n - 9) ? red : black);
        if (builder != null) builder.run();
    }

    void set(int slot, ItemStack it, Click c) {
        inv.setItem(slot, it);
        if (c != null) actions.put(slot, c);
        else actions.remove(slot);
    }

    void set(int slot, ItemStack it) {
        set(slot, it, null);
    }

    /** Sluitknop onderin het midden. */
    void closeButton() {
        set(last(4), pl.items().icon(Material.BARRIER, "<red><bold>Sluiten"), (p, ct) -> p.closeInventory());
    }

    /** Terugknop onderin het midden. */
    void backButton(Runnable back) {
        set(last(4), pl.items().icon(Material.OAK_DOOR, "<gray><bold>Terug", "", "<yellow>Klik <gray>» terug"), (p, ct) -> {
            pl.items().tick(p);
            back.run();
        });
    }

    /** Zet een lijst in het menu met vorige/volgende pagina. */
    <T> void list(List<T> items, Function<T, ItemStack> icon, BiConsumer<T, ClickType> onClick, Player viewerPlayer) {
        int[] slots = slots();
        int pages = Items.pageCount(items.size(), slots.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < slots.length; i++) {
            int idx = page * slots.length + i;
            if (idx >= items.size()) break;
            T item = items.get(idx);
            set(slots[i], icon.apply(item), (p, ct) -> onClick.accept(item, ct));
        }
        if (page > 0) set(last(0), pl.items().icon(Material.ARROW, "<yellow>« Vorige pagina", "<dark_gray>Pagina " + page + "/" + pages), (p, ct) -> {
            page--;
            pl.items().tick(p);
            refresh();
        });
        if (page < pages - 1) set(last(8), pl.items().icon(Material.ARROW, "<yellow>Volgende pagina »", "<dark_gray>Pagina " + (page + 2) + "/" + pages), (p, ct) -> {
            page++;
            pl.items().tick(p);
            refresh();
        });
    }

    public Inventory getInventory() {
        return inv;
    }

    public void click(InventoryClickEvent e, Player p) {
        if (staff && !p.hasPermission(BeneluxPvP.ADMIN)) {
            p.closeInventory();
            return;
        }
        Click c = actions.get(e.getSlot());
        if (c != null) c.on(p, e.getClick());
    }
}
