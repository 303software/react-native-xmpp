package rnxmpp;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import org.jivesoftware.smack.SmackException;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.logging.Logger;

import rnxmpp.service.RNXMPPCommunicationBridge;
import rnxmpp.service.XmppServiceSmackImpl;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */
public class RNXMPPModule extends ReactContextBaseJavaModule implements rnxmpp.service.XmppService {

    public static final String MODULE_NAME = "RNXMPP";
    Logger logger = Logger.getLogger(RNXMPPModule.class.getName());
    XmppServiceSmackImpl xmppService;

    public RNXMPPModule(ReactApplicationContext reactContext) {
        super(reactContext);
        xmppService = new XmppServiceSmackImpl(new RNXMPPCommunicationBridge(reactContext));
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    @ReactMethod
    public void trustHosts(ReadableArray trustedHosts) {
        this.xmppService.trustHosts(trustedHosts);
    }

    @Override
    @ReactMethod
    public void connect(String jid, String password, String authMethod, String hostname, Integer port) throws XmppStringprepException {

        this.xmppService.connect(jid, password, authMethod, hostname, port);
    }

    @Override
    @ReactMethod
    public void message(String text, String to, String thread) throws XmppStringprepException, SmackException.NotConnectedException, InterruptedException {
        this.xmppService.message(text, to, thread);
    }

    @Override
    @ReactMethod
    public void presence(String to, String type) throws SmackException.NotConnectedException, InterruptedException {
        this.xmppService.presence(to, type);
    }

    @Override
    @ReactMethod
    public void removeRoster(String to) throws XmppStringprepException {
        this.xmppService.removeRoster(to);
    }

    @Override
    @ReactMethod
    public void disconnect() {
        this.xmppService.disconnect();
    }

    @Override
    @ReactMethod
    public void fetchRoster() {
        this.xmppService.fetchRoster();
    }

    @Override
    @ReactMethod
    public void fetchMessageArchive(Integer max, String user) { this.xmppService.fetchMessageArchive(max, user); }

    @Override
    @ReactMethod
    public void sendStanza(String stanza) {
        this.xmppService.sendStanza(stanza);
    }

    @Override
    @ReactMethod
    public void setRosterSubscriptionMode(String subscriptionMode) {
        this.xmppService.setRosterSubscriptionMode(subscriptionMode);
    }

    @Override
    @ReactMethod
    public String createRosterEntry(String to) {
        return this.xmppService.createRosterEntry(to);
    }

    @Override
    @ReactMethod
    public String removeRosterEntry(String jid) {
        return this.xmppService.removeRosterEntry(jid);
    }
}
