import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Worker class for processing, sending, and reading data.
 * <p>
 * Shared across both the client and server implementations. The server is expected
 * to spin up one instance per client, while the client is expected to spin up only
 * a single instance for itself.
 */
public class SharedWorkerThread extends Thread {

    /**
     * Port that the server listens on
     */
    public static final int SERVER_DEFAULT_LISTEN_PORT = 42069;

    /**
     * Whether this is running on the server or the client
     */
    private final boolean isServer;

    /**
     * Socket on the other end of the connection
     */
    private final Socket remoteSocket;

    /**
     * Pending tasks waiting to be run by this thread
     */
    private final Vector<Runnable> pendingTasks = new Vector<>();

    /**
     * Separate data-receive thread
     * <p>
     * Because ObjectIO blocks, a separate data-receive thread is employed so that
     * this thread is free to send data and process other tasks.
     */
    private DataReceiveThread receiveThread;

    /**
     * Incoming data stream
     */
    private ObjectInputStream streamIn;

    /**
     * Outgoing data stream
     */
    private ObjectOutputStream streamOut;

    /**
     * Loop control variable
     */
    private boolean isConnected = true;

    /**
     * Provides access to GUI resources as well as allowing the thread to send messages
     * to the implementing classes.
     */
    private GUIResource guiClient;

    /**
     * Creates a new SharedWorkerThread
     *
     * @param remoteSocket Socket connecting us to the remote
     * @param guiClient    Instance of a client
     */
    public SharedWorkerThread(Socket remoteSocket, GUIResource guiClient) {
        super("Shared Worker Thread : " + (guiClient.isServer() ? "Server" : "Client"));
        this.guiClient = guiClient;
        this.isServer = guiClient.isServer();
        this.remoteSocket = remoteSocket;
    }

    /**
     * Creates a new SharedWorkerThread
     *
     * @param remoteSocket Socket connecting us to the remote
     * @param guiClient    Instance of a client
     * @param inputStream  ObjectInputStream to communicate over
     * @param outputStream ObjectOutputStream to communicate over
     */
    public SharedWorkerThread(Socket remoteSocket, GUIResource guiClient, ObjectInputStream inputStream, ObjectOutputStream outputStream) {
        this(remoteSocket, guiClient);

        this.streamIn = inputStream;
        this.streamOut = outputStream;
    }

    /**
     * Returns true if this thread is running on the server
     *
     * @return True if server
     */
    private boolean isServer() {
        return this.isServer;
    }

