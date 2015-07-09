/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.example;

import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.net.ssl.SSLContext;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.LwM2mClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.Value;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import org.eclipse.leshan.core.response.ValueResponse;

/*
 * To build: 
 * mvn assembly:assembly -DdescriptorId=jar-with-dependencies
 * To use:
 * java -jar target/leshan-client-*-SNAPSHOT-jar-with-dependencies.jar 127.0.0.1 5683
 */
public class LeshanClientExample {
    private String registrationID;

    public static void main(final String[] args) throws KeyManagementException, NumberFormatException,
            NoSuchAlgorithmException {
        if (args.length < 2 || args.length > 5) {
            System.out.println("Usage:\njava -jar "
                            + "target/leshan-client-example-*-SNAPSHOT-jar-with-dependencies.jar [ClientIP] [ClientPort] ServerIP ServerPort [UDP|TCP|TLS]");
        } else {
            switch (args.length) {
            case 2:
                new LeshanClientExample("0", 0, args[0], Integer.parseInt(args[1]), "UDP");
                break;
            case 3:
                new LeshanClientExample("0", 0, args[0], Integer.parseInt(args[1]), args[2]);
                break;
            case 4:
                new LeshanClientExample(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), "UDP");
                break;
            case 5:
                new LeshanClientExample(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4]);
            }
        }
    }

    public LeshanClientExample(final String localHostName, final int localPort, final String serverHostName,
            final int serverPort, final String binding) throws NoSuchAlgorithmException, KeyManagementException {

        // Initialize object list
        final ObjectsInitializer initializer = new ObjectsInitializer();
        initializer.setClassForObject(3, Device.class);

        // Create client
        final InetSocketAddress clientAddress = new InetSocketAddress(localHostName, localPort);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClientBuilder builder = new LeshanClientBuilder();

        LwM2mClient client;
        switch (binding) {
        case "TCP":
            client = builder.setObjectsInitializer(initializer).setLocalAddress(clientAddress)
                    .setServerAddress(serverAddress).addBindingModeTCPClient().configure().build();
            break;
        case "TLS":
            final SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            client = builder.setObjectsInitializer(initializer).setLocalAddress(clientAddress)
                    .setServerAddress(serverAddress).addBindingModeTCPClient().secure().setSSLContext(context)
                    .configure()
                    .configure().build();
            break;
        default:
            client = builder.setObjectsInitializer(initializer).setLocalAddress(clientAddress)
                    .setServerAddress(serverAddress).addBindingModeUDP().configure().build();
        }

        // Start the client
        client.start();

        // Register to the server provided
        final String endpointIdentifier = UUID.randomUUID().toString();
        final RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));

        // Report registration response.
        System.out.println("Device Registration (Success? " + response.getCode() + ")");
        if (response.getCode() == ResponseCode.CREATED) {
            System.out.println("\tDevice: Registered Client Location '" + response.getRegistrationID() + "'");
            registrationID = response.getRegistrationID();
        } else {
            // TODO Should we have a error message on response ?
            // System.err.println("\tDevice Registration Error: " + response.getErrorMessage());
            System.err.println("\tDevice Unable to connect.  Registration Error: " + response.getCode());
        }

        setShutdownHook(client);
    }

    private void setShutdownHook(final LwM2mClient client) {
        // Deregister on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (registrationID != null) {
                    System.out.println("\tDevice: Deregistering Client '" + registrationID + "'");
                    client.send(new DeregisterRequest(registrationID));
                    client.stop();
                }
            }
        });
    }

    public static class Device extends BaseInstanceEnabler {

        public Device() {
            // notify new date each 5 second
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    fireResourceChange(13);
                }
            }, 5000, 5000);
        }

        @Override
        public ValueResponse read(final int resourceid) {
            System.out.println("Read on resource " + resourceid);
            switch (resourceid) {
            case 0:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getManufacturer())));
            case 1:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getModelNumber())));
            case 2:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getSerialNumber())));
            case 3:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getFirmwareVersion())));
            case 9:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newIntegerValue(getBatteryLevel())));
            case 10:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newIntegerValue(getMemoryFree())));
            case 13:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newDateValue(getCurrentTime())));
            case 14:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getUtcOffset())));
            case 15:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getTimezone())));
            case 16:
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(getSupportedBinding())));
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public LwM2mResponse execute(final int resourceid, final byte[] params) {
            System.out.println("Execute on resource " + resourceid + " params " + params);
            return new LwM2mResponse(ResponseCode.CHANGED);
        }

        @Override
        public LwM2mResponse write(final int resourceid, final LwM2mResource value) {
            System.out.println("Write on resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 13:
                return new LwM2mResponse(ResponseCode.NOT_FOUND);
            case 14:
                setUtcOffset((String) value.getValue().value);
                fireResourceChange(resourceid);
                return new LwM2mResponse(ResponseCode.CHANGED);
            case 15:
                setTimezone((String) value.getValue().value);
                fireResourceChange(resourceid);
                return new LwM2mResponse(ResponseCode.CHANGED);
            default:
                return super.write(resourceid, value);
            }
        }

        private String getManufacturer() {
            return "Leshan Example Device";
        }

        private String getModelNumber() {
            return "generic";
        }

        private String getSerialNumber() {
            return "SN-LWM2M-000-001";
        }

        private String getFirmwareVersion() {
            return "1.0.0";
        }

        private int getBatteryLevel() {
            final Random rand = new Random();
            return rand.nextInt(100);
        }

        private int getMemoryFree() {
            final Random rand = new Random();
            return rand.nextInt(50) + 114;
        }

        private Date getCurrentTime() {
            return new Date();
        }

        private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());;

        private String getUtcOffset() {
            return utcOffset;
        }

        private void setUtcOffset(final String t) {
            utcOffset = t;
        }

        private String timeZone = TimeZone.getDefault().getID();

        private String getTimezone() {
            return timeZone;
        }

        private void setTimezone(final String t) {
            timeZone = t;
        }

        private String getSupportedBinding() {
            return "U";
        }
    }
}
