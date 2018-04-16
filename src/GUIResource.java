import javax.swing.*;
import java.awt.*;

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

    /**
     * Process a login reply from the SharedWorkerThread.
     * <p>
     * Thread safe, can be called directly from the worker.
     *
     * @param wasSuccess True, if login was successful
     */
    void processLoginResponse(boolean wasSuccess);

    /**
     * Gets the location that will position the specified dialog window at the
     * center of the specified parent window.
     * <p>
     * This is not the same thing as the actual center of the parent window.
     *
     * @param parent   Parent window to center on
     * @param toCenter Dialog to center
     * @return Point that will result in the dialog being centered
     */
    default Point getCenteredPosition(JFrame parent, JDialog toCenter) {
        double parentHeight = parent.getSize().getHeight();
        double parentWidth = parent.getSize().getWidth();
        double parentX = parent.getLocation().getX();
        double parentY = parent.getLocation().getY();

        // account for entirety of parent window size
        int targetX = (int) (parentX + parentWidth) / 2;
        int targetY = (int) (parentY + parentHeight) / 2;

        // account for this login window's size
        targetX -= toCenter.getSize().width / 2;
        targetY -= toCenter.getSize().height / 2;

        return new Point(targetX, targetY);
    }
}
