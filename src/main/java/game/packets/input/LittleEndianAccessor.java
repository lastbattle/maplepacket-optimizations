package game.packets.input;

import java.awt.Point;

/**
 * Provides an abstract interface for a little-endian stream of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public interface LittleEndianAccessor {

    /**
     * Reads a byte.
     *
     * @return The byte read.
     */
    byte decode1();
    
    /**
     * Reads a boolean
     * 
     * @return The value read if it is above 0.
     */
    boolean decodeBoolean();

    /**
     * Reads a character.
     *
     * @return The character read.
     */
    char decode2Char();

    /**
     * Reads a short integer.
     *
     * @return The short integer read.
     */
    short decode2();

    int decode2_unsigned();
    
    /**
     * Reads a big-endian short integer.
     *
     * @return The short integer read.
     */
    short decode2_BigEndian();
    /**
     * Reads an unsigned big-endian short integer.
     *
     * @return The short integer read.
     */
    int decode2_BigEndian_unsigned();

    /**
     * Reads a integer.
     *
     * @return The integer read.
     */
    int decode4();

    long decode4_unsigned();
    
    /**
     * Reads a big-endian integer format.
     *
     * @return The integer read.
     */
    int decode4_bigEndian();
    /**
     * Reads an unsigned big-endian integer format.
     *
     * @return The integer read.
     */
    long decode4_BigEndian_unsigned();

    /**
     * Reads a long integer.
     *
     * @return The long integer read.
     */
    long decode8();
    
    /**
     * Reads a big-endian long integer.
     *
     * @return The long integer read.
     */
    long decode8_bigEndian();
    
    /**
     * Skips ahead <code>num</code> bytes.
     *
     * @param num Number of bytes to skip ahead.
     */
    void skip(int num);

    /**
     * Reads a number of bytes.
     *
     * @param num The number of bytes to read.
     * @return The bytes read.
     */
    byte[] decodeBuffer(int num);

    /**
     * Reads a floating point integer.
     *
     * @return The floating point integer read.
     */
    float decodeFloat();

    /**
     * Reads a double-precision integer.
     *
     * @return The double-precision integer read.
     */
    double decodeDouble();

    /**
     * Reads an ASCII string.
     *
     * @param n
     * @return The string read.
     */
    String decodeAsciiString(int n);

    /**
     * Reads a null-terminated ASCII string.
     *
     * @return The string read.
     */
    String decodeNullTerminatedAsciiString();

    /**
     * Reads a MapleStory convention lengthed ASCII string.
     *
     * @return The string read.
     */
    String decodeMapleAsciiString();
    
    String decodeByteToStringAsciiString();

    /**
     * Reads a MapleStory Position information
     *
     * @return The Position read.
     */
    Point decodeXYPosition();

    /**
     * Gets the number of bytes read so far.
     *
     * @return The number of bytes read as an long integer.
     */
    long getBytesRead();

    /**
     * Gets the number of bytes left for reading.
     *
     * @return The number of bytes left for reading as an long integer.
     */
    long available();

    /**
     * Prints the entire byte of packets remaining.
     *
     * @param count
     * @return
     */
    void print(int count);
    void printAll();
}
