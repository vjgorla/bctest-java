package bctest.vjgorla.github.com;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.util.TextUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import bctest.vjgorla.github.com.Blockchain.AddBlockResult;
import bctest.vjgorla.github.com.Blockchain.Block;

@SuppressWarnings("restriction")
public class Main {
    
    private final Blockchain blockchain = new Blockchain();
    
    private String hostname;
    
    @Parameter(names = "-port", required=true)
    private int port;
    
    @Parameter(names = "-peer")
    private List<String> peers = new ArrayList<>();
    
    @Parameter(names = "-text")
    private String text;
    
    @Parameter(names = "-delay")
    private long delay = 3000;
    
    private class Miner extends Thread {
        private BigInteger nonce = BigInteger.ZERO;
        @Override
        public void run() {
            while (true) {
                nonce = nonce.add(BigInteger.ONE);
                Block block = new Block(blockchain.topBlockHash, nonce, System.currentTimeMillis(), text);
                String blockHash = Utils.digestStrToHex(block.blockContentsString());
                if (Utils.hexToBigInt(blockHash).compareTo(blockchain.currentDifficulty) <= 0) {
                    block.blockHash = blockHash;
                    blockchain.addBlock(block, true);
                    HttpUtils.relayBlock(peers, block, delay, hostname, port);
                }
            }
        }
    }
    
    private class BlockRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String peerStr = exchange.getRequestURI().getQuery().split("peer=")[1];
            String blockStr = HttpUtils.readStream(exchange.getRequestBody()).split("block=")[1];
            processBlock(HttpUtils.decode(blockStr), HttpUtils.decode(peerStr), new Function<Block, Void>() {
                @Override
                public Void apply(Block block) {
                    HttpUtils.relayBlock(peers, block, 0, hostname, port);
                    return null;
                }
            });
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
    
    private class AddPeerRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String peerStr = HttpUtils.readStream(exchange.getRequestBody()).split("peer=")[1];
            String peer = HttpUtils.decode(peerStr);
            if (!peers.contains(peer)) {
                peers.add(peer);
                System.out.println("peer " + peer + " connected");
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }

    private class GetBlocksRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String ancestor = Blockchain.ROOT_HASH;
            if (exchange.getRequestURI().getQuery() != null) {
                String[] tokens = exchange.getRequestURI().getQuery().split("ancestor=");
                ancestor = tokens[1];
            }
            List<Block> descendants = blockchain.getDescendants(ancestor);
            StringBuilder sb = new StringBuilder();
            for (Block block : descendants) {
                sb.append(block.blockString() + '\n');
            }
            byte[] responseBytes = sb.toString().getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes, 0, responseBytes.length);
            exchange.getResponseBody().close();
        }
    }

    private class GetAncestorRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String descendant = exchange.getRequestURI().getQuery().split("descendant=")[1];
            Block ancestor = blockchain.getAncestor(descendant);
            byte[] responseBytes = ancestor.blockString().getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes, 0, responseBytes.length);
            exchange.getResponseBody().close();
        }
    }

    private Main() {
    }
    
    private void processBlock(String blockStr, final String peer, final Function<Block, Void> onDone) {
        String blockElms[] = blockStr.split(":");
        if (blockElms.length != 5) {
            System.err.println("Invalid number of elements in block");
            return;
        }
        final Block block = new Block(blockElms[1], new BigInteger(blockElms[2]), Long.parseLong(blockElms[3]), blockElms[4]);
        block.blockHash = blockElms[0];
        AddBlockResult result = blockchain.addBlock(block, false);
        if (!result.alreadyExists) {
            if (result.isOrphan) {
                HttpUtils.getAncestor(peer, block.blockHash, new Function<String, Void>() {
                    @Override
                    public Void apply(String ancestorBlocksStr) {
                        processBlock(ancestorBlocksStr, peer, new Function<Block, Void>() {
                            @Override
                            public Void apply(Block unused) {
                                AddBlockResult nresult = blockchain.addBlock(block, false);
                                if (nresult.valid && onDone != null) {
                                    onDone.apply(block);
                                }
                                return null;
                            }
                        });
                        return null;
                    }
                });
            } else if (result.valid && onDone != null) {
                onDone.apply(block);
            }
        }
    }

    private void start() throws InterruptedException, IOException {
        if (!peers.isEmpty()) {
            HttpUtils.addPeer(peers, hostname, port);
            HttpUtils.getBlocks(peers.get(0), Blockchain.ROOT_HASH, new Function<String, Void>() {
                @Override
                public Void apply(String blockStr) {
                    processBlock(blockStr, null, null);
                    return null;
                }
            });
        }
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/block", new BlockRequestHandler());
        server.createContext("/addpeer", new AddPeerRequestHandler());
        server.createContext("/getblocks", new GetBlocksRequestHandler());
        server.createContext("/getancestor", new GetAncestorRequestHandler());
        server.setExecutor(null);
        server.start();
        
        Thread miner = new Main.Miner();
        miner.start();
        miner.join();
    }
    
    public static void main(String[] args) throws InterruptedException, IOException {
        Main main = new Main();
        main.hostname = InetAddress.getLocalHost().getHostAddress();
        new JCommander(main).parse(args);
        if (TextUtils.isBlank(main.text)) {
            main.text = main.hostname.concat("_").concat(Integer.toString(main.port)).replace(":", "_");
        }
        main.start();
    }
}
