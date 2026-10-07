package com.paqrap.api;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM: cada cifrado incluye un nonce aleatorio de 12 bytes. */
final class DbPasswordCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private DbPasswordCipher() { }

    static String encrypt(String password, String encodedKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(encodedKey), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la contraseña de MySQL.", e);
        }
    }

    static String decrypt(String encodedPassword, String encodedKey) {
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPassword);
            if (payload.length < NONCE_BYTES + TAG_BITS / 8) {
                throw new IllegalArgumentException("Texto cifrado incompleto.");
            }
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(encodedKey), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("No se pudo descifrar la contraseña: clave incorrecta o archivo alterado.");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la contraseña de MySQL: revisa db.properties y PAQRAP_DB_KEY.");
        }
    }

    private static SecretKeySpec key(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalArgumentException("Falta PAQRAP_DB_KEY.");
        }
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("PAQRAP_DB_KEY debe contener 32 bytes en Base64.");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    /** Utilidad local: lee una línea por stdin y solo imprime el valor cifrado. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"encrypt".equals(args[0])) {
            throw new IllegalArgumentException("Uso: DbPasswordCipher encrypt (contraseña por stdin).");
        }
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null) throw new IllegalArgumentException("Falta la contraseña por stdin.");
        System.out.println(encrypt(password, System.getenv("PAQRAP_DB_KEY")));
    }
}
