package jsmith.nknclient.wallet;

/**
 *
 */
public class Asset {

    public static final Asset T_NKN = new Asset("tNKN", "4945ca009174097e6614d306b66e1f9cb1fce586cb857729be9e1c5cc04c9c02");

    public final String name;
    public final String ID;

    public Asset(String name, String id) {
        this.name = name;
        this.ID = id;
    }

}
