import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Client init class
 */
public class MessageClient extends JFrame implements GUIResource {

    /**
     * Thread to use for networking, don't block the GUI thread when possible
     */
    private SharedWorkerThread workerThread;

    /**
     * Username of the currently logged in user, or null
     */
    private String loggedInUser;

    // todo - remove, for basic network testing only
    private JButton demoButton = new JButton("Action");

    /**
     * JVM entry
     *
     * @param args commandline args
     */
    public static void main(String[] args) {
        new MessageClient();
    }

    /**
     * Class entry
     */
    public MessageClient() {
        this.setTitle("Mail Client");
        final Dimension defaultSize = new Dimension(550, 350);
        this.setSize(defaultSize);

        // todo - list of messages panel, reading panel

        // todo - remove below 2 lines
        demoButton.addActionListener(l -> demoOnClick());
        this.add(demoButton);

        this.setVisible(true);
        updateForDisconnect(); // inits everything as we want it
    }

    // todo - remove me, more demo garbage
    private void demoOnClick() {
        workerThread.submitTask(() -> workerThread.sendDemoString(loggedInUser));
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

        new LoginPanel(this);
    }

    @Override
    public boolean isServer() {
        return false;
    }

    /**
     * Login prompt panel
     * <p>
     * Used to connect to servers and authenticate with them.
     * NOTE: This panel is one-use only. If needed again, create a new instance.
     */
    class LoginPanel extends JFrame {

        /**
         * Host to connect to
         */
        private final JTextField jtfServerIp = new JTextField(10);

        /**
         * Username to authenticate as
         */
        private final JTextField jtfLoginUsername = new JTextField(10);

        /**
         * Password to authenticate with
         */
        private final JTextField jtfLoginPassword = new JPasswordField(10);

        /**
         * Fields requiring some form of validation
         */
        private final JTextField[] fieldsToValidate = {jtfServerIp, jtfLoginUsername, jtfLoginPassword};

        /**
         * Begins the login process when clicked
         */
        private final JButton jbLogin = new JButton("Login");

        /**
         * Hidden and shown as appropriate to inform the user of the status of their connection status
         */
        private final JTextField jtfAttemptStatus = new JTextField("UNINITIALIZED");

        /**
         * Instance of the main MessageClient window
         */
        private final MessageClient mainClientInstance;

        public LoginPanel(MessageClient mainClientInstance) {
            this.setTitle("Client Login");
            this.setSize(250, 175);
            this.setResizable(false);
            this.setLocation(getCenteredPosition(mainClientInstance));
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            this.setResizable(true);

            JPanel body = new JPanel(new GridLayout(0, 1));
            JPanel host = new JPanel();
            host.add(new JLabel("server: "));
            host.add(jtfServerIp);

            JPanel username = new JPanel();
            username.add(new JLabel("username: "));
            username.add(jtfLoginUsername);

            JPanel password = new JPanel();
            password.add(new JLabel("password: "));
            password.add(jtfLoginPassword);

            jbLogin.addActionListener(l -> onLoginClicked());

            jtfAttemptStatus.setVisible(false); // hide until we have something to say
            jtfAttemptStatus.setEditable(false);
            jtfAttemptStatus.setHorizontalAlignment(JTextField.CENTER);

            body.add(host);
            body.add(username);
            body.add(password);
            body.add(jtfAttemptStatus);
            body.add(jbLogin);
            this.add(body, BorderLayout.CENTER);

            this.setVisible(true);

            this.mainClientInstance = mainClientInstance;
        }

        /**
         * Called to begin the connection and login process
         */
        private void onLoginClicked() {
            // basic field sanity check
            for (JTextField field : fieldsToValidate) {
                String contents = field.getText();
                if (contents == null || contents.equals("")) {
                    return;
                }
            }

            jtfAttemptStatus.setVisible(false); // hide pending the status of this attempt

            final String host = jtfServerIp.getText();
            final int port = SharedWorkerThread.SERVER_DEFAULT_LISTEN_PORT;

            Socket connection;
            try {
                connection = new Socket(host, port);
                workerThread = new SharedWorkerThread(connection, mainClientInstance);
                workerThread.start();
            } catch (IOException ex) {
                String error = "Unable to connect to " + host + ":" + port;
                System.out.println(error);
                ex.printStackTrace();

                JOptionPane.showMessageDialog(this, error + ": " + ex, "Connection Error!", JOptionPane.ERROR_MESSAGE);
                return; // do not proceed any further
            }

            jbLogin.setEnabled(false);
            jtfAttemptStatus.setText("Authenticating...");
            jtfAttemptStatus.setVisible(true);

            String username = jtfLoginUsername.getText();
            String password = jtfLoginPassword.getText();

            workerThread.submitTask(() -> workerThread.authenticateAndCallBack(username, password, this));
        }

        /**
         * Process a login reply from the SharedWorkerThread.
         * <p>
         * Thread safe, can be called directly from the worker.
         *
         * @param wasSuccess True, if login was successful
         */
        public void processLoginReply(boolean wasSuccess) {
            SwingUtilities.invokeLater(() -> { // sync back to GUI thread
                if (wasSuccess) {
                    this.setVisible(false);
                    onSuccessfulLogin(jtfLoginUsername.getText());
                    this.dispose(); // dispose of login frame resources
                } else {
                    jtfAttemptStatus.setText("Incorrect username/password");
                }

                jbLogin.setEnabled(true);
            });
        }

        /**
         * Gets the location that will position this login window at the
         * center of the parent window. This is not the same thing
         * as the actual center of the parent window.
         *
         * @param parent Parent window to center on
         * @return Point that will result in this window being centered
         */
        private Point getCenteredPosition(JFrame parent) {
            double height = parent.getSize().getHeight();
            double width = parent.getSize().getWidth();
            double xStart = parent.getLocation().getX();
            double yStart = parent.getLocation().getY();

            // account for entirety of parent window size
            int targetX = (int) (xStart + width) / 2;
            int targetY = (int) (yStart + height) / 2;

            // account for this login window's size
            targetX -= this.getSize().width / 2;
            targetY -= this.getSize().height / 2;

            return new Point(targetX, targetY);
        }
    }

}
