package bctest.vjgorla.github.com;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Blockchain {

    public static final String ROOT_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    public static final int RETARGET_BLOCK_INTERVAL = 100;
    public static final BigInteger INITIAL_DIFFICULTY = new BigInteger("2").pow(256).divide(new BigInteger("100000"));
    public static final BigInteger BASELINE_TS_INTERVAL = new BigInteger("1000000");
    public static final BigInteger DIFFICULTY_PRECISION = new BigInteger("1000000");

    BigInteger currentDifficulty = INITIAL_DIFFICULTY;
    int retargetBlockInterval = RETARGET_BLOCK_INTERVAL;
    BigInteger topBlockNumber = BigInteger.ZERO;
    String topBlockHash = ROOT_HASH;
    Map<String, Block> map = new HashMap<>();
    Map<String, List<Block>> descendantsMap = new HashMap<>();
    
    public static class Block {
        public Block(String prevBlockHash, BigInteger nonce, long ts, String text) {
            this.prevBlockHash = prevBlockHash;
            this.nonce = nonce;
            this.ts = ts;
            this.text = text;
        }
        String blockHash;
        String prevBlockHash;
        BigInteger nonce;
        long ts;
        String text;
        public String blockContentsString() {
            return this.prevBlockHash + ":" + this.nonce.toString() + ":" + this.ts + ":" + this.text;
        }
        public String blockString() {
            return this.blockHash + ":" + this.blockContentsString();
        }
    }
    
    public static class AddBlockResult {
        boolean alreadyExists = false;
        boolean isOrphan = false;
        boolean valid = true;
        private AddBlockResult() {}
        private static AddBlockResult create() {
            return new AddBlockResult();
        }
        private AddBlockResult alreadyExists() {
            this.alreadyExists = true;
            return this;
        }
        private AddBlockResult orphan() {
            this.isOrphan = true;
            return this;
        }
        private AddBlockResult invalid() {
            this.valid = false;
            return this;
        }
        private AddBlockResult valid() {
            return this;
        }
    }

    public synchronized AddBlockResult addBlock(Block block, boolean mined) {
        if (this.map.containsKey(block.blockHash)) {
            return AddBlockResult.create().alreadyExists();
        }
        if (!block.prevBlockHash.equals(ROOT_HASH) && !this.map.containsKey(block.prevBlockHash)) {
            System.out.println("Orphan - " + block.prevBlockHash + " does not exist");
            return AddBlockResult.create().orphan();
        }
        if (!mined) {
            String blockContentsStr = block.blockContentsString();
            String blockHash = Utils.digestStrToHex(blockContentsStr);
            if (!block.blockHash.equals(blockHash)) {
                System.out.println("Invalid block hash " + block.blockString());
                return AddBlockResult.create().invalid();
            }
            BigInteger hashBigInt = Utils.hexToBigInt(block.blockHash);
            BigInteger targetDifficulty = this._calculateDifficulty(block.prevBlockHash);
            if (hashBigInt.compareTo(targetDifficulty) > 0) {
                System.out.println("Invalid difficulty");
                return AddBlockResult.create().invalid();
            }
        }
        this.map.put(block.blockHash, block);
        List<Block> descendants = this.descendantsMap.get(block.prevBlockHash);
        if (descendants == null) {
            descendants = new ArrayList<Block>();
            this.descendantsMap.put(block.prevBlockHash, descendants);
        }
        descendants.add(block);
        if (block.prevBlockHash.equals(this.topBlockHash)) {
            this.topBlockNumber = this.topBlockNumber.add(BigInteger.ONE);
            System.out.println(this.topBlockNumber + (mined ? " > " : " < ") + block.blockString());
            this.topBlockHash = block.blockHash;
            this._setDifficulty(block);
            return AddBlockResult.create().valid();
        } else {
            String ihash = block.prevBlockHash;
            int blockDepth = 0;
            while(true) {
                blockDepth++;
                int distance = this._findDistance(ihash, this.topBlockHash, 0);
                if (distance != -1) {
                    if (blockDepth > distance) {
                        this.topBlockNumber = this.topBlockNumber.subtract(new BigInteger(Integer.toString(distance))).add(new BigInteger(Integer.toString(blockDepth)));
                        System.out.println("...(" + blockDepth + ") " + this.topBlockNumber + (mined ? " > " : " < ") + block.blockString());
                        this.topBlockHash = block.blockHash;
                        this._setDifficulty(block);
                    } else {
                        System.out.println((mined ? " > " : " < ") + block.blockString());
                    }
                    return AddBlockResult.create().valid();
                }
                if (ihash.equals(ROOT_HASH)) {
                    throw new IllegalStateException("Traversing past root!!!");
                }
                ihash = this.map.get(ihash).prevBlockHash;
            }
        }
    }
    
    private void _setDifficulty(Block block) {
        BigInteger newDifficulty = this._calculateDifficulty(block.blockHash);
        if (newDifficulty.compareTo(this.currentDifficulty) != 0) {
            BigInteger oldD = _difficultyToDisplay(currentDifficulty);
            BigInteger newD = _difficultyToDisplay(newDifficulty);
            BigDecimal diff = new BigDecimal(newD.subtract(oldD).multiply(new BigInteger("100")))
                .divide(new BigDecimal(oldD), 2, RoundingMode.HALF_UP);
            System.out.println("Difficulty " + diff + "% ... " + oldD.toString() + " > " + newD.toString());
        }
        this.currentDifficulty = newDifficulty;
    }
    
    BigInteger _calculateDifficulty(String hash) {
        int height = this._calculateHeight(hash);
        if (height <= this.retargetBlockInterval) {
            return INITIAL_DIFFICULTY;
        }
        int fromHeight = height - ((height - 1) % this.retargetBlockInterval);
        int toHeight = fromHeight - this.retargetBlockInterval;
        long fromTs = 0;
        while (true) {
            Block block = this.map.get(hash);
            if (height == fromHeight) {
                fromTs = block.ts;
            }
            if (height == toHeight) {
                long interval = fromTs - block.ts;
                return _difficulty(interval, this._calculateDifficulty(block.blockHash));
            }
            hash = block.prevBlockHash;
            height--;
        }
    }
    
    private int _calculateHeight(String hash) {
        int height = 0;
        while (!hash.equals(ROOT_HASH)) {
            Block block = this.map.get(hash);
            height++;
            hash = block.prevBlockHash;
        }
        return height;
    }
    
    private int _findDistance(String fromHash, String toHash, int currentDepth) {
        if (fromHash.equals(toHash)) {
            return currentDepth;
        }
        List<Block> descendants = this.descendantsMap.get(fromHash);
        if (descendants == null) {
            return -1;
        }
        currentDepth++;
        for (int i = 0; i < descendants.size(); i++) {
            if (descendants.get(i).blockHash.equals(toHash)) {
                return currentDepth;
            } else {
                int distance = this._findDistance(descendants.get(i).blockHash, toHash, currentDepth);
                if (distance != -1) {
                    return distance;
                }
            }
        }
        return -1;
    }
    
    public List<Block> getDescendants(String blockHash) {
        List<Block> result = new ArrayList<>();
        this._getDescendants(blockHash, result);
        return result;
    }
    
    private void _getDescendants(String blockHash, List<Block> result) {
        List<Block> descendants = this.descendantsMap.get(blockHash);
        if (descendants != null) {
            for (int i = 0; i < descendants.size(); i++) {
                int distance = this._findDistance(descendants.get(i).blockHash, this.topBlockHash, 0);
                if (distance != -1) {
                    result.add(descendants.get(i));
                    this._getDescendants(descendants.get(i).blockHash, result);
                }
            }
        }
    }

    public Block getAncestor(String descendantHash) {
        Block block = this.map.get(descendantHash);
        if (block == null) {
            return null;
        }
        return this.map.get(block.prevBlockHash);
    }
    
    private static BigInteger _difficulty(long interval, BigInteger prevDifficulty) {
        return new BigInteger(Long.toString(interval)).multiply(DIFFICULTY_PRECISION).divide(BASELINE_TS_INTERVAL).multiply(prevDifficulty).divide(DIFFICULTY_PRECISION);
    }
    
    private static BigInteger _difficultyToDisplay (BigInteger diffculty) {
        return new BigInteger("2").pow(256).divide(diffculty);
    }
}
