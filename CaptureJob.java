package nl.beneluxpvp;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Maakt een foto (snapshot) van de arena zoals hij nu is. */
final class CaptureJob implements Job {
    private final Arenas plugin;
    private final Region r;
    private final World w;
    private final int[] idx;
    private final Map<String, Integer> pal = new LinkedHashMap<>();
    private final List<Boolean> tileKind = new ArrayList<>();
    private final Map<Integer, byte[][]> tiles = new LinkedHashMap<>();
    private int i = 0;
    private final int sx, sz;
    private final long total;
    long started = System.nanoTime();

    CaptureJob(Arenas plugin, Region r, World w) {
        this.plugin = plugin;
        this.r = r;
        this.w = w;
        this.total = r.volume();
        this.idx = new int[(int) total];
        this.sx = r.sx();
        this.sz = r.sz();
    }

    public String name() { return r.name; }

    public boolean step(long deadline) throws Exception {
        while (i < total) {
            if ((i & 127) == 0 && System.nanoTime() > deadline) return false;
            int x = r.x1 + i % sx, z = r.z1 + (i / sx) % sz, y = r.y1 + i / (sx * sz);
            Block b = w.getBlockAt(x, y, z);
            String d = b.getBlockData().getAsString();
            Integer p = pal.get(d);
            if (p == null) {
                p = pal.size();
                pal.put(d, p);
                tileKind.add(b.getState() instanceof Container);
            }
            idx[i] = p;
            if (tileKind.get(p)) {
                BlockState st = b.getState();
                if (st instanceof Container c) {
                    ItemStack[] items = c.getSnapshotInventory().getContents();
                    byte[][] out = new byte[items.length][];
                    for (int k = 0; k < items.length; k++) {
                        ItemStack it = items[k];
                        if (it != null && !it.getType().isAir()) out[k] = it.serializeAsBytes();
                    }
                    tiles.put(i, out);
                }
            }
            i++;
        }
        return true;
    }

    public void finish(boolean ok) {
        if (!ok) { plugin.captureDone(r, false, 0, 0); return; }
        try {
            Snap s = new Snap(pal.keySet().toArray(new String[0]), idx, tiles);
            Snap.write(plugin.snapFile(r.name), s);
            plugin.captureDone(r, true, (System.nanoTime() - started) / 1_000_000L, tiles.size());
        } catch (Exception e) {
            plugin.getLogger().severe("Opslaan van '" + r.name + "' mislukt: " + e);
            plugin.captureDone(r, false, 0, 0);
        }
    }
}
