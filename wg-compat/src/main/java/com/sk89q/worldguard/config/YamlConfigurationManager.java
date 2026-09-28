// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.config;

import com.sk89q.util.yaml.YAMLProcessor;

import java.io.File;

/**
 * WorldGuard's YAML-backed global configuration. uWorldGuard reads none of WorldGuard's YAML, so
 * loading does nothing and the public fields keep their stock defaults.
 */
public abstract class YamlConfigurationManager extends ConfigurationManager {

    private static final String COMPAT_FILE = "wg-compat-config.yml";

    private volatile YAMLProcessor config;

    public YamlConfigurationManager() {
    }

    public abstract void copyDefaults();

    @Override
    public void load() {
    }

    public void postLoad() {
    }

    /**
     * An empty processor over {@code wg-compat-config.yml} in the data folder. It is never loaded or
     * saved by uWorldGuard, and points at its own file so a consumer calling {@code save()} on it
     * cannot overwrite uWorldGuard's {@code config.yml}.
     */
    public YAMLProcessor getConfig() {
        YAMLProcessor current = config;
        if (current == null) {
            synchronized (this) {
                current = config;
                if (current == null) {
                    current = new YAMLProcessor(new File(getDataFolder(), COMPAT_FILE), false);
                    config = current;
                }
            }
        }
        return current;
    }

    @Override
    public void disableUuidMigration() {
    }
}
