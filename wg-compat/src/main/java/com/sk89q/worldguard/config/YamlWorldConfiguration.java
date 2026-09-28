// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.config;

import com.sk89q.util.yaml.YAMLProcessor;

import java.util.List;

/**
 * WorldGuard's YAML-backed per-world configuration. No WorldGuard YAML is read, so every getter
 * answers with the default it is given.
 */
public abstract class YamlWorldConfiguration extends WorldConfiguration {

    protected YAMLProcessor parentConfig;
    protected YAMLProcessor config;

    public YamlWorldConfiguration() {
    }

    public boolean getBoolean(final String node, final boolean def) {
        return def;
    }

    public String getString(final String node, final String def) {
        return def;
    }

    public int getInt(final String node, final int def) {
        return def;
    }

    public List<Integer> getIntList(final String node, final List<Integer> def) {
        return def;
    }

    public List<String> getStringList(final String node, final List<String> def) {
        return def;
    }

    public List<String> getKeys(final String node) {
        return List.of();
    }

    public Object getProperty(final String node) {
        return null;
    }
}
