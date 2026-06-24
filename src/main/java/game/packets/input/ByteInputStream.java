package game.packets.input;

/**
 * Represents an abstract stream of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public interface ByteInputStream {

    /**
     * Reads the next byte off the stream.
     * @return The next byte as an integer.
     */
    int readByte();

    default void readBytes(byte[] destination, int offset, int length) {
	for (int i = 0; i < length; i++) {
	    destination[offset + i] = (byte) readByte();
	}
    }

    default int readShortLE() {
	int byte1 = readByte();
	int byte2 = readByte();
	return (byte2 << 8) + byte1;
    }

    default int readShortBE() {
	int byte2 = readByte();
	int byte1 = readByte();
	return (byte2 << 8) + byte1;
    }

    default int readIntLE() {
	int byte1 = readByte();
	int byte2 = readByte();
	int byte3 = readByte();
	int byte4 = readByte();
	return (byte4 << 24) + (byte3 << 16) + (byte2 << 8) + byte1;
    }

    default int readIntBE() {
	int byte4 = readByte();
	int byte3 = readByte();
	int byte2 = readByte();
	int byte1 = readByte();
	return (byte4 << 24) + (byte3 << 16) + (byte2 << 8) + byte1;
    }

    default long readLongLE() {
	long byte1 = readByte();
	long byte2 = readByte();
	long byte3 = readByte();
	long byte4 = readByte();
	long byte5 = readByte();
	long byte6 = readByte();
	long byte7 = readByte();
	long byte8 = readByte();
	return (byte8 << 56) + (byte7 << 48) + (byte6 << 40) + (byte5 << 32) + (byte4 << 24) + (byte3 << 16)
		+ (byte2 << 8) + byte1;
    }

    default long readLongBE() {
	long byte8 = readByte();
	long byte7 = readByte();
	long byte6 = readByte();
	long byte5 = readByte();
	long byte4 = readByte();
	long byte3 = readByte();
	long byte2 = readByte();
	long byte1 = readByte();
	return (byte8 << 56) + (byte7 << 48) + (byte6 << 40) + (byte5 << 32) + (byte4 << 24) + (byte3 << 16)
		+ (byte2 << 8) + byte1;
    }

    default void skip(int length) {
	for (int i = 0; i < length; i++) {
	    readByte();
	}
    }

    /**
     * Gets the number of bytes read from the stream.
     * @return The number of bytes as a long integer.
     */
    long getBytesRead();

    /**
     * Gets the number of bytes still left for reading.
     * @return The number of bytes as a long integer.
     */
    long available();
}
