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

    private void parseRawMessageBody(String[] contents) {
        String from = contents[0];
        String to = contents[1];
        String cc = contents[2];
        String date = contents[3];
        String subject = contents[4];

        // parse from
        this.setFrom(getAllMatches(from, EMAIL_ADDRESS_PATTERN)[0]);
        this.setTo(getAllMatches(to, EMAIL_ADDRESS_PATTERN));
        this.setCC(getAllMatches(cc, EMAIL_ADDRESS_PATTERN));
        this.setDate(date);
        this.setSubject(subject);

        StringBuilder builder = new StringBuilder();
        for (String line : contents) {
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
