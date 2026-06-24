package game.packets.output;

import java.awt.Point;

/**
 * Provides an interface to a writer class that writes a little-endian sequence
 * of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public interface LittleEndianWriter {

    /**
     * Write the number of zero bytes
     *
     * @param i The bytes to write.
     */
    public void writeZeroBytes(int i);

    /**
     * Write an array of bytes to the sequence.
     *
     * @param b The bytes to write.
     */
    public void encodeBuffer(byte b[]);

    /**
     * Write a byte to the sequence.
     *
     * @param b The byte to write.
     */
    public void encode1(byte b);

    /**
     * Write a byte in integer form to the sequence.
     *
     * @param b The byte as an <code>Integer</code> to write.
     */
    public void encode1(int b);

    /**
     * Writes an integer to the sequence.
     *
     * @param i The integer to write.
     */
    public void encode4(int i);
    public void encode4(long i);

    /**
     * Write a short integer to the sequence.
     *
     * @param s The short integer to write.
     */
    public void encode2(int s);

    /**
     * Write a long integer to the sequence.
     * @param l The long integer to write.
     */
    public void encode8(long l);

    public void encodeBoolean(boolean value);
    
    /**
     * Writes an ASCII string the the sequence.
     *
     * @param s The ASCII string to write.
     */
    void encodeAsciiString(String s);
    void encodeAsciiString(String s, int max);

    /**
     * Writes a null-terminated ASCII string to the sequence.
     *
     * @param s The ASCII string to write.
     */
    void encodeNullTerminatedAsciiString(String s);

    /**
     * Writes a 2D 4 byte (2x2) position information
     *
     * @param s The Point position to write.
     */
    void encodeXYCoordinate(Point s);

    /**
     * Writes a maple-convention ASCII string to the sequence.
     *
     * @param s The ASCII string to use maple-convention to write.
     */
    void encodeString(String s);
    void encodeString(String s, int max, char end);

    void encodeHex(String hex);
    
    /**
     * Writes an int64 file timestamp to the client
     * @param timeStampinMillis
     * @param roundToMinutes 
     */
    void encodeFileTimestamp(long timeStampinMillis, boolean roundToMinutes);
    
    /**
     * Writes an int32 quest timestamp to the client
     * @param realTimestamp 
     */
    void encodeQuestTimestamp(final long realTimestamp);
    
    /**
     *  Writes an int32 iten timestamp to the client that starts from the year 2000.
     * @param realTimestamp 
     */
    void encodeItemTimestamp(final long realTimestamp);
    
    /**
     * Writes an int64 tempban timestamp to the client.
     * 100 nsseconds from 1/1/1601 -> 1/1/1970
     * @param realTimestamp 
     */
    void encodeTempBanTimestamp(final long realTimestamp);
}
