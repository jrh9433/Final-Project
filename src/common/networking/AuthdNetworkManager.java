package common.networking;

import common.GUIResource;
import common.Pair;

import java.io.IOException;
import java.net.Socket;

/**
 * Single authority class to handle networking details along with authentication protocol
 */
public class AuthdNetworkManager extends NetworkManager {

    public AuthdNetworkManager(GUIResource client, Socket connection) throws IOException {
        super(client, connection);
    }

    /**
     * Attempts to authenticate with the remote using the given details
     *
     * @param username username to identify as
     * @param password password to prove ownership
     * @return True if login successful, false if failure
     */
    public boolean attemptLogin(String username, String password) {
        NetworkUtils.sendMessage(guiClient, netOut, username);
        NetworkUtils.sendMessages(guiClient, netOut, false, password); // flag obfuscates log to hide password

        String response = netIn.nextLine();
        logln(response);

        if (response.equals(ProtocolConstants.LOGIN_SUCCESS)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Server-side util to read incoming login credentials
     *
     * @return new Pair, username is the key, password is the value
     */
    public Pair<String, String> readIncomingLoginInfo() {
        String userName = netIn.nextLine();
        logln(userName);

        String password = netIn.nextLine();
        logln(password.replaceAll(".", "*")); // hide password in logs

        return new Pair<>(userName, password);
    }

    /**
     * Notifies the remote that the login failed
     */
    public void notifyLoginFailed() {
        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.LOGIN_REJECTED);
        this.closeConnections();
    }

    /**
     * Notifies the remote that the login was a success
     */
    public void notifyLoginSuccess() {
        NetworkUtils.sendMessage(guiClient, netOut, ProtocolConstants.LOGIN_SUCCESS);
    }
}
