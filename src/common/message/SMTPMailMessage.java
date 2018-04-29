package common.message;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMTPMailMessage extends MailMessage {

    /**
     * Regex pattern to use to find email addresses in strings
     */
    public static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

    /**
     * SMTP sender
     */
    private String smtpFrom;

    /**
     * SMTP recipients
     * <p>
     * Not necessarily the same as message to, cc, etc
     */
    private String[] smtpRecipients;

    public SMTPMailMessage(boolean encrypted, String smtpFrom, String[] smtpRecipients, String[] messageContents) {
        super();
        setEncrypted(encrypted);
        this.smtpFrom = smtpFrom;
        this.smtpRecipients = smtpRecipients;

        parseRawMessageBody(messageContents);
    }

    /**
     * Returns the SMTP sender
     *
     * @return smtp sender
     */
    public String getSmtpFrom() {
        return smtpFrom;
    }

    /**
     * Gets intended SMTP recipients of the message
     *
     * @return smtp recipients
     */
    public String[] getSmtpRecipients() {
        return smtpRecipients;
    }

    /**
     * Takes the raw SMTP body directly from the constructor (and therefore right after we read it in the network layer)
     * and sets the needed fields and formats the main body contents for display within the GUI
     *
     * @param contents raw SMTP body contents
     */
    private void parseRawMessageBody(String[] contents) {
        // remove SMTP protocol formatting from values before we set them
        String from = contents[0].replace("From: ", "");
        String to = contents[1].replace("To: ", "");
        String cc = contents[2].replace("Cc: ", "");
        String date = contents[3].replace("Date: ", "");
        String subject = contents[4].replace("Subject: ", "");

        // parse from
        this.setFrom(getAllMatches(from, EMAIL_ADDRESS_PATTERN)[0]);
        this.setTo(getAllMatches(to, EMAIL_ADDRESS_PATTERN));
        this.setCC(getAllMatches(cc, EMAIL_ADDRESS_PATTERN));
        this.setDate(date);
        this.setSubject(subject);

        StringBuilder builder = new StringBuilder();

        // array index at which we should use to start reading the body
        // 6 gets us clear of all the fields we read above, as well as the newlines between them and the start
        // off the main body
        int displayStart = 6;

        // length of body array, should always be total content length minus display start
        int arrayLength = contents.length - displayStart;

        String[] body = new String[arrayLength];
        System.arraycopy(contents, displayStart, body, 0, arrayLength);

        for (String line : body) {
            builder.append(line).append("\n");
        }

        setMessage(builder.toString());
    }

    private String[] getAllMatches(String source, Pattern regexPattern) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = regexPattern.matcher(source);

        while (matcher.find()) {
            matches.add(matcher.group());
        }

        return matches.toArray(new String[0]);
    }

    public String toString() {
        return String.format("SMTP From: %s%n" +
                        "SMTP Recip: %s%n" +
                        "%s",
                smtpFrom,
                Arrays.toString(smtpRecipients),
                super.toString());
    }
}
