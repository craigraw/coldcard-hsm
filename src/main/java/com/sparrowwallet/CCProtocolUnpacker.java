package com.sparrowwallet;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CCProtocolUnpacker {
    public static DeviceId mypb(byte[] msg) throws DeviceProtocolException {
        String sign = getSign(msg);
        if(!sign.equals("mypb")) {
            throw new DeviceProtocolException("Invalid response to ncry: " + sign);
        }

        byte[] remotePubKey = Arrays.copyOfRange(msg, 4, 68);
        byte[] masterFingerprint = Arrays.copyOfRange(msg, 68, 72);
        byte[] xpub = new byte[0];

        long xpubLen = Utils.readUint32(msg, 72);
        if(xpubLen > 0) {
            xpub = Arrays.copyOfRange(msg, msg.length - (int)xpubLen, msg.length);
        }

        return new DeviceId(remotePubKey, masterFingerprint, xpub);
    }

    public static DeviceVersion vers(byte[] msg) throws DeviceProtocolException {
        String sign = getSign(msg);
        if(!sign.equals("asci")) {
            throw new DeviceProtocolException("Invalid response to vers: " + sign);
        }

        String text = new String(Arrays.copyOfRange(msg, 4, msg.length), StandardCharsets.UTF_8);
        String[] lines = text.split("\n");
        return new DeviceVersion(lines[0], lines[1], lines[2], lines[3], lines[4]);
    }

    private static String getSign(byte[] msg) {
        assert msg.length >= 4;
        return new String(Arrays.copyOfRange(msg, 0, 4), StandardCharsets.UTF_8);
    }
}
