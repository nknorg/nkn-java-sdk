package jsmith.nknsdk.network;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;
import jsmith.nknsdk.wallet.Asset;
import org.bouncycastle.util.encoders.Hex;
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

    public static BigDecimal getBalance(InetSocketAddress server, String nknAddress, Asset asset) {
        final JSONObject params = new JSONObject();
        params.put("address", nknAddress);
//        params.put("assetid", asset.ID); // TODO: Is it possible to set assetid in devnet?

        final JSONObject response = rpcCallJson(server, "getbalancebyaddr", params);

        return response.getJSONObject("result").getBigDecimal("amount");
    }

    public static long getNonce(InetSocketAddress server, String nknAddress, Asset asset) {
        final JSONObject params = new JSONObject();
        params.put("address", nknAddress);
//        params.put("assetid", asset.ID); // TODO: Is it possible to set assetid in devnet?

        final JSONObject response = rpcCallJson(server, "getnoncebyaddr", params);

        return response.getJSONObject("result").getLong("nonce");
    }

    public static void sendRawTransaction(InetSocketAddress server, byte[] tx) {
        sendRawTransaction(server, Hex.toHexString(tx));
    }
    public static String sendRawTransaction(InetSocketAddress server, String tx) {
        final JSONObject params = new JSONObject();
        params.put("tx", tx);

        final JSONObject response = rpcCallJson(server, "sendrawtransaction", params);

        return response.has("error") ? null : response.getString("result");
    }

}
