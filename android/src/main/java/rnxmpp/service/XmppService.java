package rnxmpp.service;

import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppService {

    @ReactMethod
    public void trustHosts(ReadableArray trustedHosts);

    @ReactMethod
    void genericConnect(String password, String username, String serviceName) throws XmppStringprepException, IllegalArgumentException;

    @ReactMethod
    void connect(String jid, String password, String authMethod, String hostname, Integer port) throws XmppStringprepException, IllegalArgumentException;

    @ReactMethod
    void message(String text, String to, String thread) throws XmppStringprepException, InterruptedException, SmackException.NotConnectedException;

    @ReactMethod
    String sendSubscribe(String to, String from);

    @ReactMethod
    String sendSubscribed(String to, String from);

    @ReactMethod
    String sendUnsubscribe(String to, String from);

    @ReactMethod
    String sendUnsubscribed(String to, String from);

    @ReactMethod
    void presence(String to, String type) throws InterruptedException, SmackException.NotConnectedException;

    @ReactMethod
    void removeRoster(String to) throws XmppStringprepException;

    @ReactMethod
    void disconnect();

    @ReactMethod
    void fetchRoster();

    @ReactMethod
    void fetchMessageArchive(Integer max, String user) throws XMPPException.XMPPErrorException, SmackException.NotLoggedInException, SmackException.NotConnectedException, InterruptedException, SmackException.NoResponseException, XmppStringprepException;

    @ReactMethod
    void sendStanza(String stanza);

    @ReactMethod
    void setRosterSubscriptionMode(String subscriptionMode);

    @ReactMethod
    String createRosterEntry(String to);

    @ReactMethod
    String removeRosterEntry(String jid);

    @ReactMethod
    String createInstantRoom(String jid, String roomNickname);

    @ReactMethod
    String destroyRoom(String jid, String reason);

    @ReactMethod
    String joinOrCreateInstantRoom(String jid, String roomNickname);

    @ReactMethod
    String joinRoom(String jid, String roomNickname);

    @ReactMethod
    String leaveRoom(String jid);

    @ReactMethod
    String getHostedRooms(String jid);

    @ReactMethod
    String getJoinedRooms(String jid);

    @ReactMethod
    String getRoomOccupants(String roomJid);

    @ReactMethod
    void sendMucMessage(String text, String roomJid);
}
