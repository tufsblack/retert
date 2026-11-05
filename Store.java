package nl.beneluxpvp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Wat staff in het menu instelt: hoeveel kills elke cosmetic kost, en de overschreven instellingen.
 * Staat in staff.yml. De standaardwaarden voor de instellingen staan in config.yml.
 */
final class Store {
    static final int MAX_NEED = 100_000;

    private final Map<String, Integer> needs = new LinkedHashMap<>();
    private final Map<String, String> over = new LinkedHashMap<>();
    private final File file;
    private final Logger log;

    Store(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    void load() {
        needs.clear();
        over.clear();
        if (!file.isFile()) return;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            for (String s : y.getStringList("eisen")) {
                int i = s.lastIndexOf('=');
                if (i <= 0) continue;
                try {
                    int n = Integer.parseInt(s.substring(i + 1).trim());
                    if (n >= 1 && n <= MAX_NEED) needs.put(s.substring(0, i).trim().toLowerCase(Locale.ROOT), n);
                } catch (NumberFormatException ignored) {
                    log.warning("Ongeldige eis in staff.yml overgeslagen: " + s);
                }
            }
            for (String s : y.getStringList("instellingen")) {
                int i = s.indexOf('=');
                if (i > 0) over.put(s.substring(0, i).trim(), s.substring(i + 1).trim());
            }
        } catch (Exception e) {
            log.severe("staff.yml is kapot en wordt niet gebruikt (er wordt een kopie bewaard): " + e);
            backupBroken(file);
        }
    }

    boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> n = new ArrayList<>();
        for (Map.Entry<String, Integer> e : needs.entrySet()) n.add(e.getKey() + "=" + e.getValue());
        y.set("eisen", n);
        List<String> o = new ArrayList<>();
        for (Map.Entry<String, String> e : over.entrySet()) o.add(e.getKey() + "=" + e.getValue());
        y.set("instellingen", o);
        return writeAtomic(file, y.saveToString(), log);
    }

    /** Schrijft een tekstbestand veilig weg (eerst naar een tijdelijk bestand). */
    static boolean writeAtomic(File file, String content, Logger log) {
        try {
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            Path tmp = Paths.get(file.getPath() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            log.severe(file.getName() + " kon niet opgeslagen worden: " + e);
            return false;
        }
    }

    static void backupBroken(File file) {
        try {
            Files.copy(file.toPath(), Paths.get(file.getPath() + ".kapot"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // geen kopie is jammer maar niet erg
        }
    }

    /** Aantal kills dat nodig is, of 0 als er geen eis is. */
    int need(String key) {
        Integer n = needs.get(key.toLowerCase(Locale.ROOT));
        return n == null ? 0 : n;
    }

    /** Zet de eis. 0 of lager haalt hem weg. Boven het maximum wordt afgekapt. */
    void setNeed(String key, int n) {
        String k = key.toLowerCase(Locale.ROOT);
        if (n <= 0) needs.remove(k);
        else needs.put(k, Math.min(MAX_NEED, n));
    }

    Map<String, Integer> all() {
        return Collections.unmodifiableMap(needs);
    }

    String raw(String key) {
        return over.get(key);
    }

    void setRaw(String key, String value) {
        if (value == null) over.remove(key);
        else over.put(key, value);
    }
}
