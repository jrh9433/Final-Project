package server;

import common.*;
import common.message.MailMessage;
import common.message.SMTPMailMessage;
import common.networking.NetworkManager;
import common.networking.ProtocolConstants;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Server init class
 */
public class MessageServer extends JFrame implements GUIResource {

    /**
     * Default data save path
     */
    private static final String SAVED_USER_DATA_PATH = "./user-login-data.bin";

    /**
     * Manages users, passwords, and their data
     */
    protected final AuthenticationManager authenticationManager = new AuthenticationManager(this);

    /**
     * Starts the server
     */
    private final JButton jbStart = new JButton("Start");

    /**
     * Creates new users
     */
    private final JButton jbAddUser = new JButton("Add User");

    /**
     * Clears the log
     */
    private final JButton jbClear = new JButton("Clear");

    /**
     * Sets whether users are required to authenticate
     */
    private final JCheckBox jcbSecurity = new JCheckBox("Security", null, true);

    /**
     * Main text output area
     */
    private final JTextArea jtaLog = new JTextArea();

    /**
     * Processes mail queues to get messages to their recipients
     */
    private final QueueProcessingThread queueProcessor = new QueueProcessingThread(this);

    /**
     * Local addresses used to determine when this server is the intended target
     */
    private final Set<String> localAddresses = new HashSet<>();

    /**
     * Reference to server thread
     */
    private ConnectionThread connectionThread = null;

