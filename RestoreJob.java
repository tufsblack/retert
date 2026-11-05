package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Zet de arena terug naar de opgeslagen foto. Alleen blokken die anders zijn worden aangepast. */
final class RestoreJob implements Job {
    private final Arenas plugin;
    private final Region r;
    private final World w;
    private final Snap s;
    private final BlockData[] cache;
    private final Set<Integer> badPalette = new HashSet<>();
    private final int sx, sz;
    private final long total;
    private int i = 0;
    private Iterator<Map.Entry<Integer, byte[][]>> tileIt;
    int changed = 0, tilesFixed = 0, skipped = 0;
    final long started = System.nanoTime();

    RestoreJob(Arenas plugin, Region r, World w, Snap s) {
        this.plugin = plugin;
        this.r = r;
        this.w = w;
        this.s = s;
        this.cache = new BlockData[s.palette.length];
        this.total = r.volume();
        this.sx = r.sx();
        this.sz = r.sz();
    }

    public String name() { return r.name; }

    public boolean step(long deadline) throws Exception {
        while (i < total) {
            if ((i & 127) == 0 && System.nanoTime() > deadline) return false;
            int p = s.idx[i];
            if (!badPalette.contains(p)) {
                int x = r.x1 + i % sx, z = r.z1 + (i / sx) % sz, y = r.y1 + i / (sx * sz);
                Block b = w.getBlockAt(x, y, z);
                String want = s.palette[p];
                if (!b.getBlockData().getAsString().equals(want)) {
                    BlockData d = cache[p];
                    if (d == null) {
                        try {
                            d = Bukkit.createBlockData(want);
                            cache[p] = d;
                        } catch (IllegalArgumentException e) {
                            badPalette.add(p);
                            plugin.getLogger().warning("Onbekend blok in opslag, overgeslagen: " + want);
                        }
                    }
                    if (d != null) {
                        b.setBlockData(d, false);
                        changed++;
                    }
                }
            }
            i++;
        }
        if (tileIt == null) tileIt = s.tiles.entrySet().iterator();
        int n = 0;
        while (tileIt.hasNext()) {
            if ((++n & 15) == 0 && System.nanoTime() > deadline) return false;
            Map.Entry<Integer, byte[][]> e = tileIt.next();
            int bi = e.getKey();
            if (badPalette.contains(s.idx[bi])) continue;
            int x = r.x1 + bi % sx, z = r.z1 + (bi / sx) % sz, y = r.y1 + bi / (sx * sz);
            BlockState st = w.getBlockAt(x, y, z).getState();
            if (!(st instanceof Container c)) { skipped++; continue; }
            ItemStack[] want = new ItemStack[e.getValue().length];
            for (int k = 0; k < want.length; k++) {
                byte[] raw = e.getValue()[k];
                if (raw != null) {
                    try { want[k] = ItemStack.deserializeBytes(raw); }
                    catch (RuntimeException ex) { skipped++; }
                }
            }
            Inventory inv = c.getSnapshotInventory();
            if (inv.getSize() != want.length) { skipped++; continue; }
            if (!sameItems(inv.getContents(), want)) {
                inv.setContents(want);
                c.update(true, false);
                tilesFixed++;
            }
        }
        return true;
    }

    static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) return false;
        for (int k = 0; k < a.length; k++) {
            boolean ea = a[k] == null || a[k].getType().isAir(), eb = b[k] == null || b[k].getType().isAir();
            if (ea != eb) return false;
            if (!ea && !a[k].equals(b[k])) return false;
        }
        return true;
    }

    public void finish(boolean ok) {
        plugin.restoreDone(r, ok, this, (System.nanoTime() - started) / 1_000_000L);
    }
}
