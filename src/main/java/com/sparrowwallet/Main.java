package com.sparrowwallet;

import com.sun.jna.Platform;
import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

public class Main implements HidServicesListener {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public Main() {
        printPlatform();

        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
        // Use the v0.7.0 manual start feature to get immediate attach events
        hidServicesSpecification.setAutoStart(false);

        // Get HID services using custom specification
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        hidServices.addHidServicesListener(this);

        // Manually start the services to get attachment event
        System.out.println(ANSI_GREEN + "Manually starting HID services." + ANSI_RESET);
        hidServices.start();

        System.out.println(ANSI_GREEN + "Enumerating attached devices..." + ANSI_RESET);

        // Provide a list of attached devices
        for (HidDevice hidDevice : hidServices.getAttachedHidDevices()) {
            System.out.println(hidDevice);
        }
    }

    public void printPlatform() {
        // System info to assist with library detection
        System.out.println("Platform architecture: " + Platform.ARCH);
        System.out.println("Resource prefix: " + Platform.RESOURCE_PREFIX);
        System.out.println("Libusb activation: " + Platform.isLinux());
    }

    public static void main(String[] args) {
        Main main = new Main();
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        System.out.println(ANSI_BLUE + "Device attached: " + event + ANSI_RESET);
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        System.out.println(ANSI_YELLOW + "Device detached: " + event + ANSI_RESET);
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
        System.out.println(ANSI_RED + "HID failure: " + event + ANSI_RESET);
    }

    @Override
    public void hidDataReceived(HidServicesEvent event) {

    }
}