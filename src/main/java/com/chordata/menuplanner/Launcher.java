package com.chordata.menuplanner;

/**
 * Plain main class so the shaded (fat) jar can start without
 * JavaFX module-path ceremony.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MenuPlannerApp.main(args);
    }
}
