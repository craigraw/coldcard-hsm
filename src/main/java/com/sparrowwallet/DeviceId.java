package com.sparrowwallet;

import com.sparrowwallet.drongo.crypto.ECKey;

public record DeviceId(byte[] remotePubKey, byte[] masterFingerprint, byte[] xpub) {
    public ECKey getRemotePubKey() {
        byte[] uncompressedPubKey = new byte[65];
        uncompressedPubKey[0] = 0x04;
        System.arraycopy(remotePubKey, 0, uncompressedPubKey, 1, remotePubKey.length);
        return ECKey.fromPublicOnly(uncompressedPubKey);
    }
}