    /**
     * Class entry
     */
    public MessageServer() {
        this.setTitle("Message Server");
        this.setSize(600, 300);
        this.setMinimumSize(new Dimension(450, 175));
        this.setLocation(800, 100);
        this.setResizable(true);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (connectionThread != null) {
                    connectionThread.stopListening();
                }

                authenticationManager.writeUserData(SAVED_USER_DATA_PATH);
                queueProcessor.shutdown();
            }
        });

        // populate local addresses that this server can receive mail on
        populateLocalHosts();

        // start stop button
        jbStart.addActionListener(l -> onStartClicked());
        JPanel jpTopBar = new JPanel();
        jpTopBar.add(jbStart);
        jbAddUser.addActionListener(l -> onAddUserClicked());
        jpTopBar.add(jbAddUser);
        jbClear.addActionListener(l -> jtaLog.setText(""));
        jpTopBar.add(jbClear);
        jpTopBar.add(jcbSecurity);

        // output handling
        DefaultCaret caret = (DefaultCaret) jtaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        jtaLog.setLineWrap(true);
        jtaLog.setWrapStyleWord(true);
        jtaLog.setEditable(false);

        this.add(jpTopBar, BorderLayout.NORTH);
        this.add(new JScrollPane(jtaLog), BorderLayout.CENTER);

        // load saved data
        authenticationManager.loadSavedUserData(SAVED_USER_DATA_PATH);
        this.setVisible(true);
    }

    /**
     * JVM entry
     *
     * @param args Commandline args passed via the JVM
     */
    public static void main(String[] args) {
        new MessageServer();
    }

    /**
     * Handles the start button being clicked
     * <p>
     * Spins up the server thread and handles the state transition
     */
    private void onStartClicked() {
        jbStart.setEnabled(false);

        if (connectionThread == null) {
            // server not running, start it
            connectionThread = new ConnectionThread(this);
            connectionThread.start();

            jbStart.setText("Stop");
        } else {
            // server already running, stop it
            connectionThread.stopListening();
            connectionThread = null;

            jbStart.setText("Start");
        }

        jbStart.setEnabled(true);
    }

    /**
     * Called when the Add User button is clicked
     */
    private void onAddUserClicked() {
        new AddUserDialog(this, this);
    }

    /**
     * Logs specified message with a newline character
     *
     * @param str message to log
     */
    @Override
    public synchronized void logln(String str) {
        GUIResource.super.logln(str);
        jtaLog.append(str + "\n");
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public void onMailReceived(MailMessage mail) {
        SMTPMailMessage smtpMail = (SMTPMailMessage) mail;
        boolean submittedToOutgoing = false;

        // determine where it needs to go
        for (String target : smtpMail.getSmtpRecipients()) {
            if (target.isEmpty()) {
                continue;
            }

            // break into user and host
            String[] userHost = target.split("@", 2);

            if (userHost.length != 2) {
                logln("Malformed address: " + target);
                continue;
            }

            String host = userHost[1];
            if (isLocalServerAddress(host)) {
                // if local, put in internal queue
                queueProcessor.submitMessageToIncoming(userHost[0], mail);
                continue;
            } else {
                // only submit to outgoing once
                // it'll do its own loop to handle all recipients
                if (!submittedToOutgoing) {
                    // if external, put in relay queue
                    submittedToOutgoing = true;
                    queueProcessor.submitMessageToOutgoing(smtpMail);
                    continue;
                }
            }
        }
    }

    @Override
    public void updateServer(String username) {
        SharedWorkerThread clientWorker = connectionThread.connectedClients.get(username);
        if (clientWorker == null) {
            logln("Server attempted to remove a null client worker for: " + username);
            return;
        }

        logln(username + ":" + clientWorker.getNetworkManager().getRemoteIP() + " has disconnected");
        this.connectionThread.connectedClients.remove(username);
    }

    /**
     * Populates local addresses that will be used to determine where to send messages
     * or if they should be handled internally
     */
    private void populateLocalHosts() {
        try {
            InetAddress local = InetAddress.getLocalHost();

            localAddresses.add(local.getHostName()); // system hostname
            localAddresses.add(local.getHostAddress()); // system IP
            localAddresses.add(local.getCanonicalHostName()); // system FQDN
            localAddresses.add("localhost"); // localhost constant
        } catch (UnknownHostException ex) {
            logln("Unable to populate local addresses!");
            ex.printStackTrace();
            return;
        }

        logln("This server can receive mail on: ");
        localAddresses.forEach(this::logln);
        logln(""); // empty newline
    }

    /**
     * Checks whether or not the given address is this server
     *
     * @param address address to check
     * @return True, if the address belongs to this server
     */
    public boolean isLocalServerAddress(String address) {
        return localAddresses.contains(address);
    }

    /**
     * Relays a message to a locally connected client
     *
     * @param username who to send the message to
     * @param msg      message to send
     * @return whether we were able to successfully send the message or not
     */
    public boolean relayMessageToLocalUser(String username, MailMessage msg) {
        if (connectionThread != null) {
            SharedWorkerThread target = connectionThread.connectedClients.get(username);

            if (target != null) {
                target.submitTask(() -> target.sendOutgoingMessage(msg)); // sends message to their client;
                return true;
            } else {
                // todo - better handling for this case
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Implements the GUI popup message from the common.GUIResource interface
     * See docs there for additional info
     *
     * @param contents Message contents
     * @param title    Message title
     * @param type     Type of message, use the constants in {@link JOptionPane}
     */
    @Override
    public void showMessageDialog(String contents, String title, int type) {
        // sync this back to GUI thread
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, contents, title, type));
    }

    /**
     * Thread server listens on for incoming connections
     */
    private class ConnectionThread extends Thread {
        /**
         * Instance of the outer JFrame class
         * Needed to pass along its logger
         */
        private final MessageServer mainInstance;

        /**
         * Connected client list, key is logged in username, val is client thread for said user
         */
        private final Map<String, SharedWorkerThread> connectedClients = new HashMap<>();

        /**
         * Controls the overall "listening" state of the thread
         */
        private boolean listening = true;

        /**
         * Instance of the listening socket
         */
        private ServerSocket listenerSocket = null;

        /**
         * Class Entry
         *
         * @param outerClass instance of server.MessageServer class
         */
        public ConnectionThread(MessageServer outerClass) {
            this.mainInstance = outerClass;
        }

        /**
         * Stops the server thread
         */
        public void stopListening() {
            logln("Stopping server...");
            listening = false;

            // thread safety, submit disconnect actions to the threads to be executed by the threads
            connectedClients.values().forEach(c -> c.submitTask(c::notifyRemoteToDisconnect));
            connectedClients.clear();

            try {
                listenerSocket.close();
            } catch (Exception ex) {
                logln("Exception closing server listener socket!");
                ex.printStackTrace();
            }
        }

        @Override
        public void run() {
            logln("Starting server...");
            int port = ProtocolConstants.SERVER_DEFAULT_LISTEN_PORT;

            try {
                listenerSocket = new ServerSocket(port);
            } catch (IOException ex) {
                logln("Unable to bind to socket on " + port + ": " + ex);
                ex.printStackTrace();

                mainInstance.onStartClicked();
                return;
            }

            String hostInfo = listenerSocket.getInetAddress().getHostAddress() + ":" + listenerSocket.getLocalPort();
            logln("Server awaiting connections on " + hostInfo);

            while (listening) {
                Socket clientSocket = null;

                try {
                    clientSocket = listenerSocket.accept();
                } catch (IOException ex) {
                    logln("Connection blocked " + ex);
                    return;
                }

                Pair<String, NetworkManager> userConnection = authenticateUser(clientSocket);
                if (userConnection == null) {
                    continue; // auth failed
                }

                String username = userConnection.getKey();
                NetworkManager manager = userConnection.getVal();

                // spin up a client thread for the newly connected client
                SharedWorkerThread client = new SharedWorkerThread(mainInstance, manager);
                connectedClients.put(username, client);
                client.start();
            }
        }

        /**
         * Authenticates the user and returns their NetworkManager instance or null if they failed authentication
         *
         * @param socket connection socket
         * @return Null if auth failed, a NetworkManager instance if okay to proceed
         */
        private Pair<String, NetworkManager> authenticateUser(Socket socket) {
            logln("Client attempting to authenticate from: " + socket.getInetAddress().getHostName());

            NetworkManager netManager;
            try {
                netManager = new NetworkManager(mainInstance, socket);
            } catch (IOException e) {
                logln("Error initializing connection with remote");
                e.printStackTrace();
                return null;
            }

            Pair<String, String> userAndPass = netManager.readIncomingLoginInfo();
            final String username = userAndPass.getKey();
            final String password = userAndPass.getVal();

            String remoteHostname = netManager.getRemoteHostname();

            // authenticate only if we have security enabled, else just let everyone be whoever
            boolean securityOverride = !jcbSecurity.isSelected();
            if (securityOverride || authenticationManager.isValidLogin(username, password)) {
                netManager.notifyLoginSuccess();

                logln(remoteHostname + " authenticated as " + username);
                return new Pair<>(username, netManager);
            } else {
                netManager.notifyLoginFailed(); // also closes connections

                logln(remoteHostname + " failed to authenticate as " + username);
                return null;
            }
        }
    }

}
