package com.atakmap.android.haztak.plugin.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Loads the bundled ERG dataset once for all tests. */
final class TestData {

    private static Erg erg;

    private TestData() {
    }

    static synchronized Erg erg() {
        if (erg == null) {
            try {
                File f = new File("src/main/assets/erg2024.json");
                if (!f.exists())
                    f = new File("app/src/main/assets/erg2024.json");
                erg = Erg.parse(new String(Files.readAllBytes(f.toPath()),
                        StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return erg;
    }
}
