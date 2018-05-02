package common.networking;

import common.GUIResource;
import common.message.MailMessage;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Various static utils to make network handling easier
 */
public class NetworkUtils {

    ////
    // I/O factory methods
    ////

    /**
     * Creates a new {@link Scanner} ready to process input from the given socket
     *
     * @param remote socket to use as an input source for the {@link Scanner}
     * @return new {@link Scanner}
     * @throws IOException see {@link Socket#getInputStream()}
     */
    public static Scanner getNewInputScanner(Socket remote) throws IOException {
        return new Scanner(new InputStreamReader(remote.getInputStream(), ProtocolConstants.DEFAULT_CHARSET))
                .useDelimiter(Pattern.compile(ProtocolConstants.MSG_DELIMITER));
    }

    /**
     * Creates a new {@link PrintWriter} ready to send output via the given socket
     *
     * @param remote socket to use as an output source for the {@link PrintWriter}
     * @return new {@link PrintWriter}
     * @throws IOException see {@link Socket#getOutputStream()}
     */
    public static PrintWriter getNewOutputWriter(Socket remote) throws IOException {
        return new PrintWriter(new OutputStreamWriter(remote.getOutputStream(), ProtocolConstants.DEFAULT_CHARSET));
    }

    ////
    // Data Transfer methods
    ////

    /**
     * Sends a network message to the remote
     * <p>
     * SMTP is a message based protocol
     *
     * @param gui     client gui to log to
     * @param writer  writer to send data with
     * @param message message contents
     */
    public static void sendMessage(GUIResource gui, PrintWriter writer, String message) {
        sendMessages(gui, writer, message);
    }

    /**
     * Sends multiple network messages in succession without waiting
     *
     * @param gui      client to log to
     * @param writer   writer to send data with
     * @param messages messages to send
     */
    public static void sendMessages(GUIResource gui, PrintWriter writer, String... messages) {
        sendMessages(gui, writer, false, messages);
    }


    /**
     * Writes multiple network messages in succession without waiting
     *
     * @param gui          client gui to log to
     * @param writer       writer to send data with
     * @param obfuscateLog True to obfuscate the contents of the messages in the log
     * @param messages     messages to send
     */
    public static void sendMessages(GUIResource gui, PrintWriter writer, boolean obfuscateLog, String... messages) {
        for (final String msg : messages) {
            gui.logln(obfuscateLog ? msg.replaceAll(".", "*") : msg);
            writer.println(msg);
        }

        writer.flush();
    }

    ////
    // Message formatting utils
    ////

    /**
     * Formats the given string for proper sending via SMTP, as defined
     * by the SMTP RFC
     *
     * @param sender sender address to format
     * @return SMTP formatted sender address
     */
    public static String getSmtpFrom(String sender) {
        return ProtocolConstants.MAIL_FROM_PREFIX + "<" + sender + ">";
    }

    /**
     * Formats the given array of addresses to the proper format as defined by the SMTP RFC
     *
     * @param recipientArrays array of addresses to format
     * @return formatted addresses
     */
    public static String[] getSmtpRecipients(String[]... recipientArrays) {
        List<String> formatted = new ArrayList<>();

        for (String[] array : recipientArrays) {
            for (String recipient : array) {
                if (recipient.isEmpty()) {
                    continue;
                }

                formatted.add(ProtocolConstants.RECIPIENT_TO_PREFIX + "<" + recipient + ">");
            }
        }

        return formatted.toArray(new String[0]);
    }

    /**
     * Breaks up the message and details into the proper body format
     * <p>
     * Mostly determined by the SMTP RFC, a few details are from black-box testing
     * other servers and clients.
     *
     * @param mail Message to format
     * @return Formatted message contents split into an array of lines
     */
    public static String[] formatMailContentsForSend(MailMessage mail) {
        List<String> dataContents = new ArrayList<>();
        // this is the body, so if we're encrypted add our header
        if (mail.isEncrypted()) {
            dataContents.add(ProtocolConstants.ENCRYPTION_HEADER);
        } else {
            dataContents.add(ProtocolConstants.NO_ENCRYPT_HEADER);
        }

        dataContents.add("From: " + mail.getSender());

        // handle the "pretty" to field
        dataContents.add(formatArrayOfAddresses("To: ", mail.getTo()));
        // handle the "pretty" cc field
        dataContents.add(formatArrayOfAddresses("Cc: ", mail.getCC()));

        dataContents.add("Date: " + mail.getDate());
        dataContents.add("Subject: " + mail.getSubject());
        dataContents.add("");

        dataContents.addAll(Arrays.asList(mail.getMessage().split("\n")));

        // encrypt as needed
        if (mail.isEncrypted()) {
            dataContents = caesarShift(dataContents, ProtocolConstants.CAESAR_SHIFT_AMOUNT);
        }

        return dataContents.toArray(new String[0]);
    }

    /**
     * Builds the To: and CC: lines in the body, with their prefix and formatted address list
     * <p>
     * To: user@host, user@host, user@host
     *
     * @param prefix String prefix
     * @param target addresses to be formatted
     * @return formatted address line, in its entirety
     */
    private static String formatArrayOfAddresses(String prefix, String[] target) {
        StringBuilder builder = new StringBuilder(prefix);

        if (target.length == 1) {
            builder.append(target[0]);
        } else {
            for (int i = 0; i < target.length; i++) {
                builder.append(target[i]);

                if (i != target.length - 1) {
                    builder.append(", ");
                }
            }
        }

        return builder.toString();
    }

    /**
     * Applies a Caesar Shift to the given list of strings.
     * <p>
     * To reverse the shift, subtract 26 by a given shift
     *
     * @param input List of strings to apply the shift to
     * @param shift amount to shift a letter by
     * @return a new List of shifted strings
     */
    public static List<String> caesarShift(List<String> input, int shift) {
        List<String> out = new ArrayList<>();
        StringBuilder strBuilder = new StringBuilder();

        for (String str : input) {
            // ignore the header, that needs to remain
            if (str.equals(ProtocolConstants.ENCRYPTION_HEADER)) {
                out.add(str); // add header
                continue;
            }

            char c;
            for (int i = 0; i < str.length(); i++) {
                c = str.charAt(i);

                if (Character.isLetter(c)) { // can only shift letters
                    c = (char) (str.charAt(i) + shift);

                    if ((Character.isLowerCase(str.charAt(i)) && c > 'z') || (Character.isUpperCase(str.charAt(i)) && c > 'Z')) {
                        c = (char) (str.charAt(i) - (26 - shift));
                    }
                }

                strBuilder.append(c);
            }

            out.add(strBuilder.toString()); // add shifted text to output
            strBuilder.delete(0, strBuilder.length()); // clear builder for re-use
        }

        return out;
    }
}
