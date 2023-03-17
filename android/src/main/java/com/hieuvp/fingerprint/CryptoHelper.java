package com.hieuvp.fingerprint;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Array;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class CryptoHelper {
    private Context applicationContext;
    private final String provider = "AndroidKeyStore";
    private final int IV_SIZE = 16;
    private final String KEY_NAME = "smartshop_KEY";
    private final String SHARED_PREFERENCES_NAME = "smartshop_SHARED_PREFERENCES";
    private final String KEYSTORE_IV_NAME = "smartshop_KEY_IV";

    public CryptoHelper(Context context) {
        applicationContext = context;
    }

    public boolean checkCipherResult(Cipher result) {
        SharedPreferences preferences = applicationContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!preferences.contains(KEYSTORE_IV_NAME)) {
            return true;
        }
        String cachedIV = preferences.getString(KEYSTORE_IV_NAME, "");
        String resultIV = byteArrayToHex(result.getIV());
        return Objects.equals(cachedIV, resultIV);
    }

    public Cipher getCipher() throws Exception {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            SecretKey secretKey = getSecretKey();
            if (secretKey == null) {
                generateSecretKey(new KeyGenParameterSpec.Builder(KEY_NAME, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        // Invalidate the keys if the user has registered a new biometric
                        // credential, such as a new fingerprint. Can call this method only
                        // on Android 7.0 (API level 24) or higher. The variable
                        // "invalidatedByBiometricEnrollment" is true by default.
                        .setInvalidatedByBiometricEnrollment(false)
                        // .setUserAuthenticationValidityDurationSeconds(-1)
                        .build());
                secretKey = getSecretKey();
            }
            SharedPreferences preferences = applicationContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            byte[] iv;
            if (preferences.contains(KEYSTORE_IV_NAME)) {
                iv = hexToByteArray(preferences.getString(KEYSTORE_IV_NAME, ""));
                IvParameterSpec spec = new IvParameterSpec(iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, cipher.getParameters());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(KEYSTORE_IV_NAME, byteArrayToHex(cipher.getIV()));
                editor.apply();
            }
            return cipher;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            if (e instanceof KeyPermanentlyInvalidatedException) {
                deleteSecretKey();
                throw new Exception();
            }
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return;
        }
        Log.d("CryptoHelper", "generateSecretKey");
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider);
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    public SecretKey getSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(provider);
            // Before the keystore can be accessed, it must be loaded.
            keyStore.load(null);
            return ((SecretKey) keyStore.getKey(KEY_NAME, null));
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void deleteSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(provider);
            keyStore.load(null);
            keyStore.deleteEntry(KEY_NAME);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private String byteArrayToHex(byte[] bytes) {
        char[] hexChars = "0123456789ABCDEF".toCharArray();
        StringBuffer result = new StringBuffer();

        for (byte aByte : bytes) {
            int firstIndex = (aByte & 0xF0) >>> 4;
            int secondIndex = aByte & 0x0F;
            result.append(hexChars[firstIndex]);
            result.append(hexChars[secondIndex]);
        }
        Log.d("CryptoHelper", "byteArrayToHex - BYTES: " + Arrays.toString(bytes));
        Log.d("CryptoHelper", "byteArrayToHex - HEX: " + result);
        return result.toString();
    }

    private byte[] hexToByteArray(String hex) {
        String hexCharsString = "0123456789ABCDEF";
        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < hex.length(); i += 2) {
            int firstIndex = hexCharsString.indexOf(hex.charAt(i));
            int secondIndex = hexCharsString.indexOf(hex.charAt(i + 1));

            int octet = (firstIndex << 4) | secondIndex;
            Array.set(result, i >> 1, (byte) octet);
        }
        Log.d("CryptoHelper", "hexToByteArray - HEX: " + hex);
        Log.d("CryptoHelper", "hexToByteArray - BYTES: " + Arrays.toString(result));
        return result;
    }
}
