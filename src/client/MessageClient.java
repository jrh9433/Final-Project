package client;

import common.GUIResource;
import common.SharedWorkerThread;
import common.message.MailMessage;
import common.networking.AuthdNetworkManager;
import common.networking.NetworkManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Socket;

/**
 * Client init class
 */
public class MessageClient extends JFrame implements GUIResource {

    /**
     * Thread to use for common.networking, don't block the GUI thread when possible
     */
    protected SharedWorkerThread workerThread;

    /**
     * Username of the currently logged in user, or null
     */
    private String loggedInUser;

    // todo - remove, for basic network testing only
    private JButton demoButton = new JButton("Action");

    /**
     * Login dialog that pops-up and gets disposed and re-initialized
     */
    private LoginDialog loginDialog;
    private int counter;

    /**
     * Class entry
     */
    public MessageClient() {
        this.setTitle("Mail Client");
        final Dimension defaultSize = new Dimension(550, 350);
        this.setSize(defaultSize);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (workerThread != null) {
                    workerThread.submitTask(workerThread::notifyRemoteToDisconnect);
                }
            }
        });

        // todo - list of messages panel, reading panel

        // todo - remove below 2 lines
        demoButton.addActionListener(l -> demoOnClick());
        this.add(demoButton);

        this.setVisible(true);
        updateForDisconnect(); // inits everything as we want it
    }

    /**
     * JVM entry
     *
     * @param args commandline args
     */
    public static void main(String[] args) {
        new MessageClient();
    }

    // todo - remove me, more demo garbage
    private void demoOnClick() {
        MailMessage message = new MailMessage(
                false,
                new String[]{"to1@localhost", "to2@localhost"},
                "from1@localhost",
                new String[]{"cc1@localhost", "cc2@localhost"},
                "2018-04-20",
                "Hi there! " + counter++,
                "Wow what an interesting day\nHow are you doing\nblah blahblah\nOkay thanks\n\nZach"
        );

        workerThread.submitTask(() -> workerThread.sendOutgoingMessage(message));
    }

    /**
     * Called when the client chooses to log out from the server
     * <p>
     * Messages are cleared, network threads are disconnected,
     * and a new login window is displayed
     */
    private void logout() {
        workerThread.submitTask(() -> workerThread.notifyRemoteToDisconnect());
        workerThread.submitTask(() -> workerThread.disconnect());
        workerThread = null;

        updateForDisconnect();
    }

    /**
     * Called when a login attempt has been validated as successful.
     *
     * @param userName user that's been validated
     */
    private void onSuccessfulLogin(String userName) {
        this.loggedInUser = userName;
        this.setActionsEnabled(true);
    }

    /**
     * Sets the enabled state of various GUI actions
     *
     * @param enable Sets enabled state
     */
    private void setActionsEnabled(boolean enable) {
        demoButton.setEnabled(enable);
    }

    @Override
    public void showMessageDialog(String message, String title, int type) {
        JOptionPane.showMessageDialog(this, message, title, type);
    }

    @Override
    public void updateForDisconnect() {
        setActionsEnabled(false);
        this.loggedInUser = null;
        this.workerThread = null;
        //clearAllMessages(); // todo - when reading and list panels are setup, clear them

        loginDialog = new LoginDialog(this);
    }

    @Override
    public boolean isServer() {
        return false;
    }

    /**
     * Attempts to connect this client to the specified remote using the specified credentials
     * <p>
     * If a null username or empty-string username is given, the client will attempt to connect
     * without authenticating.
     *
     * @param host     remote to connect to
     * @param port     port to connect to it on
     * @param username user to identify as
     * @param password password used to authenticate as user
     */
    public void attemptToConnect(String host, int port, String username, String password) {
        Socket connection;
        try {
            logln("Attempting to connect to " + host + ":" + port);
            connection = new Socket(host, port);

            // no user? assume no auth
            if (username == null || username.equals("")) {
                NetworkManager netManager = new NetworkManager(this, connection);

                processLoginResponse(true, netManager);
                return;
            }

            // User specified? assume auth
            AuthdNetworkManager authdManager = new AuthdNetworkManager(this, connection);
            boolean authSuccess = authdManager.attemptLogin(username, password);

            if (authSuccess) {
                processLoginResponse(true, authdManager);
            } else {
                processLoginResponse(false, null); // on false no network params are used
            }
        } catch (IOException ex) {
            String error = "Unable to connect to " + host + ":" + port;
            System.out.println(error);
            ex.printStackTrace();

            JOptionPane.showMessageDialog(this, error + ": " + ex, "Connection Error!", JOptionPane.ERROR_MESSAGE);
            processLoginResponse(false, null);
        }
    }

    public void processLoginResponse(boolean wasSuccess, NetworkManager manager) {
        SwingUtilities.invokeLater(() -> { // sync back to GUI thread
            if (wasSuccess) {
                onSuccessfulLogin(loginDialog.jtfLoginUsername.getText());
                loginDialog.dispose();
                loginDialog = null;

                workerThread = new SharedWorkerThread(this, manager);
                workerThread.start();
            } else {
                loginDialog.jtfAttemptStatus.setText("Incorrect username/password");
                loginDialog.jbLogin.setEnabled(true);
            }
        });
    }

    @Override
    public void onMailReceived(MailMessage mail) {
        // called when a message is received
    }
}
