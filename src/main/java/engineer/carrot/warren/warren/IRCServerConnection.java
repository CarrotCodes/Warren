package engineer.carrot.warren.warren;

import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import engineer.carrot.warren.warren.event.ServerConnectedEvent;
import engineer.carrot.warren.warren.event.ServerDisconnectedEvent;
import engineer.carrot.warren.warren.irc.AccessLevel;
import engineer.carrot.warren.warren.irc.Channel;
import engineer.carrot.warren.warren.irc.User;
import engineer.carrot.warren.warren.irc.messages.IRCMessage;
import engineer.carrot.warren.warren.irc.messages.core.*;
import engineer.carrot.warren.warren.ssl.WrappedSSLSocketFactory;
import engineer.carrot.warren.warren.util.IMessageQueue;
import engineer.carrot.warren.warren.util.MessageQueue;
import engineer.carrot.warren.warren.util.OutgoingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class IRCServerConnection implements IWarrenDelegate {
    private final Logger LOGGER = LoggerFactory.getLogger(IRCServerConnection.class);
    private static final long SOCKET_TIMEOUT_NS = 60 * 1000000000L;
    private static final int SOCKET_INTERRUPT_TIMEOUT_MS = 1 * 1000;

    private String nickname;
    private String login;
    private String realname;
    private String server;
    private int port;

    private ChannelManager joiningChannelManager;
    private ChannelManager joinedChannelManager;

    private UserManager userManager;

    private IMessageQueue outgoingQueue;
    private Thread outgoingThread;

    private boolean useFingerprints = false;
    private Set<String> acceptedCertificatesForHost;

    private boolean loginToNickserv = false;
    private String nickservPassword;
    private List<String> autoJoinChannels;
    private boolean shouldUsePlaintext = false;

    private EventBus eventBus;

    private IncomingHandler incomingHandler;

    private BufferedReader currentReader;

    public IRCServerConnection(String server, int port, String nickname, String login) {
        this.server = server;
        this.port = port;
        this.nickname = nickname;
        this.login = login;
        this.realname = login;

        this.initialise();
    }

    private void initialise() {
        this.outgoingQueue = new MessageQueue();
        this.eventBus = new EventBus();
        this.incomingHandler = new IncomingHandler(this, this.outgoingQueue, this.eventBus);

        this.userManager = new UserManager();

        this.joiningChannelManager = new ChannelManager();
        this.joinedChannelManager = new ChannelManager();
    }

    public void registerListener(Object object) {
        this.eventBus.register(object);
    }

    public void setNickservPassword(String password) {
        this.loginToNickserv = true;
        this.nickservPassword = password;
    }

    public void setAutoJoinChannels(List<String> channels) {
        this.autoJoinChannels = channels;
    }

    public void setSocketShouldUsePlaintext(boolean shouldUsePlaintext) {
        this.shouldUsePlaintext = shouldUsePlaintext;
    }

    public void setForciblyAcceptedCertificates(Set<String> certificateFingerprints) {
        this.useFingerprints = true;
        this.acceptedCertificatesForHost = certificateFingerprints;
    }

    public void connect() {
        Socket clientSocket;

        if (this.shouldUsePlaintext) {
            try {
                clientSocket = new Socket(server, port);
                clientSocket.setSoTimeout(SOCKET_INTERRUPT_TIMEOUT_MS); // Read once a second for interrupts
            } catch (IOException e) {
                LOGGER.error("Failed to set up plaintext socket");
                e.printStackTrace();
                return;
            }
        } else {
            WrappedSSLSocketFactory ssf = new WrappedSSLSocketFactory();

            if (this.useFingerprints) {
                ssf.forciblyAcceptCertificatesWithSHA1Fingerprints(this.acceptedCertificatesForHost);
            }

            try {
                clientSocket = ssf.disableDHEKeyExchange(ssf.createSocket(server, port));
                clientSocket.setSoTimeout(SOCKET_INTERRUPT_TIMEOUT_MS); // Read once a second for interrupts
                ((SSLSocket) clientSocket).startHandshake();
                //LOGGER.info(new Gson().toJson(clientSocket.getEnabledCipherSuites()));
            } catch (IOException e) {
                LOGGER.error("Failed to set up socket and start handshake");
                e.printStackTrace();
                return;
            }
        }

        OutputStreamWriter outToServer;
        try {
            outToServer = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8);
            this.currentReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        this.eventBus.post(new ServerConnectedEvent());

        Runnable outgoingRunnable = new OutgoingRunnable(this.outgoingQueue, outToServer);
        this.outgoingThread = new Thread(outgoingRunnable);
        this.outgoingThread.start();

        this.outgoingQueue.addMessageToQueue(new ChangeNicknameMessage(this.nickname));
        this.outgoingQueue.addMessageToQueue(new UserMessage(this.login, "8", this.realname));

        long lastResponseTime = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            String serverResponse;

            try {
                serverResponse = this.currentReader.readLine();
            } catch (SocketTimeoutException e) {
                if ((System.nanoTime() - lastResponseTime) > SOCKET_TIMEOUT_NS) {
                    // Socket read timed out - try to write a PING and read again

                    this.outgoingQueue.addMessageToQueue(new PingMessage("idle"));
                    lastResponseTime = System.nanoTime();
                }
                continue;
            } catch (IOException e) {
                LOGGER.error("Connection died: {}", e);

                try {
                    clientSocket.close();
                } catch (IOException e1) {
                    LOGGER.error("Failed to close socket: {}", e1);
                }

                break;
            }

            if (serverResponse == null) {
                LOGGER.error("Server response null");

                break;
            }

            lastResponseTime = System.nanoTime();
            IRCMessage message = IRCMessage.parseFromLine(serverResponse);

            if (message == null) {
                LOGGER.error("Parsed message was null");

                break;
            }

            if (message.command == null || message.command.length() < 3) {
                LOGGER.error("Malformed command in message");

                break;
            }

            boolean handledMessage = this.incomingHandler.handleIRCMessage(message, serverResponse);
            if (!handledMessage) {
                LOGGER.error("Failed to handle message. Original: {}", serverResponse);

                break;
            }
        }

        this.disconnect();
        this.postDisconnectedEvent();
        this.cleanupOutgoingThread();
    }

    public boolean disconnect() {
        if (this.currentReader == null) {
            return false;
        }

        try {
            this.currentReader.close();
        } catch (IOException e) {
        }

        return true;
    }

    private void postDisconnectedEvent() {
        this.eventBus.post(new ServerDisconnectedEvent());
    }

    private void cleanupOutgoingThread() {
        if (this.outgoingThread == null) {
            return;
        }

        this.outgoingThread.interrupt();

        try {
            this.outgoingThread.join();
        } catch (InterruptedException e) {

        }

        return;
    }

    // IBotDelegate

    @Override
    public String getBotNickname() {
        return this.nickname;
    }

    @Override
    public ChannelManager getJoiningChannels() {
        return this.joiningChannelManager;
    }

    @Override
    public ChannelManager getJoinedChannels() {
        return this.joinedChannelManager;
    }

    @Override
    public UserManager getUserManager() {
        return this.userManager;
    }

    @Override
    public void joinChannels(List<String> channels) {
        for (String channel : channels) {
            this.joiningChannelManager.addChannel(new Channel.Builder().name(channel).users(Maps.<String, User>newHashMap()).userAccessMap(Maps.<String, AccessLevel>newHashMap()).build());
        }

        this.outgoingQueue.addMessageToQueue(new JoinChannelsMessage(channels));
    }

    @Override
    public void moveJoiningChannelToJoined(String channel) {
        if (this.joiningChannelManager.containsChannel(channel)) {
            Channel cChannel = this.joiningChannelManager.getChannel(channel);
            this.joiningChannelManager.removeChannel(channel);

            this.joinedChannelManager.addChannel(cChannel);
            // TODO: fire joined channel event
        }
    }

    @Override
    public void sendPMToUser(String user, String contents) {
        PrivMsgMessage outgoingMessage = new PrivMsgMessage(null, user, contents);
        this.outgoingQueue.addMessageToQueue(outgoingMessage);
    }

    @Override
    public void sendMessageToChannel(Channel channel, String contents) {
        PrivMsgMessage outgoingMessage = new PrivMsgMessage(null, channel.name, contents);
        this.outgoingQueue.addMessageToQueue(outgoingMessage);
    }

    @Override
    public boolean shouldIdentify() {
        return this.loginToNickserv;
    }

    @Override
    public String getIdentifyPassword() {
        return this.nickservPassword;
    }

    @Override
    public List<String> getAutoJoinChannels() {
        return this.autoJoinChannels;
    }

    @Override
    public void setPrefixes(Set<Character> prefixes) {

    }

    @Override
    public Set<Character> getPrefixes() {
        return null;
    }
}
