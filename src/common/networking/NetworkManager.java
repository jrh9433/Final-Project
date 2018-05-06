package common.networking;

import common.GUIResource;
import common.Pair;
import common.message.MailMessage;
import common.message.SMTPMailMessage;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Single authority class to handle networking details and data transmission
 */
public class NetworkManager {
    /**
     * An instance of the server or client to use for logging
     */
    private final GUIResource guiClient;

    /**
     * The socket providing the connection
     */
    private final Socket connection;

    /**
     * The writer we use to send strings to the remote
     */
    private final PrintWriter netOut;

    /**
     * The scanner we use to read incoming data from the remote
     */
    private final Scanner netIn;

    /**
     * Raw incoming data stream used to check available without blocking
     * <p>
     * Should not be used to read any data content from the stream
     */
    private final InputStream rawIn;

    /**
     * Username this manager corresponds to
     */
    private String username;

    /**
     * Constructs a NetworkManager
     *
     * @param client     client resources for logging
     * @param connection socket providing connection
     * @throws IOException see {@link NetworkUtils#getNewInputScanner(Socket)} & {@link NetworkUtils#getNewOutputWriter(Socket)}
     */
    public NetworkManager(GUIResource client, Socket connection) throws IOException {
        this.guiClient = client;
        this.connection = connection;
        this.rawIn = connection.getInputStream();
        this.netOut = NetworkUtils.getNewOutputWriter(connection);
        this.netIn = NetworkUtils.getNewInputScanner(connection);
    }

