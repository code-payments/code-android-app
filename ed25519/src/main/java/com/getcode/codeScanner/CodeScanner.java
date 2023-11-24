package com.getcode.codeScanner;

public class CodeScanner {
    public native static byte[] Encode(byte[] data);

    static {
        System.loadLibrary("kikCodes");
        System.loadLibrary("codeScanner");
    }

    public static byte[] encode(byte[] data) {
        return Encode(data);
    }
}
