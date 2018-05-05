package client;

import common.GUIResource;
import common.SharedWorkerThread;
import common.message.MailMessage;
import common.networking.NetworkManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Client init class
 */
public class MessageClient extends JFrame implements GUIResource {

    protected final JTextField jtfAttemptStatus = new JTextField("Please fill with valid entries");

    /**
     * Thread to use for common.networking, don't block the GUI thread when possible
     */
    protected SharedWorkerThread workerThread;

    /**
     * Inbox JComboBox attributes
     */
    Vector<MailMessage> mailList = new Vector<MailMessage>();
    List<String> mail = new ArrayList<String>();
    String[] mailString = mail.toArray(new String[mail.size()]);
    JComboBox<String> mailBox = new JComboBox<>(mailString);

    /**
     * Username of the currently logged in user, or null
     */
    private String loggedInUser;

    /**
     * Compose Components
     */

    // North
    private JLabel jlFrom = new JLabel("From:                ");
    private JTextField jtfFrom = new JTextField(20);
    private JLabel jlTo = new JLabel("To:                    ");
    private JTextField jtfTo = new JTextField(20);
    private JLabel jlCc = new JLabel("Cc:                    ");
    private JTextField jtfCc = new JTextField(20);
    private JLabel jlSubject = new JLabel("Subject:             ");

    private JTextField jtfSubject = new JTextField(20);
    // Center
    private JLabel jlMessage = new JLabel("Message:");
    private JTextArea jtaMessage = new JTextArea(11, 35);
    private JButton jbSend = new JButton("Send");
    private JButton jbLogout = new JButton("Logout");
    private JCheckBox jcbEncrypt = new JCheckBox("Encrypt", false);
    private boolean doEncrypt = false;

    /**
     * Inbox Components
     */

