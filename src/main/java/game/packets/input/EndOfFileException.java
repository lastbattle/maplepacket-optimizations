package game.packets.input;

public class EndOfFileException extends RuntimeException {

    private static final long serialVersionUID = -420154764822555L;

    public EndOfFileException() {
	super();
    }
    
    public EndOfFileException(String msg) {
	super(msg);
    }

    public EndOfFileException(Throwable cause) {
	super(cause);
    }

    public EndOfFileException(String message, Throwable cause) {
	super(message, cause);
    }
}
