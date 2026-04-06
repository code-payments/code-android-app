package com.getcode.ed25519;

import android.os.Parcel;
import android.os.Parcelable;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.math.Curve;
import net.i2p.crypto.eddsa.math.GroupElement;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Pure-Java shadow of the native Ed25519 class for JVM unit tests.
 * Uses net.i2p.crypto:eddsa for all cryptographic operations.
 *
 * Place this on the test classpath (via srcDir) so it shadows the real
 * Ed25519 class that calls System.loadLibrary in its static initializer.
 */
public class Ed25519 {

    private static final EdDSANamedCurveSpec ED25519_SPEC =
            EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    private static final Curve CURVE = ED25519_SPEC.getCurve();

    public static ArrayList<String> GenerateKeyPair(String seed) {
        byte[] seedBytes = Base64.getDecoder().decode(seed.trim());
        EdDSAPrivateKeySpec privSpec = new EdDSAPrivateKeySpec(seedBytes, ED25519_SPEC);
        EdDSAPrivateKey privKey = new EdDSAPrivateKey(privSpec);
        EdDSAPublicKeySpec pubSpec = new EdDSAPublicKeySpec(privKey.getA(), ED25519_SPEC);
        EdDSAPublicKey pubKey = new EdDSAPublicKey(pubSpec);

        byte[] rawPub = pubKey.getAbyte();
        // Private key format: seed(32) || publicKey(32) = 64 bytes
        byte[] rawPriv = new byte[64];
        System.arraycopy(seedBytes, 0, rawPriv, 0, 32);
        System.arraycopy(rawPub, 0, rawPriv, 32, 32);

        ArrayList<String> result = new ArrayList<>();
        result.add(Base64.getEncoder().encodeToString(rawPriv)); // index 0 = private
        result.add(Base64.getEncoder().encodeToString(rawPub));  // index 1 = public
        return result;
    }

    public static byte[] CreateSeed16() {
        byte[] seed = new byte[16];
        new SecureRandom().nextBytes(seed);
        return seed;
    }

    public static byte[] CreateSeed32() {
        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        return seed;
    }

    public static byte[] Signature(byte[] message, byte[] priKey, byte[] pubKey) {
        try {
            byte[] seed = new byte[32];
            System.arraycopy(priKey, 0, seed, 0, 32);
            EdDSAPrivateKeySpec privSpec = new EdDSAPrivateKeySpec(seed, ED25519_SPEC);
            EdDSAPrivateKey privKeyObj = new EdDSAPrivateKey(privSpec);

            EdDSAEngine signer = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
            signer.initSign(privKeyObj);
            signer.update(message);
            return signer.sign();
        } catch (Exception e) {
            throw new RuntimeException("Ed25519 signing failed", e);
        }
    }

    public static boolean Verify(byte[] sig, byte[] message, byte[] pubKey) {
        try {
            EdDSAPublicKeySpec pubSpec = new EdDSAPublicKeySpec(pubKey, ED25519_SPEC);
            EdDSAPublicKey pubKeyObj = new EdDSAPublicKey(pubSpec);

            EdDSAEngine verifier = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
            verifier.initVerify(pubKeyObj);
            verifier.update(message);
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean OnCurve(byte[] pubKey) {
        try {
            GroupElement ge = new GroupElement(CURVE, pubKey);
            ge.toCached();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // No static initializer — no System.loadLibrary calls

    public static KeyPair createKeyPair() {
        byte[] bytes = createSeed32();
        return createKeyPair(bytes);
    }

    public static KeyPair createKeyPair(String seed) {
        List<String> generatedKeyPair = GenerateKeyPair(seed);
        return new KeyPair(generatedKeyPair.get(1), generatedKeyPair.get(0), seed);
    }

    public static KeyPair createKeyPair(byte[] seed) {
        String seed64 = Base64.getEncoder().encodeToString(seed);
        List<String> generatedKeyPair = GenerateKeyPair(seed64);
        return new KeyPair(generatedKeyPair.get(1), generatedKeyPair.get(0), seed64);
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

    public static class KeyPair implements Parcelable {
        private final String publicKey;
        private final String privateKey;
        private final String seed;

        public KeyPair(String publicKey, String privateKey, String seed) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.seed = seed;
        }

        protected KeyPair(Parcel in) {
            publicKey = in.readString();
            privateKey = in.readString();
            seed = in.readString();
        }

        public static final Creator<KeyPair> CREATOR = new Creator<>() {
            @Override
            public KeyPair createFromParcel(Parcel in) {
                return new KeyPair(in);
            }

            @Override
            public KeyPair[] newArray(int size) {
                return new KeyPair[size];
            }
        };

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getSeed() {
            return seed;
        }

        public boolean verify(byte[] sig, byte[] message) {
            return Ed25519.Verify(sig, message, getPublicKeyBytes());
        }

        public byte[] getPrivateKeyBytes() {
            if (privateKey == null) return null;
            return Base64.getDecoder().decode(privateKey.trim());
        }

        public byte[] getPublicKeyBytes() {
            if (publicKey == null) return null;
            return Base64.getDecoder().decode(publicKey.trim());
        }

        public byte[] sign(byte[] message) {
            return Ed25519.sign(message, this);
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(publicKey);
            dest.writeString(privateKey);
            dest.writeString(seed);
        }
    }
}
