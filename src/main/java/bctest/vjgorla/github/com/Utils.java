package bctest.vjgorla.github.com;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    
    private static final ThreadLocal<MessageDigest> T_MD = new ThreadLocal<>();
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    
    private static MessageDigest newMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        } 
    }
    
    private Utils() {}
    
    public static String digestStrToHex(String str) {
        MessageDigest md = T_MD.get();
        if (md == null) {
            md = newMessageDigest();
            T_MD.set(md);
        }
        md.reset();
        byte[] digested = md.digest(str.getBytes());
        char[] hexChars = new char[digested.length * 2];
        for (int i = 0; i < digested.length; i++ ) {
            int v = digested[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public static BigInteger hexToBigInt(String hex) {
        return new BigInteger(hex, 16);
    }
}
