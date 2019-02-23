package jsmith.nknsdk.network;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;
import jsmith.nknsdk.wallet.Asset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.InetSocketAddress;

/**
 *
 */
public class HttpApi {

    private static final Logger LOG = LoggerFactory.getLogger(HttpApi.class);

    private static final Webb webb = new Webb(null);

    public static String rpcCall(InetSocketAddress to, String method, JSONObject parameters) {
        final JSONObject result = rpcCallJson(to, method, parameters);
        if (result.has("result")) return result.getString("result");

        LOG.warn("Invalid response format, does not contain field result: {}", result);

        return null;
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
                .connectTimeout(ConnectionProvider.rpcCallTimeoutMS())
                .readTimeout(ConnectionProvider.rpcCallTimeoutMS())
                .execute(ResponseTranslator.STRING_TRANSLATOR);

        return new JSONObject(response.getBody());
    }

    public static BigDecimal getSumUTXO(InetSocketAddress server, String nknAddress, Asset asset) {
        BigDecimal value = new BigDecimal("0");

        final JSONArray arr = getListUTXO(server, nknAddress, asset);
        if (arr == null) return value;
        for (Object obj : arr) {
            value = value.add(((JSONObject)obj).getBigDecimal("Value"));
        }

        return value;
    }

    public static JSONArray getListUTXO(InetSocketAddress server, String nknAddress, Asset asset) {

        final JSONObject params = new JSONObject();
        params.put("address", nknAddress);
        params.put("assetid", asset.ID);

        final JSONObject response = rpcCallJson(server, "getunspendoutput", params);
        if (response.isNull("result")) return null;

        return response.getJSONArray("result");
    }

}
