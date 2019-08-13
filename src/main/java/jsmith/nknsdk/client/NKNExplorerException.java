package jsmith.nknsdk.client;

import org.json.JSONObject;

public class NKNExplorerException extends Exception {

    public final String method;
    public final JSONObject params;
    public final Object error;

    public NKNExplorerException(String method, JSONObject params, Object error) {
        super(String.format("RPC failed:  \"%s\" with params: \"%s\" returned error: \"%s\"", method, params, error));

        this.method = method;
        this.params = params;
        this.error = error;
    }

    public NKNExplorerException(String message, Throwable cause) {
        super(message, cause);

        this.method = null;
        this.params = null;
        this.error = null;
    }
}
