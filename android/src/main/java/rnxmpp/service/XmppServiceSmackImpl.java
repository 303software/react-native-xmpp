package rnxmpp.service;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.ReadableArray;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.mam.element.MamElements;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.rsm.packet.RSMSet;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import de.measite.minidns.util.InetAddressUtil;
import rnxmpp.ssl.UnsafeSSLContext;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener, MessageListener {
    private static final String TAG = "XMPPServiceSmackImpl";
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
    XMPPTCPConnection connection;

    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;

    public XmppServiceSmackImpl(XmppServiceListener xmppServiceListener) {
        this.xmppServiceListener = xmppServiceListener;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for(int i = 0; i < trustedHosts.size(); i++){
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    @Override
    public void fetchMessageArchive(Integer max, String user) {

        MamManager mamManager = MamManager.getInstanceFor(connection);

        DataForm form = new DataForm(DataForm.Type.submit);
        FormField field = new FormField(FormField.FORM_TYPE);
        field.setType(FormField.Type.hidden);
        field.addValue(MamElements.NAMESPACE);
        form.addField(field);

//        logger.log(Level.WARNING, "Sending request via MamManager for " + max + " records for user " + user);

        if(!user.equals(null) && !user.isEmpty()) {

            FormField formField = new FormField("with");
            formField.addValue(user);
            form.addField(formField);
        }

        // Lets set a default
        Integer maxLookup = 200;
        if (max != 0) {
            maxLookup = max;
        }

        // "" empty string for before
        RSMSet rsmSet = new RSMSet(maxLookup, "", RSMSet.PageDirection.before);
        MamManager.MamQueryResult mamQueryResult = null;
        try {
            mamQueryResult = mamManager.page(form, rsmSet);
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException | SmackException.NotConnectedException | InterruptedException | SmackException.NotLoggedInException e) {
            logger.log(Level.WARNING, "Could not send MamQuery: " + e);
            return;
        }

        // Lets loop through the returned messages and send them to our service listener
        for (Forwarded msg: mamQueryResult.forwardedMessages) {
            this.xmppServiceListener.onArchiveMessage( msg.toXML().toString());
        }
    }

    @Override
    public void connect(final String jid, String password, String authMethod, String hostname, Integer port) throws XmppStringprepException, IllegalArgumentException {
        final String[] jidParts = jid.split("@");
        String[] serviceNameParts = jidParts[1].split("/");
        String serviceName = serviceNameParts[0];

        DomainBareJid jidServiceName = null;

        jidServiceName = JidCreate.domainBareFrom(serviceName);

        XMPPTCPConnectionConfiguration.Builder confBuilder = XMPPTCPConnectionConfiguration.builder()
                .setServiceName(jidServiceName)
                .setUsernameAndPassword(jidParts[0], password)
                .setConnectTimeout(30000)
                // .setDebuggerEnabled(true)
                .setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .setKeystoreType(null)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);

        if (serviceNameParts.length>1){
            confBuilder.setResource(serviceNameParts[1]);
        } else {
            confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
        }
        if (hostname != null){
            try {
                InetAddress hostAddr = InetAddressUtil.ipv4From(hostname);
                confBuilder.setHostAddress(hostAddr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG,"Exception while connecting!", e);
                this.xmppServiceListener.onError(e);
            }
        }
        if (port != null){
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))){
            confBuilder.setCustomSSLContext(UnsafeSSLContext.INSTANCE.getContext());
        }
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        connection = new XMPPTCPConnection(connectionConfiguration);

        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | IOException e) {
                    logger.log(Level.SEVERE, "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                } catch (InterruptedException e) {
                    XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {
            }
        }.execute();
    }

    @Override
    public void genericConnect(final String password, final String username, final String serviceName) throws XmppStringprepException, IllegalArgumentException {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(username, password)
                .setXmppDomain(JidCreate.domainBareFrom(serviceName))
                .setDnssecMode(ConnectionConfiguration.DnssecMode.disabled)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .setKeystoreType(null).build();
        connection = new XMPPTCPConnection(config);

        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | IOException e) {
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                } catch (InterruptedException e) {
                    XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {
            }
        }.execute();
    }

    @Override
    public void message(String text, String to, String thread) throws XmppStringprepException, InterruptedException, SmackException.NotConnectedException {
        String chatIdentifier = (thread == null ? to : thread);

        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat chat = chatManager.getThreadChat(chatIdentifier);
        if (chat == null) {
            if (thread == null){
                chat = chatManager.createChat(JidCreate.entityBareFrom(to), this);
            }else{
                chat = chatManager.createChat(JidCreate.entityBareFrom(to), thread, this);
            }
        }
        chat.sendMessage(text);
    }

    @Override
    public void sendMucMessage(String text, String roomJid)  {
        Log.d(TAG,"mucMessage called with: "+text+", "+roomJid);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        // Create EntityBareJid.
        EntityBareJid entityBareJid = null;
        try {
            entityBareJid = JidCreate.entityBareFrom(roomJid);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
            Log.d(TAG, "Exception while creating entityBareJid: "+e.getMessage(),e);
        }
        // Create a MultiUserChat using an XMPPConnection for a room
        MultiUserChat muc = manager.getMultiUserChat(entityBareJid);
        try {
            muc.sendMessage(text);
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            Log.d(TAG, "NotConnectedException while sending MUC message: "+e.getMessage(),e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.d(TAG, "InteruptedException while sending MUC message: "+e.getMessage(),e);
        }
    }

    @Override
    public String sendSubscribe(String to, String from) {
        Log.d(TAG,String.format("sendSubscribe -- to:%s, from:%s",to,from));
        Presence subscribe = new Presence(Presence.Type.subscribe);
        try {
            if (!TextUtils.isEmpty(to)) {
                subscribe.setTo(JidCreate.bareFrom(to));
            }
            if (!TextUtils.isEmpty(from)) {
                subscribe.setFrom(JidCreate.bareFrom(from));
            }
            connection.sendStanza(subscribe);
        } catch (Exception e) {
            Log.d(TAG,"sendSubscribe Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public String sendUnsubscribe(String to, String from) {
        Log.d(TAG,String.format("sendUnsubscribe -- to:%s, from:%s",to,from));
        Presence unsubscribe = new Presence(Presence.Type.unsubscribe);
        try {
            if (!TextUtils.isEmpty(to)) {
                unsubscribe.setTo(JidCreate.bareFrom(to));
            }
            if (!TextUtils.isEmpty(from)) {
                unsubscribe.setFrom(JidCreate.bareFrom(from));
            }
            connection.sendStanza(unsubscribe);
        } catch (Exception e) {
            Log.d(TAG,"sendUnsubscribe Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public String sendSubscribed(String to, String from) {
        Log.d(TAG,String.format("sendSubscribed -- to:%s, from:%s",to,from));
        Presence subscribed = new Presence(Presence.Type.subscribed);
        try {
            if (!TextUtils.isEmpty(to)) {
                subscribed.setTo(JidCreate.bareFrom(to));
            }
            if (!TextUtils.isEmpty(from)) {
                subscribed.setFrom(JidCreate.bareFrom(from));
            }
            connection.sendStanza(subscribed);
        } catch (Exception e) {
            Log.d(TAG,"sendSubscribed Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public String sendUnsubscribed(String to, String from) {
        Log.d(TAG,String.format("sendUnsubscribed -- to:%s, from:%s",to,from));
        Presence unsubscribed = new Presence(Presence.Type.unsubscribed);
        try {
            if (!TextUtils.isEmpty(to)) {
                unsubscribed.setTo(JidCreate.bareFrom(to));
            }
            if (!TextUtils.isEmpty(from)) {
                unsubscribed.setFrom(JidCreate.bareFrom(from));
            }
            connection.sendStanza(unsubscribed);
        } catch (Exception e) {
            Log.d(TAG,"sendUnsubscribed Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public void presence(String to, String type) throws InterruptedException, SmackException.NotConnectedException {
        connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
    }

    @Override
    public void removeRoster(String to) throws XmppStringprepException {
        Log.d(TAG,"removeRoster called with: "+to);

        Roster roster = Roster.getInstanceFor(connection);
        RosterEntry rosterEntry = roster.getEntry(JidCreate.bareFrom(to));
        if (rosterEntry != null){
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException | InterruptedException e) {
                logger.log(Level.WARNING, "Could not remove roster entry: " + to);
            }
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        xmppServiceListener.onDisconnect(null);
    }

    @Override
    public void fetchRoster() {
        Log.d(TAG,"fetchRoster called");

        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not fetch roster", e);
        }
    }

    @Override
    public void processMessage(Message message) {
        Log.d(TAG, "processMessage: " + message.getBody());
        this.xmppServiceListener.onMessage(message);
    }

    public class StanzaPacket extends org.jivesoftware.smack.packet.Stanza {
         private String xmlString;

         public StanzaPacket(String xmlString) {
             super();
             this.xmlString = xmlString;
         }

        @Override
        public String toString() {
            return null;
        }

        @Override
         public XmlStringBuilder toXML() {
             XmlStringBuilder xml = new XmlStringBuilder();
             xml.append(this.xmlString);
             return xml;
         }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendPacket(packet);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException {
        if (packet instanceof IQ){
            this.xmppServiceListener.onIQ((IQ) packet);
        }else if (packet instanceof Presence){
            try {
                this.xmppServiceListener.onPresence((Presence) packet);
            } catch (XmppStringprepException e) {
                logger.log(Level.WARNING, "Got a Stanza, of unable to process presence: "+ e);
            }
        }else{
            logger.log(Level.WARNING, "Got a Stanza, of unknown subclass", packet.toXML());
        }
    }

    @Override
    public void connected(XMPPConnection connection) {

        if (connection.getUser() != null) {

            this.xmppServiceListener.onConnnect(connection.getUser().toString(), password);
        }
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {


        this.xmppServiceListener.onLogin(connection.getUser().toString(), password);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        Log.d(TAG, "processChatMessage: " + message.getBody());
        this.xmppServiceListener.onMessage(message);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        Log.d(TAG,"onRosterLoaded: "+roster.toString());
        Log.d(TAG,"onRosterLoaded - setting subscription mode to accept_all.");
        roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
        this.xmppServiceListener.onRosterReceived(roster);
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        logger.log(Level.INFO, "Connection was closed.");
    }

    @Override
    public void reconnectionSuccessful() {
        logger.log(Level.INFO, "Did reconnect");
    }

    @Override
    public void reconnectingIn(int seconds) {
        logger.log(Level.INFO, "Reconnecting in {0} seconds", seconds);
    }

    @Override
    public void reconnectionFailed(Exception e) {
        logger.log(Level.WARNING, "Could not reconnect", e);

    }

    @Override
    public void setRosterSubscriptionMode(String subscriptionMode) {
        Roster roster = Roster.getInstanceFor(connection);
        Roster.SubscriptionMode mode = Roster.SubscriptionMode.valueOf(subscriptionMode);
        roster.setSubscriptionMode(mode);
    }

    @Override
    public String createRosterEntry(String to) {
        Log.d(TAG,"createRosterEntry called with: "+to);
        Roster roster = Roster.getInstanceFor(connection);
        try {
            roster.createEntry(JidCreate.bareFrom(to),"",null);
        } catch (Exception e) {
            Log.d(TAG,"createRosterEntry Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public String removeRosterEntry(String jid) {
        Log.d(TAG,"removeRosterEntry called with: "+jid);
        Roster roster = Roster.getInstanceFor(connection);
        try {
            RosterEntry entry = roster.getEntry(JidCreate.bareFrom(jid));
            if (entry == null) {
                return "ENTRY NOT FOUND!";
            }
            roster.removeEntry(entry);
        } catch (Exception e) {
            Log.d(TAG,"removeRosterEntry Exception:"+e.getMessage(),e);
            e.printStackTrace();
            return e.toString();
        }
        return null;
    }

    @Override
    public String createInstantRoom(String jid, String nickname) {
        Log.d(TAG,"createInstantRoom called with: "+jid+", "+nickname);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            // Get a MultiUserChat using MultiUserChatManager
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(jid));

            Log.d(TAG, "CreateInstantRoom - Adding message listener for: " + jid);
            muc.addMessageListener(this);

            // Create the room and send an empty configuration form to make this an instant room.
            Resourcepart nicknameResourcepart = Resourcepart.from(nickname);
            Log.d(TAG,"Making instant room for: "+jid);
            muc.create(nicknameResourcepart).makeInstant();
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while creating room: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String destroyRoom(String jid, String reason) {
        Log.d(TAG,"destroyRoom called with: "+jid+", "+reason);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            // Get a MultiUserChat using MultiUserChatManager
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(jid));
            muc.destroy(reason,null);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception in destroyRoom: "+e.getMessage(),e);
            this.xmppServiceListener.onError(e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String joinOrCreateInstantRoom(String jid, String nickname) {
        Log.d(TAG,"joinOrCreateInstantRoom called with: "+jid+", "+nickname);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            // Get a MultiUserChat using MultiUserChatManager
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(jid));

            Log.d(TAG, "joinOrCreateInstantRoom - Adding message listener for: " + jid);
            muc.addMessageListener(this);

            // Create the room and send an empty configuration form to make this an instant room.
            Resourcepart nicknameResourcepart = Resourcepart.from(nickname);
            Log.d(TAG,"Calling createOrJoin for: "+nickname+" on jid: "+jid);
            MultiUserChat.MucCreateConfigFormHandle handle = muc.createOrJoin(nicknameResourcepart);
            if (handle != null) {
                Log.d(TAG,"createOrJoin succeeded, calling makeInstant");
                handle.makeInstant();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception in joinOrCreateInstantRoom: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String joinRoom(String jid, String nickname) {
        Log.d(TAG,"joinRoom called with: "+jid+", "+nickname);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            // Create a MultiUserChat using an XMPPConnection for a room
            MultiUserChat muc2 = manager.getMultiUserChat(JidCreate.entityBareFrom(jid));
            Log.d(TAG, "joinRoom - Adding message listener for: " + jid);
            muc2.addMessageListener(this);

            Resourcepart nicknameResourcepart = Resourcepart.from(nickname);
            Log.d(TAG,"Joining "+jid+" for "+nickname);
            muc2.join(nicknameResourcepart);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while joining room: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String leaveRoom(String jid) {
        Log.d(TAG,"leaveRoom called with: "+jid);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            // Create a MultiUserChat using an XMPPConnection for a room
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(jid));
            Log.d(TAG,"Leaving "+jid);
            muc.leave();
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while joining room: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String getHostedRooms(String jid) {
        Log.d(TAG,"getHostedRooms called with: "+jid);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
//        List<HostedRoom> allRooms = new ArrayList<>();
        try {
            List<HostedRoom> rooms = manager.getHostedRooms(JidCreate.domainBareFrom(jid));
            this.xmppServiceListener.onHostedRoomsReceived(rooms);
//            List<DomainBareJid> serviceDomains = manager.getXMPPServiceDomains();
//            for (DomainBareJid domainBareJid:serviceDomains) {
//                List<HostedRoom> rooms = manager.getHostedRooms(domainBareJid);
//                if (rooms != null) {
//                    allRooms.addAll(rooms);
//                }
//            }
//            this.xmppServiceListener.onRoomsReceived(allRooms);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while fetching hosted rooms: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String getJoinedRooms(String jid) {
        Log.d(TAG, "getJoinedRooms called with: "+jid);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        try {
            Log.d(TAG, "Getting joined rooms...");
            Set<EntityBareJid> joinedRooms = manager.getJoinedRooms();
            if (joinedRooms != null) {
                Log.d(TAG, "Found existing joined rooms: " + Integer.toString(joinedRooms.size()));
                for (EntityBareJid roomJid : joinedRooms) {
                    try {
                        Log.d(TAG, "Fetching MultiUserChat object for: " + roomJid.asEntityBareJidString());
                        MultiUserChat muc = manager.getMultiUserChat(roomJid);
//                        Log.d(TAG, "Adding message listener for: " + roomJid.asEntityBareJidString());
//                        muc.addMessageListener(XmppServiceSmackImpl.this);
                        Log.d(TAG, "Message listener added.");
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Log.d(TAG,"Exception while setting up listeners for existing joined rooms: "+e.getMessage(),e);
                        return e.toString();
                    }
                }
            }
            this.xmppServiceListener.onJoinedRoomsReceived(joinedRooms);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while fetching joined rooms: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }

    @Override
    public String getRoomOccupants(String roomJid) {
        Log.d(TAG, "getRoomInfo called with: "+roomJid);
        // Get the MultiUserChatManager
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        // Create EntityBareJid.
        EntityBareJid entityBareJid = null;
        try {
            entityBareJid = JidCreate.entityBareFrom(roomJid);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
            Log.d(TAG, "Exception while creating entityBareJid: "+e.getMessage(),e);
        }

        try {
            Log.d(TAG, "Getting Room info...");
            RoomInfo roomInfo = manager.getRoomInfo(entityBareJid);
            if (roomInfo == null) {
                Log.d(TAG, "RoomInfo is NULL!");
            } else {
                Log.d(TAG, "Getting contact jids...");
                List<String> contactJids = roomInfo.getContactJids();
                if (contactJids == null || contactJids.size() == 0) {
                    Log.d(TAG, "No contact JIDs returned!");
                }
                else {
                    this.xmppServiceListener.onRoomOccupantsReceived(roomJid, contactJids);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Exception while fetching room info: "+e.getMessage(),e);
            return e.toString();
        }
        return null;
    }
}
