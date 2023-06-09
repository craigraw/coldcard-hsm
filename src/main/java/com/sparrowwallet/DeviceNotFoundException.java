package com.sparrowwallet;

public class DeviceNotFoundException extends DeviceException {
    public DeviceNotFoundException(String message) {
        super(message);
    }

    public DeviceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
