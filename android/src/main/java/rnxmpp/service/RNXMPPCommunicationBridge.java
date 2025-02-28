package rnxmpp.service;

import android.support.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.List;
import java.util.Set;

import rnxmpp.utils.Parser;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class RNXMPPCommunicationBridge implements XmppServiceListener {

    public static final String RNXMPP_ERROR =            "RNXMPPError";
    public static final String RNXMPP_LOGIN_ERROR =      "RNXMPPLoginError";
    public static final String RNXMPP_MESSAGE =          "RNXMPPMessage";
    public static final String RNXMPP_ARCHIVED_MESSAGE = "RNXMPPArchivedMessage";
    public static final String RNXMPP_ROSTER =           "RNXMPPRoster";
    public static final String RNXMPP_HOSTED_ROOMS =     "RNXMPPHostedRooms";
    public static final String RNXMPP_JOINED_ROOMS =     "RNXMPPJoinedRooms";
    public static final String RNXMPP_ROOM_OCCUPANTS =     "RNXMPPRoomOccupants";
    public static final String RNXMPP_IQ =               "RNXMPPIQ";
    public static final String RNXMPP_PRESENCE =         "RNXMPPPresence";
    public static final String RNXMPP_CONNECT =          "RNXMPPConnect";
    public static final String RNXMPP_DISCONNECT =       "RNXMPPDisconnect";
    public static final String RNXMPP_LOGIN =            "RNXMPPLogin";
    ReactContext reactContext;

    public RNXMPPCommunicationBridge(ReactContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    public void onError(Exception e) {
        sendEvent(reactContext, RNXMPP_ERROR, e.getLocalizedMessage());
    }

    @Override
    public void onLoginError(String errorMessage) {
        sendEvent(reactContext, RNXMPP_LOGIN_ERROR, errorMessage);
    }

    @Override
    public void onLoginError(Exception e) {
        this.onLoginError(e.getLocalizedMessage());
    }

    @Override
    public void onMessage(Message message) {
        WritableMap params = Arguments.createMap();
        params.putString("thread", message.getThread());
        params.putString("subject", message.getSubject());
        params.putString("body", message.getBody());
        params.putString("from", message.getFrom().toString());
        params.putString("src", message.toXML().toString());
        sendEvent(reactContext, RNXMPP_MESSAGE, params);
    }

    @Override
    public void onRosterReceived(Roster roster) {
        WritableArray rosterResponse = Arguments.createArray();
        for (RosterEntry rosterEntry : roster.getEntries()) {
            WritableMap rosterProps = Arguments.createMap();
            rosterProps.putString("username", rosterEntry.getUser());
            rosterProps.putString("displayName", rosterEntry.getName());
            WritableArray groupArray = Arguments.createArray();
            for (RosterGroup rosterGroup : rosterEntry.getGroups()) {
                groupArray.pushString(rosterGroup.getName());
            }
            rosterProps.putArray("groups", groupArray);
            rosterProps.putString("subscription", rosterEntry.getType().toString());
            rosterResponse.pushMap(rosterProps);
        }
        sendEvent(reactContext, RNXMPP_ROSTER, rosterResponse);
    }

    @Override
    public void onHostedRoomsReceived(List<HostedRoom> rooms) {
        WritableArray roomsResponse = Arguments.createArray();
        for (HostedRoom room : rooms) {
            WritableMap roomProps = Arguments.createMap();
            roomProps.putString("name", room.getName());
            roomProps.putString("jid", room.getJid().asEntityBareJidString());
            roomsResponse.pushMap(roomProps);
        }
        sendEvent(reactContext, RNXMPP_HOSTED_ROOMS, roomsResponse);
    }

    @Override
    public void onJoinedRoomsReceived(Set<EntityBareJid> rooms) {
        WritableArray roomsResponse = Arguments.createArray();
        for (EntityBareJid roomJid : rooms) {
            WritableMap roomProps = Arguments.createMap();
            roomProps.putString("jid", roomJid.asEntityBareJidString());
            roomsResponse.pushMap(roomProps);
        }
        sendEvent(reactContext, RNXMPP_JOINED_ROOMS, roomsResponse);
    }

    @Override
    public void onRoomOccupantsReceived(String roomJid, List<String> contactJids) {
        WritableArray roomsResponse = Arguments.createArray();
        WritableMap roomNameProps = Arguments.createMap();
        roomNameProps.putString("roomJid", roomJid);
        roomsResponse.pushMap(roomNameProps);
        for (String contactJid : contactJids) {
            WritableMap roomProps = Arguments.createMap();
            roomProps.putString("contactJid", contactJid);
            roomsResponse.pushMap(roomProps);
        }
        sendEvent(reactContext, RNXMPP_ROOM_OCCUPANTS, roomsResponse);
    }

    @Override
    public void onIQ(IQ iq) {

        sendEvent(reactContext, RNXMPP_IQ, Parser.parse(iq.toString()));
    }

    public void onArchiveMessage(String message) {

        sendEvent(reactContext, RNXMPP_ARCHIVED_MESSAGE, Parser.parse(message));
    }

    @Override
    public void onPresence(Presence presence) throws XmppStringprepException {
        WritableMap presenceMap = Arguments.createMap();
        presenceMap.putString("type", presence.getType().toString());
        presenceMap.putString("from", presence.getFrom().toString());
        presenceMap.putString("status", presence.getStatus());
        presenceMap.putString("mode", presence.getMode().toString());
        sendEvent(reactContext, RNXMPP_PRESENCE, presenceMap);
    }

    @Override
    public void onConnnect(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_CONNECT, params);
    }

    @Override
    public void onDisconnect(Exception e) {
        if (e != null) {
            sendEvent(reactContext, RNXMPP_DISCONNECT, e.getLocalizedMessage());
        } else {
            sendEvent(reactContext, RNXMPP_DISCONNECT, null);
        }
    }

    @Override
    public void onLogin(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_LOGIN, params);
    }

    void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params) {
        reactContext
                .getJSModule(RCTNativeAppEventEmitter.class)
                .emit(eventName, params);
    }
}
