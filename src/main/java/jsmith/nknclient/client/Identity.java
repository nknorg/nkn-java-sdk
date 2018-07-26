package jsmith.nknclient.client;

/**
 *
 */
public class Identity {

    // TODO Very much entire identity class

    public final String name;
    public Identity(String name) {
        this.name = name;
    }

    public String getFullIdentifier() {
        if (name == null || name.isEmpty()) return getPublicKeyAsString();
        return name + "." + getPublicKeyAsString();
    }

    public String getPublicKeyAsString() {
        return "02a23c8510fd6bb40a3b87cc72f3fc247a0497bb6e57628053707e38c587d9a992";
    }

    public String singStringAsString(String data) {
        return "";
    }

}
