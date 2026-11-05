package nl.beneluxpvp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Een opgeslagen "origineel" van een arena: blokken (palet + RLE) en kist-inhoud. */
final class Snap {
    final String[] palette;
    final int[] idx;
    /** blokindex -> items per slot (null = leeg slot) */
    final Map<Integer, byte[][]> tiles;

    Snap(String[] palette, int[] idx, Map<Integer, byte[][]> tiles) {
        this.palette = palette;
        this.idx = idx;
        this.tiles = tiles;
    }

    private static final int MAGIC = 0x41525331; // "ARS1"

    static void write(File f, Snap s) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            o.writeInt(MAGIC);
            o.writeInt(s.idx.length);
            o.writeInt(s.palette.length);
            for (String p : s.palette) o.writeUTF(p);
            int i = 0;
            while (i < s.idx.length) {
                int v = s.idx[i], j = i + 1;
                while (j < s.idx.length && s.idx[j] == v) j++;
                o.writeInt(j - i);
                o.writeInt(v);
                i = j;
            }
            o.writeInt(s.tiles.size());
            for (Map.Entry<Integer, byte[][]> e : s.tiles.entrySet()) {
                o.writeInt(e.getKey());
                byte[][] slots = e.getValue();
                o.writeInt(slots.length);
                for (byte[] b : slots) {
                    if (b == null) o.writeInt(-1);
                    else { o.writeInt(b.length); o.write(b); }
                }
            }
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Leest een bestand en controleert of het precies {@code volume} blokken bevat. */
    static Snap read(File f, long volume) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            if (in.readInt() != MAGIC) throw new IOException("geen geldig opslagbestand");
            int total = in.readInt();
            if (total != volume) throw new IOException("de afmetingen van de arena zijn veranderd sinds de opslag (" + total + " <> " + volume + ")");
            int pc = in.readInt();
            if (pc < 0 || pc > total + 1) throw new IOException("kapot palet");
            String[] pal = new String[pc];
            for (int i = 0; i < pc; i++) pal[i] = in.readUTF();
            int[] idx = new int[total];
            int pos = 0;
            while (pos < total) {
                int run = in.readInt(), v = in.readInt();
                if (run <= 0 || pos + (long) run > total || v < 0 || v >= pc) throw new IOException("kapotte blokdata");
                Arrays.fill(idx, pos, pos + run, v);
                pos += run;
            }
            int tc = in.readInt();
            if (tc < 0 || tc > total) throw new IOException("kapotte kistlijst");
            Map<Integer, byte[][]> tiles = new LinkedHashMap<>();
            for (int t = 0; t < tc; t++) {
                int bi = in.readInt(), n = in.readInt();
                if (bi < 0 || bi >= total || n < 0 || n > 256) throw new IOException("kapotte kist");
                byte[][] slots = new byte[n][];
                for (int k = 0; k < n; k++) {
                    int len = in.readInt();
                    if (len == -1) continue;
                    if (len < 0 || len > 1 << 24) throw new IOException("kapot item");
                    slots[k] = new byte[len];
                    in.readFully(slots[k]);
                }
                tiles.put(bi, slots);
            }
            return new Snap(pal, idx, tiles);
        }
    }
}
