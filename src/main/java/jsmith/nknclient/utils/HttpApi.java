package jsmith.nknclient.utils;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;
import jsmith.nknclient.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;

/**
 *
 */
public class HttpApi {

    private static final Webb webb = new Webb(null);

    public static String rpcCall(InetSocketAddress to, String method, JSONObject parameters) {
        return rpcCallJson(to, method, parameters).get("result").toString();
    }

    public static JSONObject rpcCallJson(InetSocketAddress to, String method, JSONObject parameters) {

        final JSONObject requestBody = new JSONObject();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("method", method);
        requestBody.put("params", parameters);

        Response<String> response = webb
                .post("http://" + to.getHostString() + ":" + to.getPort())
                .ensureSuccess()
                .bodyJson(requestBody.toString())
                .execute(ResponseTranslator.STRING_TRANSLATOR);

        System.out.println(response.getBody());

        return new JSONObject(response.getBody());
    }

    public static long getUTXO(InetSocketAddress server, Wallet w, String assetID) {

        final JSONObject params = new JSONObject();
        params.put("address", w.getAddressAsString());
        params.put("assetid", assetID);

        final Object response = rpcCallJson(server, "getunspendoutput", params);
        if (((JSONObject) response).isNull("result")) return 0;

        long value = 0;
        final JSONArray arr = ((JSONObject) response).getJSONArray("result");
        for (Object obj : arr) {
            value += ((JSONObject)obj).getInt("Value");
        }
        System.out.println(value);

        return value;
    }

}
