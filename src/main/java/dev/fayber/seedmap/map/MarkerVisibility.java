package dev.fayber.seedmap.map;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Screen-space LOD policy for structure markers. Rendering, not world generation:
 * hidden marker types remain searchable and their locations are not modified.
 */
public final class MarkerVisibility {
    public static final int SPARSE = 0;
    public static final int BALANCED = 1;
    public static final int DETAILED = 2;
    public static final int ALL = 3;

    private MarkerVisibility() {}

    /** Primary points of interest worth keeping in the compact feature picker. */
    public static boolean isLandmark(Identifier id) {
        String path = id.getPath();
        return path.contains("stronghold") || path.contains("mansion")
                || path.contains("monument") || path.contains("ancient_city")
                || path.contains("end_city") || path.contains("bastion")
                || path.contains("fortress");
    }

    public static int clamp(int density) {
        return Math.clamp(density, SPARSE, ALL);
    }

    /** Maximum blocks per screen pixel at which the given type is displayed. */
    public static double cutoff(Identifier id, int density) {
        if (clamp(density) == ALL) return Double.POSITIVE_INFINITY;
        String path = id.getPath();
        double base;
        if (path.contains("stronghold") || path.contains("mansion") || path.contains("monument")
                || path.contains("ancient_city") || path.contains("end_city")) {
            base = 512.0;
        } else if (path.contains("bastion") || path.contains("fortress")) {
            // Fortresses and bastions are very dense in the Nether. Disappear
            // at wider zooms rather than compressing thousands into clusters.
            base = 8.0;
        } else if (path.contains("trial_chambers")) {
            base = 8.0;
        } else if (path.startsWith("village") || path.contains("pillager_outpost")
                || path.contains("desert_pyramid") || path.contains("jungle_pyramid")
                || path.contains("swamp_hut") || path.contains("igloo")) {
            base = 24.0;
        } else if (path.contains("mineshaft") || path.contains("ruined_portal")
                || path.contains("shipwreck") || path.contains("ocean_ruin")
                || path.contains("trail_ruins") || path.contains("buried_treasure")
                || path.contains("geode") || path.contains("nether_fossil")) {
            base = 6.0;
        } else {
            base = 12.0;
        }
        return base * switch (clamp(density)) {
            case SPARSE -> 0.5;
            case DETAILED -> 4.0;
            default -> 1.0;
        };
    }

    public static boolean visible(Identifier id, double blocksPerPixel, int density) {
        return blocksPerPixel <= cutoff(id, density);
    }

    public static Set<Identifier> enabledAtScale(Set<Identifier> enabled, double blocksPerPixel, int density) {
        if (clamp(density) == ALL) return Set.copyOf(enabled);
        Set<Identifier> result = new HashSet<>();
        for (Identifier id : enabled) {
            if (visible(id, blocksPerPixel, density)) result.add(id);
        }
        return result;
    }

    /** Important markers retain their actual map icons at wide zoom levels. */
    public static boolean iconAt(double blocksPerPixel, int density) {
        return blocksPerPixel <= (clamp(density) == ALL ? 256.0 : 512.0);
    }

    public static double clusterRadius(double blocksPerPixel) {
        return Math.clamp(12.0 + Math.sqrt(Math.max(1.0, blocksPerPixel)) * 1.75, 14.0, 48.0);
    }
}
