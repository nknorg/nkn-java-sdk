package jsmith.nknclient.wallet;

/**
 *
 */
public class Asset {

    public static final Asset T_NKN = new Asset("tNKN", "4945ca009174097e6614d306b66e1f9cb1fce586cb857729be9e1c5cc04c9c02", 100000000);

    public final String name;
    public final String ID;
    public final int mul;

    public Asset(String name, String id, int mul) {
        this.name = name;
        this.ID = id;
        this.mul = mul;
    }

}
