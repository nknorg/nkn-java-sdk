package jsmith.nknsdk.network;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;
import jsmith.nknsdk.client.NKNExplorerException;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class HttpApi {

    private static final Logger LOG = LoggerFactory.getLogger(HttpApi.class);

    private static final Webb webb = new Webb(null);

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

    public static Object rpcRequest(InetSocketAddress server, String method) throws NKNExplorerException {
        return rpcRequest(server, method, new HashMap<>());
    }

    public static Object rpcRequest(InetSocketAddress server, String method, HashMap<String, Object> params) throws NKNExplorerException {
        final JSONObject paramsJson = new JSONObject();

        for (Map.Entry<String, Object> e : params.entrySet()) {
            paramsJson.put(e.getKey(), e.getValue());
        }

        final JSONObject response = rpcCallJson(server, method, paramsJson);

        if (response.has("error")) {
            throw new NKNExplorerException(method, paramsJson, response.get("error"));
        }
        if (!response.has("result")) {
            throw new NKNExplorerException(method, paramsJson, "Missing field: 'result'");
        }

        return response.get("result");
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
