package rnxmpp.service;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.List;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppServiceListener {
    void onError(Exception e);
    void onLoginError(String errorMessage);
    void onLoginError(Exception e);
    void onMessage(Message message);
    void onRosterReceived(Roster roster);
    void onRoomsReceived(List<HostedRoom> rooms);
    void onIQ(IQ iq);
    void onArchiveMessage(String message);
    void onPresence(Presence presence) throws XmppStringprepException;
    void onConnnect(String username, String password);
    void onDisconnect(Exception e);
    void onLogin(String username, String password);

}
