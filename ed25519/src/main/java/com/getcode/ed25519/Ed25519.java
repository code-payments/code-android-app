package com.getcode.ed25519;

import android.util.Base64;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Ed25519 {
    public native static ArrayList<String> GenerateKeyPair(String seed);
    public native static byte[] CreateSeed16();
    public native static byte[] CreateSeed32();
    public native static byte[] Signature(byte[] message, byte[] priKey, byte[] pubKey);
    public native static boolean Verify(byte[] sig, byte[] message, byte[] pubKey);
    public native static boolean OnCurve(byte[] pubKey);

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("ed25519");
    }

    public static KeyPair createKeyPair() {
        String seed = Base64.encodeToString(Ed25519.createSeed32(), Base64.DEFAULT);
        List<String> generatedKeyPair = GenerateKeyPair(seed);
        return new KeyPair(generatedKeyPair.get(1), generatedKeyPair.get(0));
    }

    public static KeyPair createKeyPair(String seed) {
        List<String> generatedKeyPair = GenerateKeyPair(seed);
        return new KeyPair(generatedKeyPair.get(1), generatedKeyPair.get(0));
    }

    public static byte[] createSeed16() {
        return CreateSeed16();
    }

    public static byte[] createSeed32() {
        return CreateSeed32();
    }

    public static byte[] sign(byte[] message, KeyPair keyPair) {
        return Signature(
                message,
                keyPair.getPrivateKeyBytes(),
                keyPair.getPublicKeyBytes());
    }

    public static boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        return Verify(signature, message, publicKey);
    }

    public static boolean onCurve(byte[] pubKey) {
        return OnCurve(pubKey);
    }

    public static class KeyPair {
        private String publicKey;
        private String privateKey;

        public KeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public boolean verify(byte[] sig, byte[] message) {
            return Ed25519.Verify(sig, message, getPublicKeyBytes());
        }

        public byte[] getPrivateKeyBytes() {
            if (privateKey == null) return null;
            return Base64.decode(privateKey, Base64.DEFAULT);
        }

        public byte[] getPublicKeyBytes() {
            if (publicKey == null) return null;
            return Base64.decode(publicKey, Base64.DEFAULT);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            KeyPair keyPair = (KeyPair) o;
            return Objects.equals(publicKey, keyPair.publicKey) && Objects.equals(privateKey, keyPair.privateKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(publicKey, privateKey);
        }
    }
}
