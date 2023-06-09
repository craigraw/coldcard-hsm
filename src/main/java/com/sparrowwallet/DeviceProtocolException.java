package com.sparrowwallet;

public class DeviceProtocolException extends DeviceException {
    public DeviceProtocolException(String message) {
        super(message);
    }

    public DeviceProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
