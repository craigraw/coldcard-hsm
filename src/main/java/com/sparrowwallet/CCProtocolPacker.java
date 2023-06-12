package com.sparrowwallet;

import com.sparrowwallet.drongo.crypto.ECKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CCProtocolPacker {
    private static final byte USB_NCRY_V1 = 1;

    public static byte[] encryptStart(ECKey localKey) {
        ByteBuffer buf = ByteBuffer.allocate(72);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put("ncry".getBytes(StandardCharsets.UTF_8));
        buf.putInt(USB_NCRY_V1);
        buf.put(Arrays.copyOfRange(localKey.getPubKeyUncompressed(), 1, 65));
        return buf.array();
    }

    public static byte[] version() {
        return "vers".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] checkMitm() {
        return "mitm".getBytes(StandardCharsets.UTF_8);
    }
}