    // Main
    private JLabel jlFromInbox = new JLabel("From:    ");
    private JTextField jtfFromInbox = new JTextField(20);
    private JLabel jlToInbox = new JLabel("To:        ");
    private JTextField jtfToInbox = new JTextField(20);
    private JLabel jlCcInbox = new JLabel("Cc:        ");
    private JTextField jtfCcInbox = new JTextField(20);
    private JLabel jlSubjectInbox = new JLabel("Subject: ");
    private JTextField jtfSubjectInbox = new JTextField(20);
    private JLabel jlMessageInbox = new JLabel("Message: ");
    private JTextArea jtaMessageInbox = new JTextArea(8, 35);
    private JButton jbOpen = new JButton("Open");
    private JButton jbLogoutInbox = new JButton("Logout");

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
        final Dimension defaultSize = new Dimension(525, 490);
        this.setSize(defaultSize);
        this.setResizable(false);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (workerThread != null) {
                    workerThread.submitTask(workerThread::notifyRemoteToDisconnect);
                }
            }
        });
        JTabbedPane tabbedPane = new JTabbedPane();

        // Inbox tab
        JPanel jpMailList = new JPanel();
        jpMailList.add(mailBox);

        JPanel jpButtonsInbox = new JPanel();
        jpButtonsInbox.add(jbOpen);
        jpButtonsInbox.add(jbLogoutInbox);

        JPanel jpFromInbox = new JPanel();
        jpFromInbox.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpFromInbox.add(jlFromInbox);
        jpFromInbox.add(jtfFromInbox);

        JPanel jpToInbox = new JPanel();
        jpToInbox.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpToInbox.add(jlToInbox);
        jpToInbox.add(jtfToInbox);

        JPanel jpCcInbox = new JPanel();
        jpCcInbox.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpCcInbox.add(jlCcInbox);
        jpCcInbox.add(jtfCcInbox);

        JPanel jpSubjectInbox = new JPanel();
        jpSubjectInbox.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpSubjectInbox.add(jlSubjectInbox);
        jpSubjectInbox.add(jtfSubjectInbox);

        JPanel jpMessageInbox = new JPanel();
        jpMessageInbox.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpMessageInbox.add(jlMessageInbox);

        // NORTH ... Labels + text fields
        JPanel jpNorthInbox = new JPanel(new GridLayout(5, 1));
        jpNorthInbox.add(jpFromInbox);
        jpNorthInbox.add(jpToInbox);
        jpNorthInbox.add(jpCcInbox);
        jpNorthInbox.add(jpSubjectInbox);
        jpNorthInbox.add(jpMessageInbox);

        // CENTER ... Text area
        JPanel jpCenterInbox = new JPanel(new GridLayout(1, 1));
        jtaMessageInbox.setLineWrap(true);
        jtaMessageInbox.setWrapStyleWord(true);
        jpCenterInbox.add(new JScrollPane(jtaMessageInbox));

        // MAIN ... ComboBox + Buttons + North + Center
        JPanel jpInbox = new JPanel();
        jpInbox.add(jpMailList);
        jpInbox.add(jpButtonsInbox);
        jpInbox.add(jpNorthInbox);
        jpInbox.add(jpCenterInbox);

        jtfFromInbox.setEditable(false);
        jtfToInbox.setEditable(false);
        jtfCcInbox.setEditable(false);
        jtfSubjectInbox.setEditable(false);
        jtaMessageInbox.setEditable(false);

        tabbedPane.addTab("Inbox", jpInbox);

        // Compose tab
        JPanel jpFrom = new JPanel();
        jpFrom.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpFrom.add(jlFrom);
        jpFrom.add(jtfFrom);

        JPanel jpTo = new JPanel();
        jpTo.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpTo.add(jlTo);
        jpTo.add(jtfTo);

        JPanel jpCc = new JPanel();
        jpCc.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpCc.add(jlCc);
        jpCc.add(jtfCc);

        JPanel jpSubject = new JPanel();
        jpSubject.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpSubject.add(jlSubject);
        jpSubject.add(jtfSubject);

        JPanel jpMessage = new JPanel();
        jpMessage.setLayout(new FlowLayout(FlowLayout.LEFT));
        jpMessage.add(jlMessage);

        JPanel jpSend = new JPanel();
        jpSend.setLayout(new FlowLayout(FlowLayout.RIGHT));
        jpSend.add(jtfAttemptStatus);
        jpSend.add(jcbEncrypt);
        jpSend.add(jbSend);
        jpSend.add(jbLogout);
        // NORTH ... Labels + text fields
        JPanel jpNorth = new JPanel(new GridLayout(5, 1));
        jpNorth.add(jpFrom);
        jpNorth.add(jpTo);
        jpNorth.add(jpCc);
        jpNorth.add(jpSubject);
        jpNorth.add(jpMessage);

        // CENTER ... text area + jpSend components
        JPanel jpCenter = new JPanel(new GridLayout(2, 1));
        jtaMessage.setLineWrap(true);
        jtaMessage.setWrapStyleWord(true);
        jpCenter.add(new JScrollPane(jtaMessage));
        jpCenter.add(jpSend);

        JPanel jpCompose = new JPanel();
        jpCompose.add(jpNorth);
        jpCompose.add(jpCenter);

        tabbedPane.addTab("Compose", jpCompose);

        jtfAttemptStatus.setVisible(false); // hide until we have something to say
        jtfAttemptStatus.setEditable(false);

        this.add(tabbedPane);

        setActionsEnabled(false);

        jbOpen.addActionListener(l -> openMail());
        jbSend.addActionListener(l -> validateAndSend());
        jbLogout.addActionListener(l -> logout());
        jbLogoutInbox.addActionListener(l -> logout());

        this.add(tabbedPane);
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

    /**
     * Called on compose send. Ensures fields aren't empty then sends new MailMessage object
     */
    private void validateAndSend() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy");
        Date date = new Date();
        String currentDate = sdfDate.format(date);

        String[] toField = jtfTo.getText().split(",");
        String fromField = jtfFrom.getText();
        String[] ccField = jtfCc.getText().split(",");
        String subjectField = jtfSubject.getText();
        String messageField = jtaMessage.getText();

        jtfAttemptStatus.setVisible(false);

        if (fromField.isEmpty() || subjectField.isEmpty() || toField[0].isEmpty() || messageField.isEmpty()) {
            showMessageDialog("Please fill out all necessary fields!", "Warning", 2);
            return;
        }

        if (jcbEncrypt.isSelected()) {
            doEncrypt = true;
        } else {
            doEncrypt = false;
        }

        boolean toValidates = validateRecipients(toField);
        boolean ccValidates = validateRecipients(ccField);

        // validate that fields contain an "@" for relaying purposes
        // if either field has a problem with it
        if (!toValidates || !ccValidates) {
            String warn = "The following fields don't validate properly: ";

            // if to doesn't validate specifically put it in the warning
            if (!toValidates) {
                warn += "to recipients; ";
            }

            // if cc doesn't validate specifically put in the warning
            if (!ccValidates) {
                warn += "cc recipients;";
            }

            showMessageDialog(warn, "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MailMessage message = new MailMessage(
                doEncrypt,
                toField,
                fromField,
                ccField,
                currentDate,
                subjectField,
                messageField
        );

        doClearCompose();
        workerThread.submitTask(() -> workerThread.sendOutgoingMessage(message));
        showMessageDialog("Your mail has been sent to the server!", "Message Sent", 1);
    }

    /**
     * Ensures that message recipients have an @ so that servers can relay their messages appropriately
     *
     * @param recipients array of recipients
     * @return True if validated successfully
     */
    private boolean validateRecipients(String[] recipients) {
        boolean validates = true;
        for (String str : recipients) {
            if (!str.isEmpty() && !str.contains("@")) {
                validates = false;
                logln("Error validating address: " + str);
            }
        }

        return validates;
    }

    /**
     * Called when a message is selected. Displays email contents
     */
    private void openMail() {
        String select = (String) mailBox.getSelectedItem();
        if (select.equals(String.format("%-75s", "Received Messages"))) {
            showMessageDialog("Please select a mail message", "Invalid Selection", 2);
            return;
        } else {
            try {
                int mailPos = mailBox.getSelectedIndex();

                MailMessage mailCur = mailList.get(mailPos - 1);

                jtfFromInbox.setText(mailCur.getSender());
                jtfToInbox.setText(Arrays.toString(mailCur.getTo()));
                jtfCcInbox.setText(Arrays.toString(mailCur.getCC()));
                jtfSubjectInbox.setText(mailCur.getSubject());
                jtaMessageInbox.setText(mailCur.getMessage());
            } catch (Exception e) {
                showMessageDialog("Unexpected reading error", "Error", 2);
                return;
            }
        }
    }

    /**
     * Called on logout. Clears inbox fields and mailbox vector
     */
    private void doClearInbox() {
        mailList.clear();
        mail.clear();
        mailBox.removeAllItems();
        jtfFromInbox.setText("");
        jtfToInbox.setText("");
        jtfCcInbox.setText("");
        jtfSubjectInbox.setText("");
        jtaMessageInbox.setText("");
    }

    /**
     * Called on logout. Clears compose fields
     */
    private void doClearCompose() {
        jtfFrom.setText("");
        jtfTo.setText("");
        jtfCc.setText("");
        jtfSubject.setText("");
        jtaMessage.setText("");
        jcbEncrypt.setSelected(false);
    }

    /**
     * Called when the client chooses to log out from the server
     * <p>
     * Messages are cleared, network threads are disconnected,
     * and a new login window is displayed
     */
    private void logout() {
        workerThread.submitTask(() -> workerThread.notifyRemoteToDisconnect());

        doClearCompose();
        doClearInbox();
    }

    /**
     * Called when a login attempt has been validated as successful.
     *
     * @param userName user that's been validated
     */
    private void onSuccessfulLogin(String userName) {
        this.loggedInUser = userName;
        this.setActionsEnabled(true);
        mailBox.addItem(String.format("%-75s", "Received Messages"));
    }

    /**
     * Sets the enabled state of various GUI actions
     *
     * @param enable Sets enabled state
     */
    private void setActionsEnabled(boolean enable) {
        jbSend.setEnabled(enable);
        jbLogout.setEnabled(enable);
        jbOpen.setEnabled(enable);
        jbLogoutInbox.setEnabled(enable);
    }

    @Override
    public void showMessageDialog(String message, String title, int type) {
        JOptionPane.showMessageDialog(this, message, title, type);
    }

    @Override
    public void updateForDisconnect() {
        setActionsEnabled(false);
        this.loggedInUser = null;
        doClearInbox();

        loginDialog = new LoginDialog(this);
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

            NetworkManager netManager = new NetworkManager(this, connection);
            boolean authSuccess = netManager.attemptLogin(username, password);

            if (authSuccess) {
                processLoginResponse(true, netManager);
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

    /**
     * Used to update the client's state following a login attempt
     *
     * @param wasSuccess whether the login attempt was successful or rejected
     * @param manager    Null if rejected, instance of {@link NetworkManager} if accepted
     */
    public void processLoginResponse(boolean wasSuccess, NetworkManager manager) {
        SwingUtilities.invokeLater(() -> { // sync back to GUI thread
            if (wasSuccess) {
                onSuccessfulLogin(loginDialog.jtfLoginUsername.getText());
                loginDialog.dispose();
                loginDialog = null;

                if (workerThread != null) {
                    workerThread.haltThread();
                }

                workerThread = new SharedWorkerThread(this, manager, false); // false - act as a client
                workerThread.start();
            } else {
                loginDialog.jtfAttemptStatus.setText("Incorrect username/password");
                loginDialog.jbLogin.setEnabled(true);
            }
        });
    }

    /**
     * Called when a message is received. Adds mail message to inbox
     *
     * @param mailMessage object sent to client from server
     */
    @Override
    public void onMailReceived(MailMessage mailMessage) {
        String from = mailMessage.getSender();
        String subject = String.format("%.25s", mailMessage.getSubject()); //limits subject in title to 25 characters
        String date = mailMessage.getDate();
        String format = from + " " + subject + " " + date; //format of JComboBox item title

        mailList.add(mailMessage);
        mail.add(format);
        mailBox.addItem(format);
    }
}
