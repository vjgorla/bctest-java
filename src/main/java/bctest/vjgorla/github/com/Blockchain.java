package bctest.vjgorla.github.com;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

import bctest.vjgorla.github.com.Database.Meta;

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
    
    private Database db;
    
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
        BigInteger blockNumber;
        BigInteger nextBlockDifficulty;
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

    public Blockchain(String dbUrl) {
        db = new Database(dbUrl);
        Meta meta = db.readBlockchain();
        if (meta == null) {
            db.initBlockchain(this.currentDifficulty, this.topBlockNumber, this.topBlockHash);
        } else {
            this.currentDifficulty = meta.currentDifficulty;
            this.topBlockNumber = meta.topBlockNumber;
            this.topBlockHash = meta.topBlockHash;
        }
    }
    
    public void close() {
        this.db.shutdown();
    }
    
    public synchronized AddBlockResult addBlock(Block block, boolean mined) {
        if (this.db.getBlock(block.blockHash) != null) {
            return AddBlockResult.create().alreadyExists();
        }
        Block prevBlock = null;
        if (block.prevBlockHash.equals(ROOT_HASH)) {
            block.blockNumber = BigInteger.ONE;
        } else {
            prevBlock = this.db.getBlock(block.prevBlockHash);
            if (prevBlock == null) {
                System.out.println("Orphan - " + block.prevBlockHash + " does not exist");
                return AddBlockResult.create().orphan();
            }
            block.blockNumber = prevBlock.blockNumber.add(BigInteger.ONE);
        }
        if (!mined) {
            String blockContentsStr = block.blockContentsString();
            String blockHash = Utils.digestStrToHex(blockContentsStr);
            if (!block.blockHash.equals(blockHash)) {
                System.out.println("Invalid block hash " + block.blockString());
                return AddBlockResult.create().invalid();
            }
            BigInteger hashBigInt = Utils.hexToBigInt(block.blockHash);
            BigInteger targetDifficulty = prevBlock == null ? INITIAL_DIFFICULTY : prevBlock.nextBlockDifficulty;
            if (hashBigInt.compareTo(targetDifficulty) > 0) {
                System.out.println("Invalid difficulty");
                return AddBlockResult.create().invalid();
            }
        }
        block.nextBlockDifficulty = this._calculateDifficulty(block, prevBlock);
        db.insertBlock(block);
        if (block.prevBlockHash.equals(this.topBlockHash)) {
            this.topBlockNumber = block.blockNumber;
            System.out.println(this.topBlockNumber + (mined ? " > " : " < ") + block.blockString());
            this.topBlockHash = block.blockHash;
            if (block.nextBlockDifficulty.compareTo(this.currentDifficulty) != 0) {
                this.currentDifficulty = block.nextBlockDifficulty;
            }
            db.updateBlockchain(this.currentDifficulty, this.topBlockNumber, this.topBlockHash);
            return AddBlockResult.create().valid();
        } else {
            if (block.blockNumber.compareTo(this.topBlockNumber) == 1) {
                System.out.println("...(" + topBlockDistanceFromCommonAncestor(block.prevBlockHash) + ") " + block.blockNumber + (mined ? " > " : " < ") + block.blockString());
                this.topBlockNumber = block.blockNumber;
                this.topBlockHash = block.blockHash;
                if (block.nextBlockDifficulty.compareTo(this.currentDifficulty) != 0) {
                    this.currentDifficulty = block.nextBlockDifficulty;
                }
                db.updateBlockchain(this.currentDifficulty, this.topBlockNumber, this.topBlockHash);
            } else {
                System.out.println((mined ? " > " : " < ") + block.blockString());
            }
            return AddBlockResult.create().valid();
        }
    }
    
    private int topBlockDistanceFromCommonAncestor(String ihash) {
        while(true) {
            if (ihash.equals(ROOT_HASH)) {
                return this.topBlockNumber.intValue();
            }
            int distance = this.db.getDistance(ihash, this.topBlockHash);
            if (distance >= 0) {
                return distance;
            }
            ihash = this.db.getBlock(ihash).prevBlockHash;
        }
    }
    
    BigInteger _calculateDifficulty(String hash) {
        Block block = this.db.getBlock(hash);
        return _calculateDifficulty(block, this.db.getBlock(block.prevBlockHash));
    }
    
    BigInteger _calculateDifficulty(Block block, Block prevBlock) {
        if (prevBlock == null) {
            return INITIAL_DIFFICULTY;
        }
        int height = block.blockNumber.intValue();
        if ((height - 1) % this.retargetBlockInterval > 0) {
            return prevBlock.nextBlockDifficulty;
        }
        int toHeight = height - this.retargetBlockInterval;
        Block toBlock = this.db.getAncestorAtHeight(block.prevBlockHash, toHeight);
        long interval = block.ts - toBlock.ts;
        BigInteger newDifficulty = _difficulty(interval, toBlock.nextBlockDifficulty);
        BigInteger oldD = _difficultyToDisplay(toBlock.nextBlockDifficulty);
        BigInteger newD = _difficultyToDisplay(newDifficulty);
        BigDecimal diff = new BigDecimal(newD.subtract(oldD).multiply(new BigInteger("100")))
                              .divide(new BigDecimal(oldD), 2, RoundingMode.HALF_UP);
        System.out.println("Difficulty " + diff + "% ... " + oldD.toString() + " > " + newD.toString());
        return newDifficulty;
    }
    
    public List<Block> getDescendants(String blockHash) {
        return this.db.getDescendants(blockHash, topBlockHash);
    }

    public Block getAncestor(String descendantHash) {
        Block block = this.db.getBlock(descendantHash);
        if (block == null) {
            return null;
        }
        return this.db.getBlock(block.prevBlockHash);
    }
    
    private static BigInteger _difficulty(long interval, BigInteger prevDifficulty) {
        return new BigInteger(Long.toString(interval)).multiply(DIFFICULTY_PRECISION).divide(BASELINE_TS_INTERVAL).multiply(prevDifficulty).divide(DIFFICULTY_PRECISION);
    }
    
    private static BigInteger _difficultyToDisplay (BigInteger diffculty) {
        return new BigInteger("2").pow(256).divide(diffculty);
    }
}
