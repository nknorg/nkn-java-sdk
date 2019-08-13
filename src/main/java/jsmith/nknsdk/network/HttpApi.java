package jsmith.nknsdk.network;

import com.darkyen.dave.Response;
import com.darkyen.dave.ResponseTranslator;
import com.darkyen.dave.Webb;

import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.client.NKNExplorer.GetWsAddrResult;
import jsmith.nknsdk.client.NKNHttpApiException;
import jsmith.nknsdk.wallet.WalletException;

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
    
    public static int getBlockCount(InetSocketAddress server) throws NKNClientException {
        final JSONObject params = new JSONObject();

        final String apiMethod = "getblockcount";
        final JSONObject response = rpcCallJson(server, apiMethod, params);
      
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }
        
        return response.getInt("result");
    }
    
    public static int getFirstAvailableTopicBucket(InetSocketAddress server, String topic) throws NKNClientException {
        final JSONObject params = new JSONObject();
        params.put("topic", topic);

        final String apiMethod = "getfirstavailabletopicbucket";
        final JSONObject response = rpcCallJson(server, apiMethod, params);
      
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }
        
        return response.getInt("result");
    }
    
    public static NKNExplorer.GetLatestBlockHashResult getLatestBlockHash(InetSocketAddress server) throws NKNClientException {
        final JSONObject params = new JSONObject();

        final String apiMethod = "getlatestblockhash";
        final JSONObject response = rpcCallJson(server, apiMethod, params);
        final JSONObject result = response.getJSONObject("result");
      
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }
        
        return new NKNExplorer.GetLatestBlockHashResult(result.getString("hash"), result.getInt("height"));
    }

    public static int getTopicBucketsCount(InetSocketAddress server, String topic) throws NKNClientException {
        final JSONObject params = new JSONObject();
        params.put("topic", topic);

        final String apiMethod = "gettopicbucketscount";
        final JSONObject response = rpcCallJson(server, apiMethod, params);
      
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }

        return response.getInt("result");
    }

    public static NKNExplorer.Subscriber[] getSubscribers(InetSocketAddress server, String topic, int bucket) {
        final JSONObject params = new JSONObject();
        params.put("topic", topic);
        params.put("bucket", bucket);

        final JSONObject response = rpcCallJson(server, "getsubscribers", params);
        final JSONObject result = response.getJSONObject("result");

        final NKNExplorer.Subscriber[] subscribers = new NKNExplorer.Subscriber[result.length()];

        int i = 0;
        for (String id : result.keySet()) {
            subscribers[i++] = new NKNExplorer.Subscriber(id, result.getString(id));
        }

        return subscribers;
    }
    
    public static GetWsAddrResult getWsAddr(InetSocketAddress server, String address) throws NKNHttpApiException {
        final String apiMethod = "getwsaddr";
        
        final JSONObject params = new JSONObject();
        params.put("address", address);
        final JSONObject response = rpcCallJson(server, apiMethod, params);
        final JSONObject result = response.getJSONObject("result");
        
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }
        
        return new NKNExplorer.GetWsAddrResult(result.getString("id"), result.getString("addr"), result.getString("pubkey"));
    }  

    public static BigDecimal getBalance(InetSocketAddress server, String nknAddress) {
        final JSONObject params = new JSONObject();
        params.put("address", nknAddress);
//        params.put("assetid", asset.ID); // TODO: Is it possible to set assetid in devnet?

        final JSONObject response = rpcCallJson(server, "getbalancebyaddr", params);

        return response.getJSONObject("result").getBigDecimal("amount");
    }

    public static long getNonce(InetSocketAddress server, String nknAddress) {
        final JSONObject params = new JSONObject();
        params.put("address", nknAddress);

        final JSONObject response = rpcCallJson(server, "getnoncebyaddr", params);

        long nonce = response.getJSONObject("result").getLong("nonce");
        if (response.getJSONObject("result").has("nonceInTxPool")) {
            nonce = Math.max(nonce, response.getJSONObject("result").getLong("nonceInTxPool"));
        }
        return nonce;
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

    public static String resolveName(InetSocketAddress server, String name) {
        final JSONObject params = new JSONObject();
        params.put("name", name);

        final JSONObject response = rpcCallJson(server, "getaddressbyname", params);

        return response.has("error") ? null : response.getString("result");
    }
    
    public static String getVersion(InetSocketAddress server) throws NKNHttpApiException {
        final String apiMethod = "getversion";
        
        final JSONObject params = new JSONObject();
        final JSONObject response = rpcCallJson(server, apiMethod, params);
  
        if (response.has("error")) {
            throw new NKNHttpApiException(apiMethod, params, response);
        }
        
        return response.getString("result");
    }  
  
}
