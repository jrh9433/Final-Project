package server;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog used to add users to the server user manager
 */
class AddUserDialog extends JDialog {

    /**
     * Default password to use when creating users
     */
    private static final String DEFAULT_PASSWORD = "ISTE121";

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

    /**
     * Instance of the main server
     */
    private MessageServer messageServer;

    public AddUserDialog(MessageServer messageServer, MessageServer parent) {
        super(parent, "Add User", true);
        this.messageServer = messageServer;
        this.setSize(250, 150);
        this.setLocation(messageServer.getCenteredPosition(parent, this));

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

        if (username == null || username.equals("")) { // only check usernames, blank passwords are fine
            return;
        }

        // ensure pass is never null
        if (pass == null) {
            pass = "";
        }

        messageServer.authenticationManager.addNewUser(username, pass);
        messageServer.logln("Added user: " + username);
        this.dispose();
    }

    /**
     * Called when the cancel button is clicked
     */
    private void onCancel() {
        this.dispose();
    }
}
