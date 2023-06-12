package com.sparrowwallet;

public class ColdcardClient {
    public DeviceVersion getVersion() throws DeviceException {
        try(ColdcardDevice device = new ColdcardDevice()) {
            byte[] msg = CCProtocolPacker.version();
            byte[] recv = device.sendRecv(msg);
            return CCProtocolUnpacker.vers(recv);
        }
    }

    public byte[] sign(byte[] psbt) throws DeviceException {
        try(ColdcardDevice device = new ColdcardDevice()) {
            device.checkMitm();
        }

        return new byte[0];
    }

}
