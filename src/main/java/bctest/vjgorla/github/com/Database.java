package bctest.vjgorla.github.com;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import bctest.vjgorla.github.com.Blockchain.Block;

public class Database {
    
    private final Connection conn;
    
    public static class Meta {
        BigInteger currentDifficulty;
        BigInteger topBlockNumber;
        String topBlockHash;
    }

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public Database(String url) {
        try {
            conn = DriverManager.getConnection(url, "sa", "");
            initDb();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void initDb() {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS BLOCKCHAIN(CURRENT_DIFFICULTY VARCHAR(65536) NOT NULL, "
                                                             + "TOP_BLOCK_NUMBER VARCHAR(65536) NOT NULL, "
                                                             + "TOP_BLOCK_HASH VARCHAR(64) NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS BLOCK(BLOCK_HASH VARCHAR(64) NOT NULL PRIMARY KEY, "
                                                        + "PREV_BLOCK_HASH VARCHAR(64) NOT NULL, "
                                                        + "NEXT_BLOCK_DIFFICULTY VARCHAR(65536) NOT NULL, "
                                                        + "BLOCK_NUMBER VARCHAR(65536) NOT NULL, "
                                                        + "BLOCK_STR VARCHAR(65536) NOT NULL)");
            stmt.execute("CREATE INDEX IF NOT EXISTS PREV_BLOCK_HASH_IDX ON BLOCK(PREV_BLOCK_HASH)");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(null, stmt);
        }
    }
    
    public void insertBlock(Block block) {
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("INSERT INTO BLOCK(BLOCK_HASH, "
                                                         + "PREV_BLOCK_HASH, "
                                                         + "NEXT_BLOCK_DIFFICULTY, "
                                                         + "BLOCK_NUMBER, "
                                                         + "BLOCK_STR) "
                                                         + "VALUES (?, ?, ?, ?, ?)");
            stmt.setString(1, block.blockHash);
            stmt.setString(2, block.prevBlockHash);
            stmt.setString(3, block.nextBlockDifficulty.toString());
            stmt.setString(4, block.blockNumber.toString());
            stmt.setString(5, block.blockString());
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(null, stmt);
        }
    }
    
    public void initBlockchain(BigInteger currentDifficulty, BigInteger topBlockNumber, String topBlockHash) {
        String sql = "INSERT INTO BLOCKCHAIN (CURRENT_DIFFICULTY, TOP_BLOCK_NUMBER, TOP_BLOCK_HASH) VALUES (?, ?, ?)";
        this.runBlockchainSql(sql, currentDifficulty, topBlockNumber, topBlockHash);
    }
    
    public void updateBlockchain(BigInteger currentDifficulty, BigInteger topBlockNumber, String topBlockHash) {
        String sql = "UPDATE BLOCKCHAIN SET CURRENT_DIFFICULTY = ?, TOP_BLOCK_NUMBER = ?, TOP_BLOCK_HASH = ?";
        this.runBlockchainSql(sql, currentDifficulty, topBlockNumber, topBlockHash);
    }
    
