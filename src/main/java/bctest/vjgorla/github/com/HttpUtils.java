package bctest.vjgorla.github.com;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import bctest.vjgorla.github.com.Blockchain.Block;

public class HttpUtils {
    
    public static interface GetBlocksResponseHandler {
        void handle(String blockStr);
    }
    
    private HttpUtils() {}
    
    public static void relayBlock(final List<String> peers, final Block block, final long delay, final String hostname, final int port) {
        HttpAction action = new HttpAction() {
            @Override
            public void executeImpl(CloseableHttpClient client) throws Exception {
                Thread.sleep(delay);
                for (String peer: peers) {
                    URI uri = new URIBuilder(peer + "/block").addParameter("peer", toUrl(hostname, port)).build(); 
                    HttpPost request = new HttpPost(uri);
                    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
                    nvps.add(new BasicNameValuePair("block", block.blockString()));
                    request.setEntity(new UrlEncodedFormEntity(nvps)); 
                    CloseableHttpResponse response = client.execute(request);
                    response.close();
                }
            }
        };
        action.execute(true);
    }

    public static void getBlocks(final String peer, final String ancestorHash, final Function<String, Void> handler) {
        HttpAction action = new HttpAction() {
            @Override
            public void executeImpl(CloseableHttpClient client) throws Exception {
                URI uri = new URIBuilder(peer + "/getblocks").addParameter("ancestor", ancestorHash).build(); 
                HttpGet request = new HttpGet(uri);
                CloseableHttpResponse response = client.execute(request);
                HttpEntity entity = response.getEntity();
                String responseStr = EntityUtils.toString(entity);
                if (responseStr != null) {
                    for (String blockStr : responseStr.split("\\r?\\n")) {
                        handler.apply(blockStr);
                    }
                }
                response.close();
            }
        };
        action.execute(true);
    }
    
    public static void getAncestor(final String peer, final String descendantHash, final Function<String, Void> handler) {
        HttpAction action = new HttpAction() {
            @Override
            public void executeImpl(CloseableHttpClient client) throws Exception {
                URI uri = new URIBuilder(peer + "/getancestor").addParameter("descendant", descendantHash).build(); 
                HttpGet request = new HttpGet(uri);
                CloseableHttpResponse response = client.execute(request);
                HttpEntity entity = response.getEntity();
                String ancestorBlocksStr = EntityUtils.toString(entity);
                if (ancestorBlocksStr != null) {
                    handler.apply(ancestorBlocksStr);
                }
                response.close();
            }
        };
        action.execute(true);
    }

    public static void addPeer(final List<String> peers, final String hostname, final int port) {
        HttpAction action = new HttpAction() {
            @Override
            public void executeImpl(CloseableHttpClient client) throws Exception {
                for (String peer: peers) {
                    URI uri = new URIBuilder(peer + "/addpeer").build(); 
                    HttpPost request = new HttpPost(uri);
                    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
                    nvps.add(new BasicNameValuePair("peer", toUrl(hostname, port)));
                    request.setEntity(new UrlEncodedFormEntity(nvps)); 
                    CloseableHttpResponse response = client.execute(request);
                    response.close();
                }
            }
        };
        action.execute(true);
    }

    private static abstract class HttpAction {
        abstract void executeImpl(CloseableHttpClient client) throws Exception;
        void execute(boolean async) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    CloseableHttpClient client = HttpClientBuilder.create().disableAutomaticRetries().build();
                    try {
                        executeImpl(client);
                    } catch (Exception ex) {
                        //System.err.println(ex.getLocalizedMessage());
                    } finally {
                        try {
                            client.close();
                        } catch (IOException ex) {
                            //System.err.println(ex.getLocalizedMessage());
                        }
                    }
                }
            };
            if (async) {
                Thread thread = new Thread(runnable);
                thread.start();
            } else {
                runnable.run();
            }
        }
    }
    
    public static String readStream(InputStream inputStream) {
        final int bufferSize = 1024;
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        try {
            Reader in = new InputStreamReader(inputStream, "UTF-8");
            while (true) {
                int rsz = in.read(buffer, 0, buffer.length);
                if (rsz < 0) {
                    break;
                }
                out.append(buffer, 0, rsz);
            }
            return out.toString();  
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static String decode(String str) {
        return URLDecoder.decode(str);
    }
    
    private static String toUrl(String hostname, int port) {
        return "http://" + hostname + ":" + port;
    }
}
