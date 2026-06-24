package game.packets.input;

/**
 * 
 * @author
 */
public class MapleEncryptionException extends RuntimeException {

    private static final long serialVersionUID = -1515791315561L;

    public MapleEncryptionException() {
        super();
    }

    public MapleEncryptionException(String msg) {
        super(msg);
    }

    public MapleEncryptionException(Throwable cause) {
        super(cause);
    }

    public MapleEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
