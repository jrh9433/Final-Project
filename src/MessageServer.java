import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server init class
 */
public class MessageServer extends JFrame implements GUIResource {

    /**
     * Starts the server
     */
    private final JButton jbStart = new JButton("Start");

    /**
     * Main text output area
     */
    private final JTextArea jtaLog = new JTextArea();

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
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(true);

        // start stop button
        jbStart.addActionListener(l -> onStartClicked());
        JPanel jpTopBar = new JPanel();
        jpTopBar.add(jbStart);

        // output handling
        DefaultCaret caret = (DefaultCaret) jtaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        jtaLog.setLineWrap(true);
        jtaLog.setWrapStyleWord(true);
        jtaLog.setEditable(false);

        this.add(jpTopBar, BorderLayout.NORTH);
        this.add(new JScrollPane(jtaLog), BorderLayout.CENTER);
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
                } catch (IOException ex) {
                    logln("Connection blocked " + ex);
                    return;
                }

                // spin up a client thread for the newly connected client
                SharedWorkerThread client = new SharedWorkerThread(clientSocket, mainInstance);
                connectedClients.add(client);
                client.start();
            }
        }
    }
}
