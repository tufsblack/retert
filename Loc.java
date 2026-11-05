package nl.beneluxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** Een plek in een wereld (met kijkrichting), op te slaan als tekst. */
record Loc(String world, double x, double y, double z, float yaw, float pitch) {
    static Loc of(Location l) {
        World w = l.getWorld();
        return new Loc(w == null ? "" : w.getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Null als de wereld niet (meer) geladen is. */
    Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    String serialize() {
        return world + ";" + String.format(Locale.ROOT, "%.3f;%.3f;%.3f;%.1f;%.1f", x, y, z, yaw, pitch);
    }

    static Loc parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] p = s.split(";");
            if (p.length != 6 || p[0].isEmpty()) return null;
            double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]), z = Double.parseDouble(p[3]);
            float yaw = Float.parseFloat(p[4]), pitch = Float.parseFloat(p[5]);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) return null;
            return new Loc(p[0], x, y, z, yaw, pitch);
        } catch (RuntimeException e) {
            return null;
        }
    }

    String pretty() {
        return String.format(Locale.ROOT, "%.0f, %.0f, %.0f", x, y, z);
    }
}
