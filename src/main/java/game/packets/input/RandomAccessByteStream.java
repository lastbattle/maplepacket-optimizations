package game.packets.input;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Provides an abstract layer to a byte stream. This layer can be accessed
 * randomly.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public class RandomAccessByteStream implements SeekableInputStreamBytestream {

    private RandomAccessFile raf;
    private long read = 0;

    /**
     * Class constructor. Wraps this object around a RandomAccessFile.
     *
     * @param raf
     *            The RandomAccessFile instance to wrap this around.
     * @see java.io.RandomAccessFile
     */
    public RandomAccessByteStream(RandomAccessFile raf) {
        super();
        this.raf = raf;
    }

    /**
     * Reads a byte off of the file.
     *
     * @return The byte read as an integer.
     */
    @Override
    public int readByte() {
        int temp;
        try {
            temp = raf.read();
            if (temp == -1) {
//                throw new EndOfFileException();
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
        try {
            raf.readFully(destination, offset, length);
            read += length;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void skip(int length) {
        if (length < 0) {
            throw new EndOfFileException("amount of bytes to skip is below 0, n = " + length);
        }
        try {
            long position = raf.getFilePointer();
            if (length > raf.length() - position) {
                throw new RuntimeException("Reached the end of file.");
            }
            raf.seek(position + length);
            read += length;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see net.sf.odinms.tools.data.input.SeekableInputStreamBytestream#seek(long)
     */
    @Override
    public void seek(long offset) throws IOException {
        raf.seek(offset);
    }

    /**
     * @see net.sf.odinms.tools.data.input.SeekableInputStreamBytestream#getPosition()
     */
    @Override
    public long getPosition() throws IOException {
        return raf.getFilePointer();
    }

    /**
     * Get the number of bytes read.
     *
     * @return The number of bytes read as a long integer.
     */
    @Override
    public long getBytesRead() {
        return read;
    }

    /**
     * Get the number of bytes available for reading.
     *
     * @return The number of bytes available for reading as a long integer.
     */
    @Override
    public long available() {
        try {
            return raf.length() - raf.getFilePointer();
        } catch (IOException e) {
            System.err.println("ERROR" + e);
            return 0;
        }
    }
}
