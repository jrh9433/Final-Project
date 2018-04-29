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
    protected final GUIResource guiClient;

    /**
     * The socket providing the connection
     */
    protected final Socket connection;

    /**
     * The writer we use to send strings to the remote
     */
    protected final PrintWriter netOut;

    /**
     * The scanner we use to read incoming data from the remote
     */
    protected final Scanner netIn;

    /**
     * Raw incoming data stream used to check available without blocking
     * <p>
     * Should not be used to read any data content from the stream
     */
    private final InputStream rawIn;

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
        NetworkUtils.sendMessage(guiClient, netOut, username);
        NetworkUtils.sendMessages(guiClient, netOut, true, password); // flag obfuscates log to hide password

        String response = netIn.nextLine();
        logln(response);

        if (response.equals(ProtocolConstants.LOGIN_SUCCESS)) {
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
        logln(userName);

        String password = netIn.nextLine();
        logln(password.replaceAll(".", "*")); // hide password in logs

        return new Pair<>(userName, password);
    }

    /**
     * Notifies the remote that the login failed
     */
    public void notifyLoginFailed() {
        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.LOGIN_REJECTED);
        this.closeConnections();
    }

    /**
     * Notifies the remote that the login was a success
     */
    public void notifyLoginSuccess() {
        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.LOGIN_SUCCESS);
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

        NetworkUtils.sendMessage(guiClient, netOut, smtpFrom);
        logln(netIn.nextLine()); // 250 server OK

        for (String recipient : smtpRecipients) {
            NetworkUtils.sendMessage(guiClient, netOut, recipient);
            logln(netIn.nextLine()); // 250 server OK
        }

        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.DATA_HEADER);
        logln(netIn.nextLine()); // server 354

        for (String string : messageContents) {
            NetworkUtils.sendMessage(guiClient, netOut, string);
        }

        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.DATA_TERMINATOR);
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
        NetworkUtils.sendMessage(guiClient, netOut, okay250); // send 250

        // there can be a variable number of smtp recipients
        List<String> smptRecipients = new ArrayList<>();

        String nextLine = netIn.nextLine();
        while (nextLine.startsWith("RCPT TO")) {
            logln(nextLine);

            String smptRecip = nextLine.substring(9, nextLine.length() - 1); // break down to just address
            smptRecipients.add(smptRecip);

            NetworkUtils.sendMessage(guiClient, netOut, okay250);
            nextLine = netIn.nextLine();
        }

        // at this point we've already read in one line past the recipients
        // that one line extra is the DATA header

        logln(nextLine); // don't re-read, DATA
        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.END_DATA_WITH); // send 354

        nextLine = netIn.nextLine(); // read in looking for encrypted header
        boolean encrypted = false;
        if (nextLine.contains(ProtocolConstants.ENCRYPTION_HEADER)) {
            logln(nextLine);
            encrypted = true;
            nextLine = netIn.nextLine(); // read in first line of actual content
        }

        // content length is unknown to us
        List<String> messageContents = new ArrayList<>();

        logln(nextLine); // first line of content was read in by encrypted handling above
        while (!nextLine.equals(ProtocolConstants.DATA_TERMINATOR)) {
            logln(nextLine);
            messageContents.add(nextLine);
            nextLine = netIn.nextLine();
        }

        // at this point, nextLine has already read in the data terminator
        // all we need to do is send OK and its queue position
        logln(nextLine);

        NetworkUtils.sendMessage(guiClient, netOut, okay250);

        // decrypt contents before passing around internally
        if (encrypted) {
            int reverseShift = 26 - ProtocolConstants.CAESAR_SHIFT_AMOUNT; // number of supported characters (26) minus our shift
            messageContents = NetworkUtils.caesarShift(messageContents, reverseShift);
        }

        return new SMTPMailMessage(encrypted, smtpFrom, smptRecipients.toArray(new String[0]), messageContents.toArray(new String[0]));
    }

    public void initConnections(boolean isServer) {
        final String localHostName = getLocalHostname();

        if (isServer) {
            NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.INIT_HELLO_CODE + " " + localHostName + " ESMTP");
            logln(netIn.nextLine()); // client responds with HELO and its host

            String remoteHostname = getRemoteHostname();
            NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.REQUEST_OKAY_CODE + " Hello " + remoteHostname + ", I am glad to meet you");
        } else {
            logln(netIn.nextLine()); // server sends its own init and host
            NetworkUtils.sendMessage(guiClient, netOut, "HELO " + localHostName);
            logln(netIn.nextLine()); // server sends ack
        }
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

    public String getRemoteIP() {
        return connection.getInetAddress().getHostAddress();
    }

    /**
     * Convenience method to log a message
     *
     * @param msg message to log
     */
    protected void logln(String msg) {
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
}
