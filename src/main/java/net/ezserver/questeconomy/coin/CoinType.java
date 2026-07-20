package net.ezserver.questeconomy.coin;

/** Two coin tiers. Copper is the base; Silver is worth 3 Copper and only made at the Mint. */
public enum CoinType {
    COPPER("Copper", 1, 1001, "#e0913a"),   // orange
    SILVER("Silver", 3, 1002, "#c2c8ce");   // grey

    public final String display;
    public final int defaultValue;
    public final int defaultModelData;
    public final String hex;
    public final String key;

    CoinType(String display, int value, int modelData, String hex) {
        this.display = display;
        this.defaultValue = value;
        this.defaultModelData = modelData;
        this.hex = hex;
        this.key = name().toLowerCase();
    }

    public static CoinType byKey(String s) {
        if (s == null) return null;
        for (CoinType t : values()) if (t.key.equalsIgnoreCase(s)) return t;
        return null;
    }

    /** Highest value first: SILVER, COPPER. */
    public static CoinType[] descending() {
        return new CoinType[]{SILVER, COPPER};
    }

    /** Lowest value first: COPPER, SILVER. */
    public static CoinType[] ascending() {
        return new CoinType[]{COPPER, SILVER};
    }
}
