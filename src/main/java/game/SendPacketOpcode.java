package game;




import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public enum SendPacketOpcode implements WritableIntValueHolder {
    // GENERAL
    ;
    private int code = -2;

    @Override
    public void setValue(int code) {
	this.code = code;
    }

    @Override
    public int getValue() {
	return code;
    }

    public static Properties getDefaultProperties() throws FileNotFoundException, IOException {
	Properties props = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(String.format("packet_op/sendops_ver%d.properties", 83))) {
            props.load(fileInputStream);
        }
	return props;
    }

    static {
	reloadValues();
    }

    public static SendPacketOpcode getSendPacketOpcodeByValue(short value) {
        for (SendPacketOpcode send : values()) {
            if (send.getValue() == value) {
                return send;
            }
        }
        return null;
    }

    public static final void LoadValues() {
	// static.
    }

    public static final void reloadValues() {
	try {
	    ExternalCodeTableGetter.populateValues(getDefaultProperties(), values());
	} catch (IOException e) {
	    throw new RuntimeException("Failed to load sendops", e);
	}
    }
}
