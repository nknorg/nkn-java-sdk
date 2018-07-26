package jsmith.nknclient.utils;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;
import org.json.JSONObject;

import java.net.InetSocketAddress;

/**
 *
 */
public class HttpApi {

    private static final Webb webb = new Webb(null);

    public static String rpcCall(InetSocketAddress to, String method, JSONObject parameters) {

        final JSONObject requestBody = new JSONObject();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("method", method);
        requestBody.put("params", parameters);

        Response<String> response = webb
                .post("http://" + to.getHostString() + ":" + to.getPort())
                .ensureSuccess()
                .bodyJson(requestBody.toString())
                .execute(ResponseTranslator.STRING_TRANSLATOR);

        return new JSONObject(response.getBody()).get("result").toString();
    }

}
