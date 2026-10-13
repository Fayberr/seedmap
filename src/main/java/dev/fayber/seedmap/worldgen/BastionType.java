package dev.fayber.seedmap.worldgen;

import net.minecraft.resources.Identifier;

public enum BastionType {
    HOUSING("units", "Housing Units"), STABLES("hoglin_stable", "Hoglin Stables"),
    TREASURE("treasure", "Treasure Room"), BRIDGE("bridge", "Bridge");
    private final String templatePath;
    private final String label;
    BastionType(String templatePath, String label) { this.templatePath = templatePath; this.label = label; }
    public String label() { return label; }
    public Identifier markerId() { return Identifier.withDefaultNamespace("bastion_remnant_" + templatePath); }
    public static BastionType fromTemplate(Identifier id) {
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        for (var type : values()) if (id.getPath().startsWith("bastion/" + type.templatePath + "/")) return type;
        return null;
    }
    public static BastionType fromMarker(Identifier id) {
        for (var type : values()) if (type.markerId().equals(id)) return type;
        return null;
    }
}
