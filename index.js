"use strict";
var React = require("react-native");
var { NativeAppEventEmitter, NativeModules } = React;
var RNXMPP = NativeModules.RNXMPP;

var map = {
  message: "RNXMPPMessage",
  archiveMessage: "RNXMPPArchivedMessage",
  iq: "RNXMPPIQ",
  presence: "RNXMPPPresence",
  connect: "RNXMPPConnect",
  disconnect: "RNXMPPDisconnect",
  error: "RNXMPPError",
  loginError: "RNXMPPLoginError",
  login: "RNXMPPLogin",
  roster: "RNXMPPRoster",
  hostedRooms: "RNXMPPHostedRooms",
  joinedRooms: "RNXMPPJoinedRooms",
  roomOccupants: "RNXMPPRoomOccupants"
};

const LOG = message => {
  if (__DEV__) {
    console.log("react-native-xmpp: " + message);
  }
};

class XMPP {
  PLAIN = RNXMPP.PLAIN;
  SCRAM = RNXMPP.SCRAMSHA1;
  MD5 = RNXMPP.DigestMD5;

  constructor() {
    this.isConnected = false;
    this.isLogged = false;
    this.listeners = [
      NativeAppEventEmitter.addListener(
        map.connect,
        this.onConnected.bind(this)
      ),
      NativeAppEventEmitter.addListener(
        map.disconnect,
        this.onDisconnected.bind(this)
      ),
      NativeAppEventEmitter.addListener(map.error, this.onError.bind(this)),
      NativeAppEventEmitter.addListener(
        map.loginError,
        this.onLoginError.bind(this)
      ),
      NativeAppEventEmitter.addListener(map.login, this.onLogin.bind(this))
    ];
  }

  onConnected() {
    LOG("Connected");
    this.isConnected = true;
  }

  onLogin() {
    LOG("Login");
    this.isLogged = true;
  }

  onDisconnected(error) {
    LOG("Disconnected, error: " + error);
    this.isConnected = false;
    this.isLogged = false;
  }

  onError(text) {
    LOG("Error: " + text);
  }

  onLoginError(text) {
    this.isLogged = false;
    LOG("LoginError: " + text);
  }

  on(type, callback) {
    if (map[type]) {
      const listener = NativeAppEventEmitter.addListener(map[type], callback);
      this.listeners.push(listener);
      return listener;
    } else {
      throw "No registered type: " + type;
    }
  }

  removeListener(type) {
    if (map[type]) {
      for (var i = 0; i < this.listeners.length; i++) {
        var listener = this.listeners[i];
        if (listener.eventType === map[type]) {
          listener.remove();
          var index = this.listeners.indexOf(listener);
          if (index > -1) {
            this.listeners.splice(index, 1);
          }
          LOG(`Event listener of type "${type}" removed`);
        }
      }
    }
  }

  removeListeners() {
    for (var i = 0; i < this.listeners.length; i++) {
      this.listeners[i].remove();
    }

    this.listeners = [
      NativeAppEventEmitter.addListener(
        map.connect,
        this.onConnected.bind(this)
      ),
      NativeAppEventEmitter.addListener(
        map.disconnect,
        this.onDisconnected.bind(this)
      ),
      NativeAppEventEmitter.addListener(map.error, this.onError.bind(this)),
      NativeAppEventEmitter.addListener(
        map.loginError,
        this.onLoginError.bind(this)
      ),
      NativeAppEventEmitter.addListener(map.login, this.onLogin.bind(this))
    ];

    LOG("All event listeners removed");
  }

  trustHosts(hosts) {
    React.NativeModules.RNXMPP.trustHosts(hosts);
  }

  connect(
    username,
    password,
    auth = RNXMPP.SCRAMSHA1,
    hostname = null,
    port = 5222
  ) {
    React.NativeModules.RNXMPP.connect(
      username,
      password,
      auth,
      hostname,
      port
    );
  }

  genericConnect(password, username, serviceName) {
    React.NativeModules.RNXMPP.genericConnect(password, username, serviceName);
  }

  message(text, user, thread = null) {
    React.NativeModules.RNXMPP.message(text, user, thread);
  }

  fetchMessageArchive(max = 0, user = "") {
    React.NativeModules.RNXMPP.fetchMessageArchive(max, user);
  }

  sendStanza(stanza) {
    RNXMPP.sendStanza(stanza);
  }

  sendSubscribe(to, from) {
    RNXMPP.sendSubscribe(to, from);
  }

  sendSubscribed(to, from) {
    RNXMPP.sendSubscribed(to, from);
  }

  sendUnsubscribe(to, from) {
    RNXMPP.sendUnsubscribe(to, from);
  }

  sendUnsubscribed(to, from) {
    RNXMPP.sendUnsubscribed(to, from);
  }

  fetchRoster() {
    RNXMPP.fetchRoster();
  }

  setRosterSubscriptionMode(subscriptionMode) {
    RNXMPP.setRosterSubscriptionMode(subscriptionMode);
  }

  createRosterEntry(to) {
    RNXMPP.createRosterEntry(to);
  }

  removeRosterEntry(jid) {
    RNXMPP.removeRosterEntry(jid);
  }

  createInstantRoom(jid, nickname) {
    RNXMPP.createInstantRoom(jid, nickname);
  }

  joinOrCreateInstantRoom(jid, nickname) {
    RNXMPP.joinOrCreateInstantRoom(jid, nickname);
  }

  joinRoom(jid, nickname) {
    RNXMPP.joinRoom(jid, nickname);
  }

  getHostedRooms(jid) {
    RNXMPP.getHostedRooms(jid);
  }

  destroyRoom(jid, reason) {
    RNXMPP.destroyRoom(jid, reason);
  }

  getRoomOccupants(jid) {
    RNXMPP.getRoomOccupants(jid);
  }

  getJoinedRooms(jid) {
    RNXMPP.getJoinedRooms(jid);
  }

  leaveRoom(jid) {
    RNXMPP.leaveRoom(jid);
  }

  sendMucMessage(text, roomJid) {
    RNXMPP.sendMucMessage(text, roomJid);
  }

  presence(to, type) {
    React.NativeModules.RNXMPP.presence(to, type);
  }

  disconnect() {
    React.NativeModules.RNXMPP.disconnect();
  }
  disconnectAfterSending() {
    if (this.isConnected) {
      React.NativeModules.RNXMPP.disconnectAfterSending();
    }
  }
}

module.exports = new XMPP();
