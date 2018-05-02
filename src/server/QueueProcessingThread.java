package server;

import common.Pair;
import common.SharedWorkerThread;
import common.message.MailMessage;
import common.message.SMTPMailMessage;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Message queue management and processing thread
 */
public class QueueProcessingThread extends Thread {
    /**
     * Instance of the main server
     */
    private final MessageServer server;

    /**
     * Messages queued up to be sent internally
     * <p>
     * Within the pair, the string is the local username without any addressing information. Just the username
     */
    private final Queue<Pair<String, MailMessage>> incomingQueue = new ConcurrentLinkedQueue<>();

    /**
     * Messages queued up to be relayed to other servers
     */
    private final Queue<SMTPMailMessage> outgoingQueue = new ConcurrentLinkedQueue<>();

    /**
     * A list of SharedWorkerThreads being used to relay messages to other servers
     */
    private final List<SharedWorkerThread> relayWorkers = new ArrayList<>();

    /**
     * Vector of pending tasks submitted to this thread
     */
    private final Vector<Runnable> pendingTasks = new Vector<>();

    /**
     * Loop control variable
     */
    private boolean running = true;

    /**
     * Constructs a new QueueProcessingThread
     *
     * @param server instance of the running server
     */
    public QueueProcessingThread(MessageServer server) {
        super("Incoming/Outgoing Queue Processing Thread");
        this.server = server;
        this.start();
    }

    /**
     * Submits a message to be sent internally on this server
     *
     * @param username name of user on this server to send message to
     * @param msg      message to send
     */
    public void submitMessageToIncoming(String username, MailMessage msg) {
        pendingTasks.add(() -> this.incomingQueue.add(new Pair<>(username, msg)));
    }

    /**
     * Submits a message to be relayed to another server
     *
     * @param msg message to be relayed
     */
    public void submitMessageToOutgoing(SMTPMailMessage msg) {
        pendingTasks.add(() -> this.outgoingQueue.add(msg));
    }

    /**
     * Cleans up and shuts down this thread
     */
    public void shutdown() {
        running = true;

        this.relayWorkers.forEach(c -> c.submitTask(c::notifyRemoteToDisconnect));
        this.relayWorkers.clear();
    }

    /**
     * Process the incoming message queue
     *
     * @param taskLimit maximum number of queued messages that can be sent this cycle
     */
    private void processIncomingQueue(final int taskLimit) {
        int processed = 0;
        while (processed < taskLimit) {
            // check for content, don't just block here
            if (incomingQueue.peek() == null) {
                return;
            }

            Pair<String, MailMessage> userMail = incomingQueue.poll();
            String userName = userMail.getKey();
            MailMessage message = userMail.getVal();

            // log message to file
            writeMessageToFile("logs/localServer", userName, message);

            server.relayMessageToLocalUser(userName, message);

            processed++;
        }
    }

    /**
     * Processes the outgoing message queue
     *
     * @param taskLimit maximum number of queued messages that can be sent this cycle
     */
    private void processOutgoingQueue(final int taskLimit) {
        int processed = 0;
        while (processed < taskLimit) {
            // check for content, don't just block here
            if (outgoingQueue.peek() == null) {
                return;
            }

            SMTPMailMessage message = outgoingQueue.poll();

            for (String recipient : message.getSmtpRecipients()) {
                String[] userHost = recipient.split("@", 2);

                if (userHost.length != 2) {
                    server.logln("Malformed data in outgoing queue: " + recipient);
                    continue;
                }

                // log message to file
                String userName = userHost[0];
                String remoteHost = userHost[1];

                // don't send it locally, that has already been handled
                if (server.isLocalServerAddress(remoteHost)) {
                    continue;
                }

                writeMessageToFile("logs/" + remoteHost, userName, message);

                // attempt to relay to remotes
                // todo
            }

            processed++;
        }
    }

    /**
     * Logs message to file
     *
     * @param folderName directory to put the file under
     * @param userName   user responsible
     * @param msg        message to log
     */
    private void writeMessageToFile(String folderName, String userName, MailMessage msg) {
        String path = folderName + "/";

        if (userName != null) {
            path += userName + "/";
        }

        // format current time to use as file name
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd-HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String currentTime = dtf.format(now);

        path += currentTime;
        File out = new File(path + ".txt");

        try {
            if (!out.exists()) {
                out.getParentFile().mkdirs(); // make directories for path
                out.createNewFile(); // make file
            }

            PrintWriter writer = new PrintWriter(new FileOutputStream(out));
            writer.write(msg.toString());

            writer.flush();
            writer.close();
        } catch (IOException e) {
            server.logln("Error logging message from " + msg.getSender() + ": " + e);
            e.printStackTrace();
        }
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

    @Override
    public void run() {
        while (running) {
            processPendingTasks();
            processIncomingQueue(10); // limit to 10 messages per cycle
            processOutgoingQueue(10); // limit to 10 messages per cycle

            // sleep this thread for a bit so we aren't
            // just pegging out the CPU and eating all its cycles
            try {
                Thread.sleep(250); // 0.25 seconds
            } catch (InterruptedException ignored) {
                // fall through to continue
            }
        }
    }
}
