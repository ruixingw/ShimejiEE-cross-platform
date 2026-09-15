package com.group_finity.mascot.environment;

import java.util.List;

/**
 * Title based filter deciding which windows mascots can interact with.
 * <p>
 * An empty whitelist accepts every title. Blacklist entries always win.
 * Entries match by substring (the standard shimeji behavior).
 */
public final class WindowTitleFilter {

    public static final WindowTitleFilter ALLOW_ALL = new WindowTitleFilter(List.of(), List.of());

    private final List<String> whitelist;
    private final List<String> blacklist;

    private WindowTitleFilter(List<String> whitelist, List<String> blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public static WindowTitleFilter of(List<String> whitelist, List<String> blacklist) {
        if ((whitelist == null || whitelist.isEmpty()) && (blacklist == null || blacklist.isEmpty())) {
            return ALLOW_ALL;
        }
        return new WindowTitleFilter(
                whitelist == null ? List.of() : List.copyOf(whitelist),
                blacklist == null ? List.of() : List.copyOf(blacklist));
    }

    /**
     * Parses a slash separated caption list ({@code Chat/Notepad/Friends}).
     */
    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("/"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean test(String title) {
        if (title == null) {
            return false;
        }
        for (String black : blacklist) {
            if (title.contains(black)) {
                return false;
            }
        }
        if (whitelist.isEmpty()) {
            return true;
        }
        for (String white : whitelist) {
            if (title.contains(white)) {
                return true;
            }
        }
        return false;
    }
}
