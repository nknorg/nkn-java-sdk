package jsmith.nknsdk.client;

import org.json.JSONObject;

public class NKNHttpApiException extends NKNClientException {
    public NKNHttpApiException(String method, JSONObject params, JSONObject response) {
        super(String.format("API call to method \"%s\" with params: \"%s\" returned error: \"%s\"", method, response.get("error"), params));
    }
}
