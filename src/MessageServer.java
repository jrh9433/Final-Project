import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server init class
 */
public class MessageServer extends JFrame implements GUIResource {

    /**
     * Default password to use when creating users
     */
    private static final String DEFAULT_PASSWORD = "ISTE121";

    /**
     * Default data save path
     */
    private static final String SAVED_USER_DATA_PATH = "./saved-user-data.bin";

    /**
     * Starts the server
     */
    private final JButton jbStart = new JButton("Start");

    /**
     * Creates new users
     */
    private final JButton jbAddUser = new JButton("Add User");

    /**
     * Main text output area
     */
    private final JTextArea jtaLog = new JTextArea();

    /**
     * Manages users, passwords, and their data
     */
    private final UserManager userManager = new UserManager(this);

    /**
     * Output stream writer
     */
    private ObjectOutputStream oos = null;

    /**
     * Input stream reader
     */
    private ObjectInputStream ois = null;

    /**
     * Reference to server thread
     */
    private ServerThread serverThread = null;

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
                if (serverThread != null) {
                    serverThread.stopListening();
                }

                userManager.writeUserData(SAVED_USER_DATA_PATH);
            }
        });

        // start stop button
        jbStart.addActionListener(l -> onStartClicked());
        JPanel jpTopBar = new JPanel();
        jpTopBar.add(jbStart);
        jbAddUser.addActionListener(l -> onAddUserClicked());
        jpTopBar.add(jbAddUser);

        // output handling
        DefaultCaret caret = (DefaultCaret) jtaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        jtaLog.setLineWrap(true);
        jtaLog.setWrapStyleWord(true);
        jtaLog.setEditable(false);

        this.add(jpTopBar, BorderLayout.NORTH);
        this.add(new JScrollPane(jtaLog), BorderLayout.CENTER);

        // load saved data
        userManager.loadSavedUserData(SAVED_USER_DATA_PATH);
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

        if (serverThread == null) {
            // server not running, start it
            serverThread = new ServerThread(this);
            serverThread.start();

            jbStart.setText("Stop");
        } else {
            // server already running, stop it
            serverThread.stopListening();
            serverThread = null;

            jbStart.setText("Start");
        }

        jbStart.setEnabled(true);
    }

    /**
     * Called when the Add User button is clicked
     */
    private void onAddUserClicked() {
        new AddUserDialog(this);
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
    public void processLoginResponse(boolean wasSuccess) {
        // do nothing, server does not process login responses
    }

    /**
     * Updates the GUI when the remote disconnects
     */
    @Override
    public void updateForDisconnect() {
        // do nothing, the server does not have any GUI elements that need updating
        // if it did, or ever does, make sure to sync this to the GUI thread
        // SwingUtilities.invokeLater(() -> { // some code });
    }

    /**
     * Implements the GUI popup message from the GUIResource interface
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
    private class ServerThread extends Thread {
        /**
         * Instance of the outer JFrame class
         * Needed to pass along its logger
         */
        private final MessageServer mainInstance;

        /**
         * Connected client list
         */
        private final java.util.List<SharedWorkerThread> connectedClients = new ArrayList<>();

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
         * @param outerClass instance of MessageServer class
         */
        public ServerThread(MessageServer outerClass) {
            this.mainInstance = outerClass;
        }

        /**
         * Stops the server thread
         */
        public void stopListening() {
            logln("Stopping server...");
            listening = false;

            // thread safety, submit disconnect actions to the threads to be executed by the threads
            connectedClients.forEach(c -> c.submitTask(c::notifyRemoteToDisconnect));
            connectedClients.forEach(c -> c.submitTask(c::disconnect));
            connectedClients.clear();

            try {
                listenerSocket.close();
            } catch (IOException ex) {
                logln("IOException closing server listener socket!");
                ex.printStackTrace();
            }
        }

        @Override
        public void run() {
            logln("Starting server...");
            int port = SharedWorkerThread.SERVER_DEFAULT_LISTEN_PORT;

            try {
                listenerSocket = new ServerSocket(port);
            } catch (IOException ex) {
                logln("Unable to bind to socket on " + port + ": " + ex);
                ex.printStackTrace();
                return;
            }

            String hostInfo = listenerSocket.getInetAddress().getHostAddress() + ":" + listenerSocket.getLocalPort();
            logln("Server awaiting connections on " + hostInfo);

            while (listening) {
                Socket clientSocket = null;

                try {
                    clientSocket = listenerSocket.accept();
                    oos = new ObjectOutputStream(clientSocket.getOutputStream());
                    ois = new ObjectInputStream(clientSocket.getInputStream());
                } catch (IOException ex) {
                    logln("Connection blocked " + ex);
                    return;
                }

                String clientInfo = clientSocket.getInetAddress().getHostName();

                try {
                    // read username and password immediately
                    Object expectedUsername = ois.readObject();
                    Object expectedUserPass = ois.readObject();

                    if (!(expectedUsername instanceof String && expectedUserPass instanceof String)) {
                        logln("Expected login information; got \"" + String.valueOf(expectedUsername) + "\":\"" + String.valueOf(expectedUserPass) + "\"");
                        continue;
                    }

                    String userName = (String) expectedUsername;
                    String password = (String) expectedUserPass;

                    // check user information against user store
                    if (userManager.isValidLogin(userName, password)) {
                        oos.writeObject(ProtocolConstants.LOGIN_SUCCESS);
                        logln(clientInfo + " connected as " + userName);

                        // spin up a client thread for the newly connected client
                        SharedWorkerThread client = new SharedWorkerThread(clientSocket, mainInstance, ois, oos);
                        connectedClients.add(client);
                        client.start();
                    } else {
                        // notify remote login failed and clean up
                        oos.writeObject(ProtocolConstants.LOGIN_REJECTED);
                        logln(clientInfo + " failed to connect as " + userName + " - Invalid username/password combo");

                        ois.close();
                        oos.close();
                        clientSocket.close();
                    }
                } catch (EOFException ignored) {
                    continue;
                } catch (IOException | ClassNotFoundException e) {
                    logln("Exception " + e);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Dialog used to add users to the server user manager
     */
    class AddUserDialog extends JDialog {
        /**
         * Username for the new user
         */
        private final JTextField jtfUser = new JTextField(10);

        /**
         * Password for the new user
         */
        private final JTextField jtfPass = new JPasswordField(10);

        /**
         * Adds the user to the server
         */
        private final JButton jbAdd = new JButton("Add");

        /**
         * Cancels the operation
         */
        private final JButton jbCancel = new JButton("Cancel");

        public AddUserDialog(MessageServer parent) {
            super(parent, "Add User", true);
            this.setSize(250, 150);
            this.setLocation(getCenteredPosition(parent, this));

            JPanel body = new JPanel(new GridLayout(0, 1));
            JPanel username = new JPanel();
            username.add(new JLabel("Username: "));
            username.add(jtfUser);

            JPanel password = new JPanel();
            jtfPass.setText(DEFAULT_PASSWORD);
            password.add(new JLabel("Password: "));
            password.add(jtfPass);

            JPanel buttons = new JPanel();
            jbAdd.addActionListener(l -> onAdd());
            jbCancel.addActionListener(l -> onCancel());

            buttons.add(jbAdd);
            buttons.add(jbCancel);

            body.add(username);
            body.add(password);
            body.add(buttons);
            this.add(body, BorderLayout.CENTER);
            this.setVisible(true);
        }

        /**
         * Called when the add button is clicked
         */
        private void onAdd() {
            String username = jtfUser.getText();
            String pass = jtfPass.getText();

            if (username == null || username.equals("") || pass == null || pass.equals("")) {
                return;
            }

            userManager.addNewUser(username, pass);
            logln("Added user: " + username);
            this.dispose();
        }

        /**
         * Called when the cancel button is clicked
         */
        private void onCancel() {
            this.dispose();
        }
    }
}