    /**
     * Attempts to authenticate with the remote using the given details
     *
     * @param username username to identify as
     * @param password password to prove ownership
     * @return True if login successful, false if failure
     */
    public boolean attemptLogin(String username, String password) {
        this.sendMessage(username);
        this.sendMessages(true, password); // flag obfuscates log to hide password

        String response = netIn.nextLine();
        logln(response);

        if (response.equals(ProtocolConstants.LOGIN_SUCCESS)) {
            this.username = username;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Server-side util to read incoming login credentials
     *
     * @return new Pair, username is the key, password is the value
     */
    public Pair<String, String> readIncomingLoginInfo() {
        String userName = netIn.nextLine();
        this.username = userName;
        logln(userName);

        String password = netIn.nextLine();
        logln(password.replaceAll(".", "*")); // hide password in logs

        return new Pair<>(userName, password);
    }

    /**
     * Notifies the remote that the login failed
     */
    public void notifyLoginFailed() {
        this.sendMessage(ProtocolConstants.LOGIN_REJECTED);
        this.closeConnections();
    }

    /**
     * Notifies the remote that the login was a success
     */
    public void notifyLoginSuccess() {
        this.sendMessage(ProtocolConstants.LOGIN_SUCCESS);
    }

    /**
     * Called when the client wants to send a message
     *
     * @param mail mail to send
     */
    public void sendOutgoingMessage(MailMessage mail) {
        String smtpFrom = NetworkUtils.getSmtpFrom(mail.getSender());
        String[] smtpRecipients = NetworkUtils.getSmtpRecipients(mail.getTo(), mail.getCC());
        String[] messageContents = NetworkUtils.formatMailContentsForSend(mail);

        this.sendMessage(smtpFrom);
        logln(netIn.nextLine()); // 250 server OK

        for (String recipient : smtpRecipients) {
            this.sendMessage(recipient);
            logln(netIn.nextLine()); // 250 server OK
        }

        this.sendMessage(ProtocolConstants.DATA_HEADER);
        logln(netIn.nextLine()); // server 354

        for (String contentLine : messageContents) {
            this.sendMessage(contentLine);
        }

        this.sendMessage(ProtocolConstants.DATA_TERMINATOR);
        logln(netIn.nextLine()); // server 250, w/ queue
    }

    /**
     * Reads an incoming message from the data stream
     *
     * @param firstLine the first line we already parsed
     * @return message body contents
     */
    public SMTPMailMessage readRawIncomingMessage(String firstLine) {
        // the param is the first line of the message, it needs to be processed too
        String smtpFrom = firstLine.substring(11, firstLine.length() - 1); // break down to just address
        logln(firstLine);

        final String okay250 = ProtocolConstants.REQUEST_OKAY_CODE + " OK";
        this.sendMessage(okay250); // send 250

        // there can be a variable number of smtp recipients
        List<String> smptRecipients = new ArrayList<>();

        String nextLine = netIn.nextLine();
        while (nextLine.startsWith("RCPT TO")) {
            logln(nextLine);

            String smptRecip = nextLine.substring(9, nextLine.length() - 1); // break down to just address
            if (!smptRecip.isEmpty()) {
                smptRecipients.add(smptRecip);
            }

            this.sendMessage(okay250);
            nextLine = netIn.nextLine();
        }

        // at this point we've already read in one line past the recipients
        // that one line extra is the DATA header

        logln(nextLine); // don't re-read, DATA
        this.sendMessage(ProtocolConstants.END_DATA_WITH); // send 354

        nextLine = netIn.nextLine(); // read in looking for encrypted header
        logln(nextLine);
        boolean encrypted = nextLine.equals(ProtocolConstants.ENCRYPTION_HEADER); // set encrypted status

        nextLine = netIn.nextLine(); // read in next

        // content length is unknown to us
        List<String> messageContents = new ArrayList<>();

        while (!nextLine.equals(ProtocolConstants.DATA_TERMINATOR)) {
            logln(nextLine);
            messageContents.add(nextLine);
            nextLine = netIn.nextLine();
        }

        // at this point, nextLine has already read in the data terminator
        // all we need to do is send OK and its queue position
        logln(nextLine);

        this.sendMessage(okay250);

        // decrypt contents before passing around internally
        if (encrypted) {
            int reverseShift = 26 - ProtocolConstants.CAESAR_SHIFT_AMOUNT; // number of supported characters (26) minus our shift
            messageContents = NetworkUtils.caesarShift(messageContents, reverseShift);
        }

        return new SMTPMailMessage(encrypted, smtpFrom, smptRecipients.toArray(new String[0]), messageContents.toArray(new String[0]));
    }

    /**
     * Initializes connections and creates the handshake
     *
     * @param isServer True if we're serving as the server
     */
    public void initConnections(boolean isServer) {
        final String localHostName = getLocalHostname();

        if (isServer) {
            this.sendMessage(ProtocolConstants.INIT_HELLO_CODE + " " + localHostName + " ESMTP");
            logln(netIn.nextLine()); // client responds with HELO and its host

            String remoteHostname = getRemoteHostname();
            this.sendMessage(ProtocolConstants.REQUEST_OKAY_CODE + " Hello " + remoteHostname + ", I am glad to meet you");
        } else {
            logln(netIn.nextLine()); // server sends its own init and host
            this.sendMessage("HELO " + localHostName);
            logln(netIn.nextLine()); // server sends ack
        }
    }

    /**
     * Notifies the remote of our intent to disconnect, then waits for their response
     */
    public void sendDisconnect() {
        this.sendMessage("QUIT");
        logln(netIn.nextLine()); // 221
    }

    /**
     * Receives a remote's intent to disconnect via param, then responds to the remote
     *
     * @param message first line of already received SMTP QUIT
     */
    public void receiveDisconnect(String message) {
        logln(message); // already read in QUIT
        this.sendMessage(ProtocolConstants.TRANSMISSION_END_CODE + " " + this.getLocalHostname() + " Service closing transmission channel");
    }

    /**
     * Alerts the remote that we are unable to process their command
     */
    public void sendUnknownCommand() {
        this.sendMessage(ProtocolConstants.COMMAND_NOT_RECOGNIZED_ERR);
    }

    /**
     * Gets the hostname of the local client
     *
     * @return local hostname
     */
    public String getLocalHostname() {
        return connection.getLocalAddress().getHostName();
    }

    /**
     * Gets the hostname of the remote client
     *
     * @return remote hostname
     */
    public String getRemoteHostname() {
        return connection.getInetAddress().getHostName();
    }

    /**
     * Gets the IP address of the remote client
     *
     * @return remote IP
     */
    public String getRemoteIP() {
        return connection.getInetAddress().getHostAddress();
    }

    /**
     * Gets this user this manager corresponds to
     *
     * @return username
     */
    public String getUser() {
        return this.username;
    }

    /**
     * Convenience method to log a message
     *
     * @param msg message to log
     */
    private void logln(String msg) {
        guiClient.logln(msg);
    }

    /**
     * Closes the resources held by this instance
     */
    public void closeConnections() {
        try {
            this.netIn.close();
            this.netOut.close();
            connection.close();
        } catch (IOException ex) {
            logln("Unable to properly close connection to " + getRemoteHostname());
            ex.printStackTrace();
        }
    }

    /**
     * Checks the underlying data stream for new data
     *
     * @return True if new data
     */
    public synchronized boolean hasNewData() throws IOException {
        return rawIn.available() > 0;
    }

    /**
     * Returns whatever is currently on the incoming data stream
     *
     * @return Null or new data
     */
    public String rawRead() {
        return netIn.hasNextLine() ? netIn.nextLine() : null;
    }

    /**
     * Sends a network message to the remote
     * <p>
     * SMTP is a message based protocol
     *
     * @param message message contents
     */
    private void sendMessage(String message) {
        sendMessages(false, message);
    }

    /**
     * Writes multiple network messages in succession without waiting
     *
     * @param obfuscateLog True to obfuscate the contents of the messages in the log
     * @param messages     messages to send
     */
    private void sendMessages(boolean obfuscateLog, String... messages) {
        for (final String msg : messages) {
            guiClient.logln(obfuscateLog ? msg.replaceAll(".", "*") : msg);
            netOut.println(msg);
        }

        netOut.flush();
    }
}
