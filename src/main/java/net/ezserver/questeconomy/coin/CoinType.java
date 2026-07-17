package net.ezserver.questeconomy.coin;

/** The four coin tiers. Canonical values/model-data live here; config can override amounts. */
public enum CoinType {
    COPPER("Copper", 1, 1001, "#c87f3a"),
    SILVER("Silver", 2, 1002, "#b8c0c6"),
    GOLD("Gold", 5, 1003, "#e8c14a"),
    DIAMOND("Diamond", 10, 1004, "#4fd6c8");

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

    /** Highest value first: DIAMOND, GOLD, SILVER, COPPER. */
    public static CoinType[] descending() {
        return new CoinType[]{DIAMOND, GOLD, SILVER, COPPER};
    }

    /** Lowest value first: COPPER, SILVER, GOLD, DIAMOND. */
    public static CoinType[] ascending() {
        return new CoinType[]{COPPER, SILVER, GOLD, DIAMOND};
    }
}
