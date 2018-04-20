package server;

import common.GUIResource;

import java.io.*;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages user login information
 */
public class AuthenticationManager {

    /**
     * All managed users
     */
    private final Map<String, User> managedUsers = new HashMap<>();

    /**
     * Instance of the server
     */
    private final GUIResource gui;

    /**
     * Constructs a new instance of the auth manager
     *
     * @param guiResource instance of the server
     */
    public AuthenticationManager(GUIResource guiResource) {
        this.gui = guiResource;

        // verify both our security algorithms exist immediately
        // to do this, we just call the methods using them
        byte[] testSalt = generateRandomSalt();
        getHashedPassword("test-pass", testSalt);
    }

    /**
     * Validates a user login
     *
     * @param username      name that's attempting to login
     * @param plainTextPass password for that username
     * @return True if valid login, false if bad
     */
    public boolean isValidLogin(String username, String plainTextPass) {
        User target = managedUsers.get(username);
        if (target == null) {
            return false; // user does not exist on the server
        }

        byte[] salt = target.getPasswordSalt();
        String passHash = getHashedPassword(plainTextPass, salt);

        return passHash.equals(target.getPasswordHash());
    }

    /**
     * Attempts to load saved data from file
     *
     * @param saveFilePath path to read data from
     */
    public void loadSavedUserData(String saveFilePath) {
        if (!savedDataExists(saveFilePath)) {
            return;
        }

        try (DataInputStream streamIn = new DataInputStream(new FileInputStream(new File(saveFilePath)))) {
            while (streamIn.available() > 0) {
                User user = User.readFromStream(streamIn);
                managedUsers.put(user.username, user);
                gui.logln("Loaded saved user: " + user.username);
            }
        } catch (IOException ex) {
            gui.logln("Error reading saved user data: " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Attempts to save user data to file
     *
     * @param saveFilePath path to save the data to
     */
    public void writeUserData(String saveFilePath) {
        if (managedUsers.size() == 0) {
            return; // don't save an empty file if we have no data
        }

        try (DataOutputStream streamOut = new DataOutputStream(new FileOutputStream(new File(saveFilePath)))) {
            for (User user : managedUsers.values()) {
                user.writeToStream(streamOut);
            }
        } catch (IOException ex) {
            gui.logln("Error reading saved user data: " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Adds a new user to the auth manager
     *
     * @param username      name to identify the user as
     * @param plainTextPass password to use to authenticate
     */
    public void addNewUser(String username, String plainTextPass) {
        byte[] salt = generateRandomSalt();
        String hashedPass = getHashedPassword(plainTextPass, salt);

        managedUsers.put(username, new User(username, salt, hashedPass));
    }

    /**
     * Checks if any previous saved data exists
     *
     * @param saveFilePath path to check for saved data
     * @return True if present
     */
    private boolean savedDataExists(String saveFilePath) {
        File savedData = new File(saveFilePath);
        boolean filePresent = !savedData.isDirectory() && savedData.exists();

        if (!filePresent) {
            String canonicalPath = savedData.getAbsolutePath(); // init to absolute

            try {
                canonicalPath = savedData.getCanonicalPath();
            } catch (IOException ignored) {
            }

            gui.logln("Unable to access saved user data at " + canonicalPath);
        }

        return filePresent;
    }

    /**
     * Takes a given plain-text password and returns a hashed version of it using the salt provided.
     * <p>
     * This is not a particularly great way to store passwords but it's ever so slightly better than storing
     * them in plain-text directly. SHA-1 isn't an algorithm you should use for hashing passwords, nor is this the best
     * way to handle it. Look up Bcrypt or one of the other dedicated password hashing algorithms.
     *
     * @param plainText string to hash
     * @param salt      salt to use while generating the hash
     * @return hashed version of the plain-text input
     */
    private String getHashedPassword(String plainText, byte[] salt) {
        final String ALGORITHM_NAME = "SHA-1";
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(ALGORITHM_NAME);
        } catch (NoSuchAlgorithmException ex) {
            gui.logln("Cannot find security algorithm: " + ALGORITHM_NAME + "; Server unable to continue");
            throw new RuntimeException(ex);
        }

        digest.update(salt);
        byte[] bytes = digest.digest(plainText.getBytes()); // gets us decimal bytes of the salted hash

        StringBuilder sb = new StringBuilder();
        for (byte bite : bytes) {
            // translate from decimal to hexadecimal
            // 02 - use two characters; x - format as hexadecimal
            sb.append(String.format("%02x", bite));
        }

        return sb.toString();
    }

    /**
     * Generates and returns a random salt
     *
     * @return new random salt
     */
    private byte[] generateRandomSalt() {
        final String ALGORITHM_NAME = "SHA1PRNG";
        SecureRandom secRandom;

        try {
            secRandom = SecureRandom.getInstance(ALGORITHM_NAME);
        } catch (NoSuchAlgorithmException ex) {
            gui.logln("Cannot find security algorithm: " + ALGORITHM_NAME + "; Server unable to continue");
            throw new RuntimeException(ex);
        }

        byte[] salt = new byte[User.SALT_LENGTH];
        secRandom.nextBytes(salt); // populate
        return salt;
    }

    /**
     * Represents a user
     */
    public static class User {
        /**
         * Length of the password salt
         */
        private static final int SALT_LENGTH = 16;

        /**
         * User's login name
         */
        private final String username;

        /**
         * Hash of the user's login password
         */
        private final String passHash;

        /**
         * Salt of the user's login password
         */
        private final byte[] passSalt;

        /**
         * Constructs a new User
         *
         * @param usernameIn name to identify the user with
         * @param saltIn     salt to use when generating password hashes
         * @param passHashIn hash of user's password
         */
        public User(String usernameIn, byte[] saltIn, String passHashIn) {
            this.username = usernameIn;
            this.passSalt = saltIn;
            this.passHash = passHashIn;
        }

        /**
         * Reads a new User from the data stream
         *
         * @param streamIn Stream to read from
         * @return a new User
         * @throws IOException see {@link DataInputStream}
         */
        public static User readFromStream(DataInputStream streamIn) throws IOException {
            String username = streamIn.readUTF();
            String passHash = streamIn.readUTF();
            int saltLength = streamIn.readInt();

            byte[] passSalt = new byte[saltLength];
            for (int i = 0; i < passSalt.length; i++) {
                passSalt[i] = streamIn.readByte();
            }

            return new User(username, passSalt, passHash);
        }

        /**
         * Gets this user's login name
         *
         * @return user's name
         */
        public final String getUserName() {
            return this.username;
        }

        /**
         * Gets this user's password salt
         *
         * @return user's password salt
         */
        public final byte[] getPasswordSalt() {
            return this.passSalt;
        }

        /**
         * Gets this user's password hash
         *
         * @return user's password hash
         */
        public final String getPasswordHash() {
            return this.passHash;
        }

        /**
         * Writes this user to the DataStream
         *
         * @param streamOut Stream to write the user to
         * @throws IOException see {@link DataOutputStream}
         */
        public void writeToStream(DataOutputStream streamOut) throws IOException {
            streamOut.writeUTF(username);
            streamOut.writeUTF(passHash);
            streamOut.writeInt(passSalt.length);

            for (byte bite : this.passSalt) {
                streamOut.writeByte(bite);
            }
        }
    }
}
