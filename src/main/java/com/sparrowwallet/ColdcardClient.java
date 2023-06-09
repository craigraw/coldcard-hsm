package com.sparrowwallet;

public class ColdcardClient {
    public DeviceVersion getVersion() throws DeviceException {
        try(ColdcardDevice device = new ColdcardDevice()) {
            byte[] msg = CCProtocolPacker.version();
            byte[] recv = device.sendRecv(msg);
            return CCProtocolUnpacker.vers(recv);
        }
    }
}
