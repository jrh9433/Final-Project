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
import java.text.SimpleDateFormat;  
import java.util.Date;  

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

    /**
     * Subject Components
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
    private JTextArea jtaMessage = new JTextArea(10, 35);
    private JButton jbSend = new JButton("Send");
    private JButton jbLogout = new JButton("Logout");
    protected final JTextField jtfAttemptStatus = new JTextField("Please fill with valid entries");
    private JCheckBox jcbEncrypt = new JCheckBox("Encrypt", false);
    
    private boolean doEncrypt = false;


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
        final Dimension defaultSize = new Dimension(550, 475);
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
        JTabbedPane tabbedPane = new JTabbedPane();
        
        //tabbedPane.addTab("Inbox");
        /**
         * Compose Tab
         */
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

        JPanel jpNorth = new JPanel(new GridLayout(5, 1));
        jpNorth.add(jpFrom);
        jpNorth.add(jpTo);
        jpNorth.add(jpCc);
        jpNorth.add(jpSubject);
        jpNorth.add(jpMessage);

        // CENTER ... Label + text area
        JPanel jpCenter = new JPanel(new GridLayout(2, 1));
        jtaMessage.setLineWrap(true);
        jtaMessage.setWrapStyleWord(true);
        jpCenter.add(new JScrollPane(jtaMessage));
        jpCenter.add(jpSend);

        JPanel jpCompose = new JPanel();
        jpCompose.add(jpNorth);
        jpCompose.add(jpCenter);
        
        tabbedPane.addTab("Compose", jpCompose);
        // todo - list of messages panel, reading panel
        
        jtfAttemptStatus.setVisible(false); // hide until we have something to say
        jtfAttemptStatus.setEditable(false);
        
        this.add(tabbedPane);
        
        jbLogout.setEnabled(false);
        jbSend.setEnabled(false);  
        jbSend.addActionListener(l ->ValidateAndSend());
        jbLogout.addActionListener(l ->logout());
        
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

    
    private void ValidateAndSend() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy");  
        Date date = new Date();
        String currentDate = sdfDate.format(date);  
        
        String[] toField = jtfTo.getText().split(",");
        String fromField = jtfFrom.getText();
        String[] ccField = jtfCc.getText().split(",");
        String subjectField = jtfSubject.getText();
        String messageField = jtaMessage.getText();
        
        jtfAttemptStatus.setVisible(false);
          
        if(fromField.isEmpty() || subjectField.isEmpty() || toField[0].isEmpty() || messageField.isEmpty()) {
            jtfAttemptStatus.setVisible(true);
            return;
        }
        
        if(jcbEncrypt.isSelected()) {
            doEncrypt = true;
        }
        else {
            doEncrypt = false;
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
        jbSend.setEnabled(true);
        jbLogout.setEnabled(true);
    }

    /**
     * Sets the enabled state of various GUI actions
     *
     * @param enable Sets enabled state
     */
    private void setActionsEnabled(boolean enable) {
        //demoButton.setEnabled(enable);
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
        jbSend.setEnabled(false);
        jbLogout.setEnabled(false);
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
