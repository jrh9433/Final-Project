package client;

import common.networking.ProtocolConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Login prompt panel
 * <p>
 * Used to connect to servers and authenticate with them.
 * NOTE: This panel is one-use only. If needed again, create a new instance.
 */
class LoginDialog extends JDialog {

    /**
     * Username to authenticate as
     */
    protected final JTextField jtfLoginUsername = new JTextField(10);

    /**
     * Begins the login process when clicked
     */
    protected final JButton jbLogin = new JButton("Login");

    /**
     * Hidden and shown as appropriate to inform the user of the status of their connection status
     */
    protected final JTextField jtfAttemptStatus = new JTextField("UNINITIALIZED");

    /**
     * Host to connect to
     */
    private final JTextField jtfServerIp = new JTextField(10);

    /**
     * Password to authenticate with
     */
    private final JTextField jtfLoginPassword = new JPasswordField(10);

    /**
     * Fields requiring some form of validation
     */
    private final JTextField[] fieldsToValidate = {jtfServerIp, jtfLoginUsername, jtfLoginPassword};

    /**
     * Instance of the main MessageClient
     */
    private MessageClient messageClient;

    public LoginDialog(MessageClient messageClient) {
        this.messageClient = messageClient;
        this.setTitle("Connect");
        this.setSize(250, 175);
        this.setLocation(messageClient.getCenteredPosition(messageClient, this));
        this.setModal(true);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

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
    }

    /**
     * Called to begin the connection and login process
     */
    private void onLoginClicked() {
        jtfAttemptStatus.setVisible(false); // hide pending the status of this attempt

        final String host = jtfServerIp.getText();
        final int port = ProtocolConstants.SERVER_DEFAULT_LISTEN_PORT;

        if (host == null || host.equals("")) {
            messageClient.showMessageDialog("No host specified!", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String username = jtfLoginUsername.getText();
        String password = jtfLoginPassword.getText();

        if (username == null || username.equals("")) {
            messageClient.showMessageDialog("Must specify a username!", "Input Error", JOptionPane.ERROR_MESSAGE);
        }

        jbLogin.setEnabled(false);
        jtfAttemptStatus.setText("Authenticating...");
        jtfAttemptStatus.setVisible(true);

        messageClient.attemptToConnect(host, port, username, password, this);
    }
}
