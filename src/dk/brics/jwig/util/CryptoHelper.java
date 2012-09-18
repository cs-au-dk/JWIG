package dk.brics.jwig.util;

import dk.brics.jwig.JWIGException;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;

public class CryptoHelper {
    static {
        KeyGenerator kgen = null;
        try {
            kgen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            throw new JWIGException(e);
        }
        kgen.init(128);
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();

        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            encrypter = cipher;
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            decrypter = cipher;
        } catch (Exception e) {
            throw new JWIGException(e);
        }


    }

    private static Cipher encrypter;
    private static Cipher decrypter;

    public static String encode(String s) {
        try {
            byte[] rawBytes = encrypter.doFinal(s.getBytes());
            return String.valueOf(Base64.encode(rawBytes));
        } catch (Exception e) {
            throw new JWIGException(e);
        }
    }


    public static String decode(String s) {
        try {
            byte[] base64Decoded = Base64.decode(s);
            return new String(decrypter.doFinal(base64Decoded));
        } catch (Exception e) {
            throw new JWIGException(e);
        }
    }
}
