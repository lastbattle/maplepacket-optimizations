package game.packets.input;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provides an abstract wrapper to a stream of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public class InputStreamByteStream implements ByteInputStream {

    private InputStream is;
    private long read = 0;

    /**
     * Class constructor.
     * Provide an input stream to wrap this around.
     *
     * @param is The input stream to wrap this object around.
     */
    public InputStreamByteStream(InputStream is) {
	this.is = is;
    }

    /**
     * Reads the next byte from the stream.
     *
     * @return Then next byte in the stream.
     */
    @Override
    public int readByte() {
	int temp;
	try {
	    temp = is.read();
	    if (temp == -1) {
//		throw new EndOfFileException();
		throw new RuntimeException("Reached the end of file.");
	    }
	    read++;
	    return temp;
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
    }

    @Override
    public void readBytes(byte[] destination, int offset, int length) {
        if (length < 0 || offset < 0 || length > destination.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        int totalRead = 0;
        try {
            while (totalRead < length) {
                int currentRead = is.read(destination, offset + totalRead, length - totalRead);
                if (currentRead == -1) {
                    throw new RuntimeException("Reached the end of file.");
                }
                totalRead += currentRead;
            }
            read += totalRead;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void skip(int length) {
        if (length < 0) {
            throw new EndOfFileException("amount of bytes to skip is below 0, n = " + length);
        }
        long remaining = length;
        try {
            while (remaining > 0) {
                long skipped = is.skip(remaining);
                if (skipped <= 0) {
                    if (is.read() == -1) {
                        throw new RuntimeException("Reached the end of file.");
                    }
                    skipped = 1;
                }
                remaining -= skipped;
                read += skipped;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the number of bytes read from the stream.
     *
     * @return The number of bytes read as a long integer.
     */
    @Override
    public long getBytesRead() {
	return read;
    }

    /**
     * Returns the number of bytes left in the stream.
     *
     * @return The number of bytes available for reading as a long integer.
     */
    @Override
    public long available() {
	try {
	    return is.available();
	} catch (IOException e) {
	    System.err.println("ERROR" + e);
	    return 0;
	}
    }
}
