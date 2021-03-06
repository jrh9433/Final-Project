package common;

import common.message.MailMessage;
import common.message.SMTPMailMessage;
import common.networking.NetworkManager;

import javax.swing.*;
import java.io.IOException;
import java.util.Vector;

/**
 * Worker class for processing, sending, and reading data.
 * <p>
 * Shared across both the client and server implementations. The server is expected
 * to spin up one instance per client, while the client is expected to spin up only
 * a single instance for itself.
 */
public class SharedWorkerThread extends Thread {

    /**
     * Whether this is running as the server or the client
     */
    private final boolean isServer;

    /**
     * Network manager instance to handle networking
     */
    private final NetworkManager networkManager;

    /**
     * Pending tasks waiting to be run by this thread
     */
    private final Vector<Runnable> pendingTasks = new Vector<>();

    /**
     * Provides access to GUI resources as well as allowing the thread to send messages
     * to the implementing classes.
     */
    private final GUIResource guiClient;

    /**
     * Loop control variable
     */
    private boolean isConnected = true;

    /**
     * Creates a new SharedWorkerThread
     *
     * @param guiClient Instance of a client
     * @param manager   Instance of NetworkManager
     */
    public SharedWorkerThread(GUIResource guiClient, NetworkManager manager, boolean actAsServer) {
        super("Shared Worker Thread : " + (actAsServer ? "Server" : "Client") + " : " + manager.getRemoteIP());

        this.guiClient = guiClient;
        this.isServer = actAsServer;
        this.networkManager = manager;
    }

    /**
     * Returns true if this thread is acting as a server thread
     *
     * @return True if server
     */
    private boolean isServer() {
        return this.isServer;
    }

    /**
     * Returns true if this thread is acting as a client thread
     *
     * @return True if client
     */
    private boolean isClient() {
        return !this.isServer;
    }

    /**
     * Convenience method to log a message
     *
     * @param msg String to log
     */
    private void logln(String msg) {
        guiClient.logln(msg);
    }

    /**
     * Stops the thread without side effects
     */
    public void haltThread() {
        this.isConnected = false;
    }

    /**
     * Gets this worker's NetworkManager
     *
     * @return worker's NetworkManager
     */
    public NetworkManager getNetworkManager() {
        return this.networkManager;
    }

    @Override
    public void run() {
        initConnection();

        while (isConnected) {
            // process pending tasks like sending out data
            processPendingTasks();

            boolean hasNewData;
            try {
                hasNewData = networkManager.hasNewData(); // async safe
            } catch (IOException ex) {
                logln("Unable to read from data stream!");
                continue;
            }

            if (hasNewData) {
                this.processIncomingData(networkManager.rawRead());
            } else {
                // sleep this thread for a bit so we aren't
                // just pegging out the CPU and eating all its cycles
                try {
                    Thread.sleep(150); // 0.15 seconds
                } catch (InterruptedException ignored) {
                    // fall through to continue
                }
            }
        }
    }

    /**
     * Accepts an incoming message, determines what it is, and processes it as needed.
     *
     * @param message message to process
     */
    private void processIncomingData(String message) {
        String command = message.toUpperCase(); // normalize to uppercase

        if (command.startsWith("MAIL FROM")) {
            processIncomingMessage(message);
            return;
        }

        if (command.equals("QUIT")) {
            networkManager.receiveDisconnect(message);
            this.disconnect();
            guiClient.updateServerForUserDisconnect(networkManager.getUser());
            return;
        }

        if (command.startsWith("500")) {
            guiClient.showMessageDialog("Received Error Code 500 (Server unable to process command)", "Server Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // sends 500 unknown if we get this far without processing data
        networkManager.sendUnknownCommand();
    }

    /**
     * Handles processing an incoming message and notifying the proper elements
     *
     * @param message first line of SMTP MAIL FROM
     */
    private void processIncomingMessage(String message) {
        SMTPMailMessage mail = networkManager.readRawIncomingMessage(message);
        guiClient.onMailReceived(mail);
    }

    /**
     * Schedules a safe disconnection, notifying the remote and then disconnecting
     */
    public void scheduleDisconnect() {
        this.pendingTasks.add(this::notifyAndDisconnect);
    }

    /**
     * Notifies the remote of the disconnection then disconnects and updates as needed
     */
    private void notifyAndDisconnect() {
        networkManager.sendDisconnect();
        networkManager.closeConnections();
        this.disconnect();
    }

    /**
     * Iterates through all the scheduled tasks, performs them, then clears the list
     */
    private void processPendingTasks() {
        if (pendingTasks.size() == 0) {
            return; // nothing to do? just skip all of this
        }

        pendingTasks.forEach(Runnable::run);
        pendingTasks.clear();
    }

    /**
     * Disconnects this thread from the remote
     */
    private void disconnect() {
        this.isConnected = false;
        guiClient.updateForDisconnect();
    }

    /**
     * Blocking method to initialize the connection
     */
    private void initConnection() {
        networkManager.initConnections(isServer);
    }

    /**
     * Schedules an outgoing message to be sent
     *
     * @param mail message to send
     */
    public void scheduleMessageSend(MailMessage mail) {
        final String from = mail.getSender();
        if (from == null || from.equals("")) {
            throw new IllegalArgumentException("Cannot send a message with no from address!");
        }

        final String[] to = mail.getTo();
        final String[] cc = mail.getCC();

        if (to.length == 0 && cc.length == 0) {
            throw new IllegalArgumentException("Cannot send a message without any recipients!");
        }

        this.pendingTasks.add(() -> sendMessage(mail));
    }

    /**
     * Called when the client wants to send a message
     *
     * @param mail mail to send
     */
    private void sendMessage(MailMessage mail) {
        networkManager.sendOutgoingMessage(mail);
    }
}
