import javax.swing.*;

/**
 * Interface for accessing common elements from the shared
 * client threads
 */
public interface GUIResource {

    /**
     * Shows a pop-up dialog on the screen
     * <p>
     * This method is thread safe and can be called from any context
     *
     * @param message Message contents to display
     * @param title   Title to show in the title bar
     * @param type    Type of dialog to show, use {@link JOptionPane} constants
     */
    void showMessageDialog(String message, String title, int type);

    /**
     * Notifies the implementing GUI that the network thread has disconnected
     */
    void updateForDisconnect();

    /**
     * Logs a message to the implementation
     *
     * @param msg Message to write
     */
    default void logln(String msg) {
        // client only logs to console output, server overrides later
        System.out.println(msg);
    }

    /**
     * Gets whether the implementing class belongs to the server side of the application
     * or the client side of the application.
     *
     * @return True, if server
     */
    boolean isServer();
}
