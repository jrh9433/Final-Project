package common.message;

import java.io.Serializable;
import java.util.*;

/**
 * Represents an email
 */
public class MailMessage implements Serializable {
    public static final long serialVersionUID = 100L;

    // Backing data storage
    private final List<String> toRecipients = new ArrayList<>();
    private final List<String> ccRecipients = new ArrayList<>();
    private boolean encrypted;
    private String sender;
    private String date;
    private String subject;
    private String message;

    /**
     * No args constructor, all can be populated later
     */
    public MailMessage() {
    }

    /**
     * Convenience constructor
     *
     * @param to              message recipient
     * @param from            message from
     * @param subject         message subject
     * @param messageContents contents of the message
     */
    public MailMessage(String to, String from, String subject, String messageContents) {
        this.toRecipients.add(to);
        this.sender = from;
        this.date = Calendar.getInstance().getTime().toString();
        this.subject = subject;
        this.message = messageContents;
    }

    /**
     * All args constructor
     *
     * @param encrypted       sets the message to be encrypted
     * @param to              message recipient
     * @param from            message from
     * @param cc              additional cc recipients
     * @param date            date it was written
     * @param subject         message subject
     * @param messageContents message contents
     */
    public MailMessage(boolean encrypted, String[] to, String from,
                       String[] cc, String date, String subject, String messageContents) {
        this.encrypted = encrypted;
        this.toRecipients.addAll(Arrays.asList(to));
        this.sender = from;
        this.ccRecipients.addAll(Arrays.asList(cc));
        this.date = date;
        this.subject = subject;
        this.message = messageContents;
    }

    /**
     * Gets whether this message is encrypted
     *
     * @return True if encrypted
     */
    public boolean isEncrypted() {
        return encrypted;
    }

    /**
     * Sets whether this message should be encrypted
     *
     * @param setEncr True to be encrypted
     */
    public void setEncrypted(boolean setEncr) {
        encrypted = setEncr;
    }

    /**
     * Gets all "To" recipients of the message
     *
     * @return to recipients
     */
    public String[] getTo() {
        return toRecipients.toArray(new String[0]);
    }

    /**
     * Replaces the "to" list with the given recipient/s
     *
     * @param to new recipients
     */
    public void setTo(String... to) {
        toRecipients.clear();
        toRecipients.addAll(Arrays.asList(to));
    }

    /**
     * Adds one or more recipients to the "to" list
     *
     * @param to address to add
     */
    public void addTo(String... to) {
        toRecipients.addAll(Arrays.asList(to));
    }

    /**
     * Gets the message sender's address
     *
     * @return sender's address
     */
    public String getSender() {
        return sender;
    }

    /**
     * Sets the sender's address
     *
     * @param sender sender's address
     */
    public void setFrom(String sender) {
        this.sender = sender;
    }

    /**
     * Gets "cc" level recipients
     *
     * @return cc recipients
     */
    public String[] getCC() {
        return ccRecipients.toArray(new String[0]);
    }

    /**
     * Replaces the "cc" list with the given recipient/s
     *
     * @param cc new recipients
     */
    public void setCC(String... cc) {
        ccRecipients.clear();
        ccRecipients.addAll(Arrays.asList(cc));
    }

    /**
     * Adds one or more recipients to the "cc" list
     *
     * @param cc address to add
     */
    public void addCC(String... cc) {
        ccRecipients.addAll(Arrays.asList(cc));
    }

    /**
     * Gets the date this message was written
     *
     * @return date
     */
    public String getDate() {
        return date;
    }

    /**
     * Sets the date this message was written
     *
     * @param sendDate date to set
     */
    public void setDate(String sendDate) {
        date = sendDate;
    }

    /**
     * Gets the subject of this message
     *
     * @return message subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the subject of this message
     *
     * @param sendSub subject to set
     */
    public void setSubject(String sendSub) {
        subject = sendSub;
    }

    /**
     * Gets the contents of this message
     *
     * @return message contents
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the contents of this message
     *
     * @param sentMessage contents
     */
    public void setMessage(String sentMessage) {
        message = sentMessage;
    }

    public String toString() {
        return String.format("Encrypted: %s%n" +
                        "From: %s%n" +
                        "To: %s%n" +
                        "Cc: %s%n" +
                        "Date: %s%n" +
                        "Subject: %s%n" +
                        "Body: %n%s%n",
                String.valueOf(encrypted),
                getSender(),
                Arrays.toString(getTo()),
                Arrays.toString(getCC()),
                getDate(),
                getSubject(),
                getMessage());
    }
}