    /**
     * Returns true if this thread is running on the client
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

    @Override
    public void run() {
        try {
            if (streamOut == null) {
                streamOut = new ObjectOutputStream(remoteSocket.getOutputStream());
            }

            if (streamIn == null) {
                streamIn = new ObjectInputStream(remoteSocket.getInputStream());
            }
        } catch (IOException ex) {
            logln("Error initializing streams: " + ex);
            return;
        }

        // Because object IO blocks, use a separate thread to receive objects.
        // That thread will then immediately turn around and submit those objects
        // back to this thread. This thread will handle processing them.
        receiveThread = new DataReceiveThread(this);
        receiveThread.start();

        while (isConnected) {
            // process pending tasks like sending out data, processing incoming data queued up from the
            // receive thread, etc
            processPendingTasks();

            // sleep this thread for a bit so we aren't
            // just pegging out the CPU and eating all its cycles
            try {
                Thread.sleep(50); // 0.05 seconds
            } catch (InterruptedException ignored) {
                // fall through to continue
            }
        }

        // clean up after ourselves
        try {
            streamIn.close();
            streamOut.close();
            remoteSocket.close();

            // as a side effect of closing the socket, readObject will throw an exception (which we catch),
            // resulting in the DataReceiveThread cleaning itself up as well.
            receiveThread = null;
        } catch (IOException ex) {
            if (!isServer) {
                // on client, show dialog
                guiClient.showMessageDialog("Error disconnecting: " + ex, "Connection Error", JOptionPane.ERROR_MESSAGE);
            }

            logln("Error disconnecting from remote at " + remoteSocket.getInetAddress());
            ex.printStackTrace();
        }
    }

    // todo - remove me
    public void sendDemoString(String string) {
        try {
            streamOut.writeObject(string);
            streamOut.flush();
        } catch (IOException ignored) {
        }
    }

    /**
     * Attempts to authenticate the user with the server.
     * <p>
     * When a response is received, the worker will update the client
     *
     * @param username username to authenticate with
     * @param password password to authenticate with
     */
    public void sendLoginInformation(String username, String password) {
        try {
            streamOut.writeObject(username);
            streamOut.writeObject(password);
        } catch (IOException ex) {
            logln("Unable to send username and password: " + ex);
            guiClient.showMessageDialog("Error sending login information: " + ex, "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Accepts an incoming object, determines what it is, and processes it as needed.
     *
     * @param incomingObj Object to process
     */
    private void processIncomingData(Object incomingObj) {
        if (incomingObj instanceof String) {
            String incoming = (String) incomingObj;

            if (incoming.equals(ProtocolConstants.LOGIN_SUCCESS)) {
                guiClient.processLoginResponse(true);
            } else if (incoming.equals(ProtocolConstants.LOGIN_REJECTED)) {
                guiClient.processLoginResponse(false);
            } else {
                logln("Recieved unknown message: " + incoming);
            }

            return;
        }

        logln("Received unknown object: " + String.valueOf(incomingObj));
    }

    /**
     * Notifies the remote to disconnect gracefully
     */
    public void notifyRemoteToDisconnect() {
        // todo - more design-by-committee to figure out; notify remote to disconnect BEFORE terminating thread
    }

    /**
     * Submits a task to this thread
     * <p>
     * The submitted task will be run by the client thread during the next iteration
     *
     * @param runnable task to be run
     */
    public synchronized void submitTask(Runnable runnable) {
        pendingTasks.add(runnable);
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
    public void disconnect() {
        this.isConnected = false;
        guiClient.updateForDisconnect();
    }

    /**
     * Separate thread to handle receiving data without blocking the main worker thread
     */
    private class DataReceiveThread extends Thread {
        /**
         * Input stream to read from
         */
        private final ObjectInputStream streamIn;

        /**
         * Loop control variable
         */
        private boolean acceptIncoming = true;

        /**
         * Instance of the main SharedWorkerThread that owns this receive thread
         */
        private SharedWorkerThread parentWorker;

        /**
         * Constructs a new DataReceiveThread
         *
         * @param parentWorkerThread instance of SharedWorkerThread responsible for this thread
         */
        private DataReceiveThread(SharedWorkerThread parentWorkerThread) {
            super("Data Receive Thread");
            this.streamIn = parentWorkerThread.streamIn;
            this.parentWorker = parentWorkerThread;
        }

        @Override
        public void run() {
            while (acceptIncoming) {
                Object incomingObj;
                try {
                    incomingObj = streamIn.readObject(); // blocking
                } catch (EOFException | SocketException ignored) {
                    return; // expected on terminate
                } catch (IOException e) {
                    logln("Exception reading incoming data! " + e);
                    e.printStackTrace();
                    break;
                } catch (ClassNotFoundException e) {
                    logln("Received unknown data! " + e);
                    e.printStackTrace();
                    continue; // don't break the loop for one piece of bad data
                }

                // make sure we are still accepting data
                if (!acceptIncoming) {
                    break;
                }

                // submit task to main worker, freeing this up for more incoming data
                if (incomingObj != null) {
                    submitTask(() -> parentWorker.processIncomingData(incomingObj));
                }
            }
        }

        /**
         * Instructs the thread to stop accepting incoming data and stop the loop
         * <p>
         * NOTE: because {@link ObjectInputStream#readObject()} is blocking, this will not stop the thread immediately.
         * It is acceptable to skip this call and just close the main socket directly.
         */
        public void stopAcceptingData() {
            acceptIncoming = false;
        }
    }
}
