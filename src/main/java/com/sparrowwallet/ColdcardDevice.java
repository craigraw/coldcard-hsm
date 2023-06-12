package com.sparrowwallet;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.bip47.SecretPoint;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import java.util.List;

public class ColdcardDevice implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ColdcardDevice.class);

    private static final int COINKITE_VID = Integer.parseInt("d13e", 16);
    private static final int COLDCARD_PID = Integer.parseInt("cc10", 16);

    private static final int MAX_BLK_LEN = 2048;
    private static final int MAX_MSG_LEN = 4+4+4+MAX_BLK_LEN;

    private HidDevice hidDevice;
    private ECKey localKey;
    private DeviceId deviceId;
    private byte[] sessionKey;

    private Cipher encryptCipher;
    private Cipher decryptCipher;

    public ColdcardDevice() throws DeviceException {
        List<HidDevice> hidDevices = getHidDevices();
        for(HidDevice hidDevice : hidDevices) {
            if(hidDevice.getVendorId() == COINKITE_VID && hidDevice.getProductId() == COLDCARD_PID) {
                this.hidDevice = hidDevice;
            }
        }

        if(hidDevice == null) {
            throw new DeviceNotFoundException("Coldcard not found in attached USB devices");
        }

        if(!hidDevice.open()) {
            throw new DeviceInitializationException("Coldcard not be opened");
        }

        startEncryption();
    }

    public void close() {
        if(hidDevice != null && !hidDevice.isClosed()) {
            hidDevice.close();
        }
    }

    public void checkMitm() throws DeviceException {
        byte[] recv = sendRecv(CCProtocolPacker.checkMitm(), true, 5000);
        byte[] signatureBytes = CCProtocolUnpacker.getMitmSignature(recv);

        ECKey xpub = ECKey.fromPublicOnly(deviceId.getPubKeyString());

        byte[] rBytes = new byte[32];
        byte[] sBytes = new byte[32];

        ByteBuffer buffer = ByteBuffer.wrap(signatureBytes);
        buffer.get(); // Skip the sign byte (0x30)
        buffer.get(rBytes, 0, 32);
        buffer.get(sBytes, 0, 32);

        BigInteger r = new BigInteger(1, rBytes);
        BigInteger s = new BigInteger(1, sBytes);

        ECDSASignature signature = new ECDSASignature(r, s);

        if(!signature.verify(sessionKey, xpub.getPubKey())) {
            throw new DeviceMitmFailedException("Failed to verify signature - possible MitM attack!");
        }
    }

    private List<HidDevice> getHidDevices() {
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoStart(false);
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        return hidServices.getAttachedHidDevices();
    }

    private void startEncryption() throws DeviceException {
        localKey = new ECKey();
        byte[] msg = CCProtocolPacker.encryptStart(localKey);
        byte[] recv = sendRecv(msg, false);
        deviceId = CCProtocolUnpacker.mypb(recv);

        try {
            ECKey remotePubKey = deviceId.getRemotePubKey();
            new SecretPoint(localKey.getPrivKeyBytes(), remotePubKey.getPubKey());
            ECKey ecKey = remotePubKey.multiply(localKey.getPrivKey());
            byte[] secretBytes = ecKey.getPubKey();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sessionKey = digest.digest(Arrays.copyOfRange(secretBytes, 1, secretBytes.length));

            setupCiphers(sessionKey);
        } catch(Exception e) {
            throw new DeviceInitializationException("Could not determine session key", e);
        }
    }

    public byte[] sendRecv(byte[] msg) throws DeviceProtocolException {
        return sendRecv(msg, true);
    }

    public byte[] sendRecv(byte[] msg, boolean encrypt) throws DeviceProtocolException {
        return sendRecv(msg, encrypt, 3000);
    }

    public byte[] sendRecv(byte[] msg, boolean encrypt, int timeout) throws DeviceProtocolException {
        assert msg.length >= 4 && msg.length <= MAX_MSG_LEN : "msg length: " + msg.length;

        if(encryptCipher == null || decryptCipher == null) {
            encrypt = false; // disable encryption if not already enabled for this connection
        }

        if(encrypt) {
            msg = encryptRequest(msg);
        }

        int left = msg.length;
        int offset = 0;
        ByteArrayOutputStream response = new ByteArrayOutputStream();

        while(left > 0) {
            int here = Math.min(63, left);
            byte[] buf = new byte[64];
            System.arraycopy(msg, offset, buf, 1, here);

            if (here == left) {
                // final one in sequence
                buf[0] = (byte) (here | 0x80 | (encrypt ? 0x40 : 0x00));
            } else {
                // more will be coming
                buf[0] = (byte) here;
            }

            log.debug("Tx [" + here + "]: " + Utils.bytesToHex(buf) + " (0x" + Integer.toHexString(buf[1] & 0xFF) + ")");

            int rv = hidDevice.write(buf, buf.length, (byte)0);
            assert rv == buf.length;

            offset += here;
            left -= here;
        }

        byte flag;
        do {
            Byte[] buf = hidDevice.read(64, timeout != 0 ? timeout : 1000);

            if (buf.length == 0 && timeout != 0) {
                // give it another try
                buf = hidDevice.read(64, timeout);
            }

            assert buf.length != 0 : "timeout reading USB EP";

            flag = buf[0];
            byte[] readBuf = new byte[buf.length];
            for(int i = 0; i < buf.length; i++) {
                readBuf[i] = buf[i];
            }

            try {
                response.write(Arrays.copyOfRange(readBuf, 1, 1 + (flag & 0x3F)));
            } catch(IOException e) {
                throw new DeviceProtocolException("Error writing to Coldcard", e);
            }
        } while((flag & 0x80) == 0);

        byte[] responseArray;
        if((flag & 0x40) != 0) {
            log.debug("Enc response: " + Utils.bytesToHex(response.toByteArray()));
            responseArray = decryptResponse(response.toByteArray());
        } else {
            responseArray = response.toByteArray();
        }

        log.debug("Rx [" + responseArray.length + "]: " + Utils.bytesToHex(responseArray));

        return responseArray;
    }

    private void setupCiphers(byte[] sessionKey) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        SecretKeySpec key = new SecretKeySpec(sessionKey, "AES");
        encryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[16]));
        decryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
        decryptCipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[16]));
    }

    private byte[] encryptRequest(byte[] msg) throws DeviceProtocolException {
        try {
            return encryptCipher.doFinal(msg);
        } catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new DeviceProtocolException("Could not encrypt message", e);
        }
    }

    private byte[] decryptResponse(byte[] resp) throws DeviceProtocolException {
        try {
            return decryptCipher.doFinal(resp);
        } catch(IllegalBlockSizeException | BadPaddingException e) {
            throw new DeviceProtocolException("Could not encrypt message", e);
        }
    }
}
