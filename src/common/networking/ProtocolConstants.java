package common.networking;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Message constants used to communicate with the remote
 */
public class ProtocolConstants {

    /**
     * Port that the server listens on
     */
    public static final int SERVER_DEFAULT_LISTEN_PORT = 25;

    /**
     * Character set to use for data transmission per SMTP standard
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    /**
     * Message delimiter to use per SMTP standard
     */
    public static final String MSG_DELIMITER = "\r\n";

    /**
     * Sent when a login is successful
     */
    public static final String LOGIN_SUCCESS = "ACCEPTED";

    /**
     * Sent when a login is rejected because of a bad username/password combination
     */
    public static final String LOGIN_REJECTED = "DECLINED";

    /**
     * Sent by the server as an initializing hello
     */
    public static final int INIT_HELLO_CODE = 220;

    /**
     * Sent by the server to acknowledge a client request in the affirmative
     */
    public static final int REQUEST_OKAY_CODE = 250;

    /**
     * Main message body data terminator
     */
    public static final String DATA_TERMINATOR = ".";

    /**
     * Server 354 response constant
     */
    public static final String END_DATA_WITH = "354 End data with <CR><LF> " + DATA_TERMINATOR + "<CR><LF>";

    /**
     * Mail sender prefix constant
     */
    public static final String MAIL_FROM_PREFIX = "MAIL FROM:";

    /**
     * Mail recipient prefix constant
     */
    public static final String RECIPIENT_TO_PREFIX = "RCPT TO:";

    /**
     * Mail body content data header
     */
    public static final String DATA_HEADER = "DATA";
}