    private void runBlockchainSql(String sql, BigInteger currentDifficulty, BigInteger topBlockNumber, String topBlockHash) {
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentDifficulty.toString());
            stmt.setString(2, topBlockNumber.toString());
            stmt.setString(3, topBlockHash);
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(null, stmt);
        }
    }

    public Meta readBlockchain() {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT CURRENT_DIFFICULTY, TOP_BLOCK_NUMBER, TOP_BLOCK_HASH FROM BLOCKCHAIN");
            rs = stmt.executeQuery();
            boolean hasRow = rs.next();
            if (!hasRow) {
                return null;
            }
            Meta meta = new Meta();
            meta.currentDifficulty = new BigInteger(rs.getString(1));
            meta.topBlockNumber = new BigInteger(rs.getString(2));
            meta.topBlockHash = rs.getString(3);
            return meta;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(rs, stmt);
        }
    }
    
    public Block getBlock(String hash) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("SELECT BLOCK_STR, BLOCK_NUMBER, NEXT_BLOCK_DIFFICULTY FROM BLOCK WHERE BLOCK_HASH = ?");
            stmt.setString(1, hash);
            rs = stmt.executeQuery();
            boolean hasRow = rs.next();
            if (!hasRow) {
                return null;
            }
            return readBlock(rs);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(rs, stmt);
        }
    }
    
    public Block getAncestorAtHeight(String hash, int height) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("WITH LINK(PREV_BLOCK_HASH, BLOCK_NUMBER, BLOCK_STR, NEXT_BLOCK_DIFFICULTY) AS ( " +
                                             "SELECT PREV_BLOCK_HASH, BLOCK_NUMBER, BLOCK_STR, NEXT_BLOCK_DIFFICULTY FROM BLOCK WHERE BLOCK_HASH = '" + hash + "' " +
                                             "UNION ALL " +
                                             "SELECT BLOCK.PREV_BLOCK_HASH, BLOCK.BLOCK_NUMBER, BLOCK.BLOCK_STR, BLOCK.NEXT_BLOCK_DIFFICULTY " +
                                             "FROM LINK INNER JOIN BLOCK ON BLOCK.BLOCK_HASH = LINK.PREV_BLOCK_HASH " +
                                             "WHERE BLOCK.BLOCK_NUMBER >= '" + Integer.toString(height) + "' " + 
                                         ") " +
                                         "SELECT BLOCK_STR, BLOCK_NUMBER, NEXT_BLOCK_DIFFICULTY FROM LINK WHERE BLOCK_NUMBER = '" + Integer.toString(height) + "'");
            rs = stmt.executeQuery();
            rs.next();
            return readBlock(rs);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(rs, stmt);
        }
    }
    
    public List<Block> getDescendants(String fromHash, String toHash) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<Block> blocks = new ArrayList<>();
        try {
            stmt = conn.prepareStatement("WITH LINK(PREV_BLOCK_HASH, BLOCK_NUMBER, BLOCK_STR, NEXT_BLOCK_DIFFICULTY) AS ( " +
                                             "SELECT PREV_BLOCK_HASH, BLOCK_NUMBER, BLOCK_STR, NEXT_BLOCK_DIFFICULTY FROM BLOCK WHERE BLOCK_HASH = '" + toHash + "' " +
                                             "UNION ALL " +
                                             "SELECT BLOCK.PREV_BLOCK_HASH, BLOCK.BLOCK_NUMBER, BLOCK.BLOCK_STR, BLOCK.NEXT_BLOCK_DIFFICULTY " +
                                             "FROM LINK INNER JOIN BLOCK ON BLOCK.BLOCK_HASH = LINK.PREV_BLOCK_HASH " +
                                             "WHERE CAST(BLOCK.BLOCK_NUMBER AS NUMBER) >= CAST((SELECT DISTINCT BLOCK_NUMBER FROM BLOCK WHERE PREV_BLOCK_HASH = '" + fromHash + "') AS NUMBER) " + 
                                         ") " +
                                         "SELECT BLOCK_STR, BLOCK_NUMBER, NEXT_BLOCK_DIFFICULTY FROM LINK ORDER BY CAST(BLOCK_NUMBER AS NUMBER) ASC");
            rs = stmt.executeQuery();
            if (rs.next()) {
                Block firstBlock = readBlock(rs);
                if (firstBlock.prevBlockHash.equals(fromHash)) {
                    blocks.add(firstBlock);
                    while(rs.next()) {
                        blocks.add(readBlock(rs));
                    }
                }
            }
            return blocks;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(rs, stmt);
        }
    }

    public int getDistance(String fromHash, String toHash) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement("WITH LINK(BLOCK_HASH, DISTANCE) AS ( " +
                                             "SELECT BLOCK_HASH, 0 FROM BLOCK WHERE BLOCK_HASH = '" + fromHash + "' " +
                                             "UNION ALL " + 
                                             "SELECT BLOCK.BLOCK_HASH, LINK.DISTANCE + 1 " + 
                                             "FROM LINK INNER JOIN BLOCK ON BLOCK.PREV_BLOCK_HASH = LINK.BLOCK_HASH " +
                                         ") " + 
                                         "SELECT DISTANCE FROM LINK WHERE LINK.BLOCK_HASH = '" + toHash + "'");
            rs = stmt.executeQuery();
            boolean hasRow = rs.next();
            if (!hasRow) {
                return -1;
            }
            return rs.getInt(1);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            close(rs, stmt);
        }
    }

    private Block readBlock(ResultSet rs) throws SQLException {
        String blockElms[] = rs.getString(1).split(":");
        if (blockElms.length != 5) {
            throw new RuntimeException("Invalid number of elements in block");
        }
        Block block = new Block(blockElms[1], new BigInteger(blockElms[2]), Long.parseLong(blockElms[3]), blockElms[4]);
        block.blockHash = blockElms[0];
        block.blockNumber = new BigInteger(rs.getString(2));
        block.nextBlockDifficulty = new BigInteger(rs.getString(3));
        return block;
    }

    public void shutdown() {
        try { conn.close(); } catch (Exception ex) {}
    }
    
    private void close(ResultSet rs, Statement stmt) {
        if (rs != null) {
            try { rs.close(); } catch (Exception ex) {}
        }
        if (stmt != null) {
            try { stmt.close(); } catch (Exception ex) {}
        }
    }
}
