package bctest.vjgorla.github.com;

import java.math.BigInteger;
import java.util.List;

import org.junit.Test;

import bctest.vjgorla.github.com.Blockchain.Block;
import junit.framework.TestCase;

public class BlockchainTest extends TestCase {

    private Blockchain blockchain;
    
    protected void setUp() {
        blockchain = new Blockchain();
    }
    
    @Test
    public void testIntialState() {
        assertEquals(blockchain.topBlockHash, Blockchain.ROOT_HASH);
        assertEquals(blockchain.topBlockNumber.intValue(), 0);
    }

    public void testFirstBlock() {
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH), true);
        assertEquals(blockchain.topBlockHash, "a");
        assertEquals(blockchain.topBlockNumber.intValue(), 1);
    }
    
    public void testOrphan() {
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH), true);
        blockchain.addBlock(createBlock("b", "x"), true);
        assertEquals(blockchain.topBlockHash, "a");
        assertEquals(blockchain.topBlockNumber.intValue(), 1);
    }
    
    public void testNoReorg() {
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH), true);
        blockchain.addBlock(createBlock("b", "a"), true);
        blockchain.addBlock(createBlock("c", "b"), true);
        blockchain.addBlock(createBlock("p",Blockchain.ROOT_HASH), true);
        assertEquals(blockchain.topBlockHash, "c");
        assertEquals(blockchain.topBlockNumber.intValue(), 3);
    }
    
    public void testReorgSuperficial() {
        blockchain.addBlock(createBlock("p",Blockchain.ROOT_HASH), true);
        assertEquals(blockchain.topBlockHash, "p");
        assertEquals(blockchain.topBlockNumber.intValue(), 1);
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH), true);
        assertEquals(blockchain.topBlockHash, "p");
        assertEquals(blockchain.topBlockNumber.intValue(), 1);
        blockchain.addBlock(createBlock("b", "a"), true);
        assertEquals(blockchain.topBlockHash, "b");
        assertEquals(blockchain.topBlockNumber.intValue(), 2);
        blockchain.addBlock(createBlock("q","p"), true);
        assertEquals(blockchain.topBlockHash, "b");
        assertEquals(blockchain.topBlockNumber.intValue(), 2);
        blockchain.addBlock(createBlock("r","q"), true);
        assertEquals(blockchain.topBlockHash, "r");
        assertEquals(blockchain.topBlockNumber.intValue(), 3);
    }
    
    public void testReorgDeep() {
        blockchain.addBlock(createBlock("x",Blockchain.ROOT_HASH), true);
        blockchain.addBlock(createBlock("p","x"), true);
        blockchain.addBlock(createBlock("q","p"), true);
        blockchain.addBlock(createBlock("a","x"), true);
        blockchain.addBlock(createBlock("b", "a"), true);
        blockchain.addBlock(createBlock("c", "b"), true);
        assertEquals(blockchain.topBlockHash, "c");
        assertEquals(blockchain.topBlockNumber.intValue(), 4);
        blockchain.addBlock(createBlock("r","q"), true);
        assertEquals(blockchain.topBlockHash, "c");
        assertEquals(blockchain.topBlockNumber.intValue(), 4);
        blockchain.addBlock(createBlock("s","r"), true);
        assertEquals(blockchain.topBlockHash, "s");
        assertEquals(blockchain.topBlockNumber.intValue(), 5);
    }
    
    public void testReorgVeryDeep() {
        blockchain.addBlock(createBlock("p",Blockchain.ROOT_HASH), true);
        blockchain.addBlock(createBlock("q","p"), true);
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH), true);
        blockchain.addBlock(createBlock("b", "a"), true);
        blockchain.addBlock(createBlock("c", "b"), true);
        assertEquals(blockchain.topBlockHash, "c");
        assertEquals(blockchain.topBlockNumber.intValue(), 3);
        blockchain.addBlock(createBlock("r","q"), true);
        assertEquals(blockchain.topBlockHash, "c");
        assertEquals(blockchain.topBlockNumber.intValue(), 3);
        blockchain.addBlock(createBlock("s","r"), true);
        assertEquals(blockchain.topBlockHash, "s");
        assertEquals(blockchain.topBlockNumber.intValue(), 4);
    }
    
    public void testReorgMultipleBranches() {
        blockchain.addBlock(createBlock("p",Blockchain.ROOT_HASH), true);
        assertEquals(getDescendantsAsStr(Blockchain.ROOT_HASH), "p");
        assertEquals(getDescendantsAsStr("p"), "");
        blockchain.addBlock(createBlock("q","p"), true);
        assertEquals(getDescendantsAsStr(Blockchain.ROOT_HASH), "p:q");
        assertEquals(getDescendantsAsStr("p"), "q");
        blockchain.addBlock(createBlock("r","q"), true);
        assertTopBlock("r", 3);
        blockchain.addBlock(createBlock("a","p"), true);
        assertTopBlock("r", 3);
        blockchain.addBlock(createBlock("b", "a"), true);
        assertTopBlock("r", 3);
        blockchain.addBlock(createBlock("c", "b"), true);
        assertEquals(getDescendantsAsStr(Blockchain.ROOT_HASH), "p:a:b:c");
        assertEquals(getDescendantsAsStr("p"), "a:b:c");
        assertEquals(getDescendantsAsStr("q"), "");
        assertTopBlock("c", 4);
        blockchain.addBlock(createBlock("x", "b"), true);
        assertTopBlock("c", 4);
        blockchain.addBlock(createBlock("y", "x"), true);
        assertTopBlock("y", 5);
        blockchain.addBlock(createBlock("d", "c"), true);
        assertTopBlock("y", 5);
        blockchain.addBlock(createBlock("e", "d"), true);
        assertTopBlock("e", 6);
        blockchain.addBlock(createBlock("s", "r"), true);
        assertTopBlock("e", 6);
        blockchain.addBlock(createBlock("t", "s"), true);
        assertTopBlock("e", 6);
        blockchain.addBlock(createBlock("u", "t"), true);
        assertTopBlock("e", 6);
        blockchain.addBlock(createBlock("v", "u"), true);
        assertEquals(getDescendantsAsStr(Blockchain.ROOT_HASH), "p:q:r:s:t:u:v");
        assertTopBlock("v", 7);
    }

    private String getDescendantsAsStr(String hash) {
        List<Block> descendants = blockchain.getDescendants(hash);
        StringBuilder acc = new StringBuilder();
        for (Block block : descendants) {
            if (acc.length() > 0) {
                acc.append(":");
            }
            acc.append(block.blockHash);
        }
        return acc.toString();
    }
    
    private void assertTopBlock(String _blockHash, int depth) {
        assertEquals(blockchain.topBlockHash, _blockHash);
        assertEquals(blockchain.topBlockNumber.intValue(), depth);
    }

    private Block createBlock(String _blockHash, String _prevBlockHash) {
        Block block = new Block(_prevBlockHash, BigInteger.ONE, 1, "x");
        block.blockHash = _blockHash;
        return block;
    }
}
