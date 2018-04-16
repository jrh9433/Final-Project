/**
 * Object constants used to communicate with the remote
 */
public class ProtocolConstants {
    /**
     * Sent when a login is successful
     */
    public static final String LOGIN_SUCCESS = "ACCEPTED";

    /**
     * Sent when a login is rejected because of a bad username/password combination
     */
    public static final String LOGIN_REJECTED = "DECLINED";
}
