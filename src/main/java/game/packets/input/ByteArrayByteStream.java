package game.packets.input;

import game.tools.HexTool;
import java.io.IOException;


/**
 * Provides for an abstraction layer for an array of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 326
 */
public class ByteArrayByteStream implements SeekableInputStreamBytestream {

    private int pos = 0;
    private final byte[] arr;

    /**
     * Class constructor.
     *
     * @param arr Array of bytes to wrap the stream around.
     */
    public ByteArrayByteStream(byte[] arr) {
	this.arr = arr;
    }

    /**
     * Gets the current position of the stream.
     *
     * @return The current position of the stream.
     * @see net.sf.odinms.tools.data.input.SeekableInputStreamBytestream#getPosition()
     */
    @Override
    public long getPosition() {
	return pos;
    }

    /**
     * Seeks the pointer the the specified position.
     *
     * @param offset The position you wish to seek to.
     * @see net.sf.odinms.tools.data.input.SeekableInputStreamBytestream#seek(long)
     */
    @Override
    public void seek(long offset) throws IOException {
	pos = (int) offset;
    }

    /**
     * Returns the numbers of bytes read from the stream.
     *
     * @return The number of bytes read.
     * @see net.sf.odinms.tools.data.input.ByteInputStream#getBytesRead()
     */
    @Override
    public long getBytesRead() {
	return pos;
    }

    /**
     * Reads a byte from the current position.
     *
     * @return The byte as an integer.
     * @see net.sf.odinms.tools.data.input.ByteInputStream#readByte()
     */
    @Override
    public int readByte() {
	if (pos >= arr.length) {
	    throw new EndOfFileException();
	}
	return ((int) arr[pos++]) & 0xFF;
    }

    @Override
    public void readBytes(byte[] destination, int offset, int length) {
	if (length < 0 || offset < 0 || length > destination.length - offset) {
	    throw new IndexOutOfBoundsException();
	}
	if (length > arr.length - pos) {
	    throw new EndOfFileException();
	}
	System.arraycopy(arr, pos, destination, offset, length);
	pos += length;
    }

    @Override
    public int readShortLE() {
	if (2 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	int value = (arr[pos] & 0xFF) | ((arr[pos + 1] & 0xFF) << 8);
	pos += 2;
	return value;
    }

    @Override
    public int readShortBE() {
	if (2 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	int value = ((arr[pos] & 0xFF) << 8) | (arr[pos + 1] & 0xFF);
	pos += 2;
	return value;
    }

    @Override
    public int readIntLE() {
	if (4 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	int value = (arr[pos] & 0xFF)
		| ((arr[pos + 1] & 0xFF) << 8)
		| ((arr[pos + 2] & 0xFF) << 16)
		| ((arr[pos + 3] & 0xFF) << 24);
	pos += 4;
	return value;
    }

    @Override
    public int readIntBE() {
	if (4 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	int value = ((arr[pos] & 0xFF) << 24)
		| ((arr[pos + 1] & 0xFF) << 16)
		| ((arr[pos + 2] & 0xFF) << 8)
		| (arr[pos + 3] & 0xFF);
	pos += 4;
	return value;
    }

    @Override
    public long readLongLE() {
	if (8 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	long value = ((long) arr[pos] & 0xFF)
		| (((long) arr[pos + 1] & 0xFF) << 8)
		| (((long) arr[pos + 2] & 0xFF) << 16)
		| (((long) arr[pos + 3] & 0xFF) << 24)
		| (((long) arr[pos + 4] & 0xFF) << 32)
		| (((long) arr[pos + 5] & 0xFF) << 40)
		| (((long) arr[pos + 6] & 0xFF) << 48)
		| (((long) arr[pos + 7] & 0xFF) << 56);
	pos += 8;
	return value;
    }

    @Override
    public long readLongBE() {
	if (8 > arr.length - pos) {
	    throw new EndOfFileException();
	}
	long value = (((long) arr[pos] & 0xFF) << 56)
		| (((long) arr[pos + 1] & 0xFF) << 48)
		| (((long) arr[pos + 2] & 0xFF) << 40)
		| (((long) arr[pos + 3] & 0xFF) << 32)
		| (((long) arr[pos + 4] & 0xFF) << 24)
		| (((long) arr[pos + 5] & 0xFF) << 16)
		| (((long) arr[pos + 6] & 0xFF) << 8)
		| ((long) arr[pos + 7] & 0xFF);
	pos += 8;
	return value;
    }

    @Override
    public void skip(int length) {
	if (length < 0) {
	    throw new EndOfFileException("amount of bytes to skip is below 0, n = " + length);
	}
	if (length > arr.length - pos) {
	    throw new EndOfFileException();
	}
	pos += length;
    }

    /**
     * Returns the current stream as a hexadecimal string of values.
     * Shows the entire stream, and the remaining data at the current position.
     *
     * @return The current stream as a string.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
	String nows = "";
	if (arr.length - pos > 0) {
	    byte[] now = new byte[arr.length - pos];
	    System.arraycopy(arr, pos, now, 0, arr.length - pos);
	    nows = HexTool.toString(now);
	}
//	return "All: " + HexTool.toString(arr) + "\nNow: " + nows;
	return "Data: " + nows;
    }
    
    /**
     * Returns the number of bytes available from the stream.
     *
     * @return Number of bytes available as a long integer.
     * @see net.sf.odinms.tools.data.input.ByteInputStream#available()
     */
    @Override
    public long available() {
	return arr.length - pos;
    }
}
