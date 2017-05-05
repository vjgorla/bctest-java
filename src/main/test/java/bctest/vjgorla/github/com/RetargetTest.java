package bctest.vjgorla.github.com;

import java.math.BigInteger;

import bctest.vjgorla.github.com.Blockchain.Block;
import bctest.vjgorla.github.com.Blockchain.AddBlockResult;
import static bctest.vjgorla.github.com.Blockchain.INITIAL_DIFFICULTY;
import junit.framework.TestCase;

public class RetargetTest extends TestCase {

    private Blockchain blockchain;
    
    protected void setUp() {
        blockchain = new Blockchain();
        blockchain.retargetBlockInterval = 2;
    }
    
    public void testInitialDifficulty() {
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH, 0), true);
        assertEquals(blockchain._calculateDifficulty("a"), INITIAL_DIFFICULTY);
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
        blockchain.addBlock(createBlock("b", "a", 0), true);
        assertEquals(blockchain._calculateDifficulty("b"), INITIAL_DIFFICULTY);
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
    }
    
    public void testRetargetMainBranch() {
        blockchain.addBlock(createBlock("a",Blockchain.ROOT_HASH, 0), true);
        blockchain.addBlock(createBlock("b", "a", 10), true);
        blockchain.addBlock(createBlock("c", "b", 2000000), true);
        assertEquals(blockchain._calculateDifficulty("c"), INITIAL_DIFFICULTY.multiply(new BigInteger("2")));
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY.multiply(new BigInteger("2")));
        blockchain.addBlock(createBlock("d", "c", 2000010), true);
        blockchain.addBlock(createBlock("e", "d", 3000000), true);
        assertEquals(blockchain._calculateDifficulty("e"), INITIAL_DIFFICULTY);
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
        blockchain.addBlock(createBlock("f", "e", 3000010), true);
        blockchain.addBlock(createBlock("g", "f", 3000010), true);
        assertEquals(blockchain._calculateDifficulty("g"), INITIAL_DIFFICULTY.divide(new BigInteger("100000")));
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY.divide(new BigInteger("100000")));
    }
    
    public void testRetargetOtherBranches() {
        blockchain.addBlock(createBlock("a", Blockchain.ROOT_HASH, 0), true);
        blockchain.addBlock(createBlock("b", "a", 10), true);
        blockchain.addBlock(createBlock("c", "b", 1000000), true);
        blockchain.addBlock(createBlock("d", "c", 1000010), true);
        blockchain.addBlock(createBlock("e", "d", 2000000), true);
        assertEquals(blockchain.topBlockHash, "e");

        Block block = new Block(Blockchain.ROOT_HASH, new BigInteger("104566"), 0, "x");
        block.blockHash = "0000775a57e301f40a233af9e38a5b4b88252990e54ff6d5cdd90193bae574ab";
        AddBlockResult result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        block = new Block("0000775a57e301f40a233af9e38a5b4b88252990e54ff6d5cdd90193bae574ab", new BigInteger("4666"), 10, "x");
        block.blockHash = "0000946104f7553285a916a1b4fcfa5daa90d3bfdba15f06f5f48f85dc8f508c";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        block = new Block("0000946104f7553285a916a1b4fcfa5daa90d3bfdba15f06f5f48f85dc8f508c", new BigInteger("24846"), 2000000, "x");
        block.blockHash = "00002210f887de78b5697862f9b04a9ad880afc81a23a012b75b000a2c1305cd";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        assertEquals(blockchain._calculateDifficulty("00002210f887de78b5697862f9b04a9ad880afc81a23a012b75b000a2c1305cd"), INITIAL_DIFFICULTY.multiply(new BigInteger("2")));
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
        assertEquals(blockchain.topBlockHash, "e");

        block = new Block("00002210f887de78b5697862f9b04a9ad880afc81a23a012b75b000a2c1305cd", new BigInteger("57500"), 2000010, "x");
        block.blockHash = "00012eb9a9a568a13af7bad824ecf9719238aa45093d991941ed64a35e12fdd7";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        block = new Block("0000946104f7553285a916a1b4fcfa5daa90d3bfdba15f06f5f48f85dc8f508c", new BigInteger("57504"), 500000, "x");
        block.blockHash = "000093b3cea80d91e5046abe34a750c690891afa47ac83edba838e52190ec312";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        assertEquals(blockchain._calculateDifficulty("000093b3cea80d91e5046abe34a750c690891afa47ac83edba838e52190ec312"), INITIAL_DIFFICULTY.divide(new BigInteger("2")));
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
        assertEquals(blockchain.topBlockHash, "e");

        block = new Block("000093b3cea80d91e5046abe34a750c690891afa47ac83edba838e52190ec312", new BigInteger("147774"), 500010, "x");
        block.blockHash = "00009a0db70081b2091775b23c4f2829b5d9515c798d77533c99aef599565995";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, false);

        block = new Block("000093b3cea80d91e5046abe34a750c690891afa47ac83edba838e52190ec312", new BigInteger("211319"), 500010, "x");
        block.blockHash = "000048021b2f9e47e0c81783ce2940cc318f31d909bf537526e73fb54a9fb013";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        block = new Block("000048021b2f9e47e0c81783ce2940cc318f31d909bf537526e73fb54a9fb013", new BigInteger("526528"), 750000, "x");
        block.blockHash = "00002007422630a845f547083b9bad6ffd06bb4653cfc575f0f6bbf7038bf4bc";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        assertEquals(blockchain._calculateDifficulty("00002007422630a845f547083b9bad6ffd06bb4653cfc575f0f6bbf7038bf4bc"), INITIAL_DIFFICULTY.divide(new BigInteger("4")));
        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY);
        assertEquals(blockchain.topBlockHash, "e");

        block = new Block("00002007422630a845f547083b9bad6ffd06bb4653cfc575f0f6bbf7038bf4bc", new BigInteger("201327"), 750010, "x");
        block.blockHash = "00002d44ede421cb9287e478999fd875ea84247b430d140d680d203a99854897";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, false);
        assertEquals(blockchain.topBlockHash, "e");

        block = new Block("00002007422630a845f547083b9bad6ffd06bb4653cfc575f0f6bbf7038bf4bc", new BigInteger("243011"), 750010, "x");
        block.blockHash = "000005cdad940869376620dd4174419cb50ff6b342494ed59a7f45c5648f58b5";
        result = blockchain.addBlock(block, false);
        assertEquals(result.valid, true);

        assertEquals(blockchain.currentDifficulty, INITIAL_DIFFICULTY.divide(new BigInteger("4")));
        assertEquals(blockchain.topBlockHash, "000005cdad940869376620dd4174419cb50ff6b342494ed59a7f45c5648f58b5");
    }

    private Block createBlock(String _blockHash, String _prevBlockHash, long ts) {
        Block block = new Block(_prevBlockHash, BigInteger.ONE, ts, "x");
        block.blockHash = _blockHash;
        return block;
    }
}
