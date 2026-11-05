package nl.beneluxpvp;

/** Een kubusvormig gebied (arena). Alle grenzen zijn inclusief. */
final class Region {
    final String name, world;
    final int x1, y1, z1, x2, y2, z2;

    Region(String name, String world, int ax, int ay, int az, int bx, int by, int bz) {
        this.name = name;
        this.world = world;
        this.x1 = Math.min(ax, bx); this.x2 = Math.max(ax, bx);
        this.y1 = Math.min(ay, by); this.y2 = Math.max(ay, by);
        this.z1 = Math.min(az, bz); this.z2 = Math.max(az, bz);
    }

    int sx() { return x2 - x1 + 1; }
    int sy() { return y2 - y1 + 1; }
    int sz() { return z2 - z1 + 1; }
    long volume() { return (long) sx() * sy() * sz(); }

    boolean contains(String w, double x, double y, double z) {
        return world.equals(w) && x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    /** Zoals {@link #contains}, maar de hoogte telt niet mee (omhoog geschoten worden met een windcharge mag, het gebied is oneindig hoog). */
    boolean containsXZ(String w, double x, double z) {
        return world.equals(w) && x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1;
    }

    String serialize() { return world + ";" + x1 + ";" + y1 + ";" + z1 + ";" + x2 + ";" + y2 + ";" + z2; }

    static Region parse(String name, String s) {
        if (s == null) return null;
        try {
            String[] p = s.split(";");
            if (p.length != 7) return null;
            return new Region(name, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
