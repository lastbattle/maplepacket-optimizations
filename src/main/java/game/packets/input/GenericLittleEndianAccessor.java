package game.packets.input;

import game.tools.HexTool;
import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Provides a generic interface to a Little Endian stream of bytes.
 * 
 * @version 1.0
 * @author Frz
 * @since Revision 323
 */
public class GenericLittleEndianAccessor implements LittleEndianAccessor {

    // For the list of charset: 
    // https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html
    // https://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html
    // Korean = MS949
    // English = ISO-8859-1, UTF-8
    private static final Charset CHARSET = Charset.forName("MS932");
    
    private final ByteInputStream bs;

    /**
     * Class constructor - Wraps the accessor around a stream of bytes.
     *
     * @param bs The byte stream to wrap the accessor around.
     */
    public GenericLittleEndianAccessor(final ByteInputStream bs) {
	this.bs = bs;
    }

    /**
     * Read a single byte from the stream.
     *
     * @return The byte read.
     * @see net.sf.odinms.tools.data.input.ByteInputStream#readByte
     */
    @Override
    public final byte decode1() {
	return (byte) bs.readByte();
    }
    
    @Override
    public final boolean decodeBoolean() {
        return bs.readByte() > 0;
    }

    /**
     * Reads an integer from the stream.
     *
     * @return The integer read.
     */
    @Override
    public final int decode4() {
	return bs.readIntLE();
    }

    @Override
    public final long decode4_unsigned() {
	final int val = decode4();
	return (long) (val & 0xFFFFFFFFL);
    }
    
    /**
     * Reads a big-endian integer format.
     *
     * @return The integer read.
     */
    @Override
    public final int decode4_bigEndian() {
        return bs.readIntBE();
    }
    /**
     * Reads an unsigned big-endian integer format.
     *
     * @return The integer read.
     */
    @Override
    public final long decode4_BigEndian_unsigned() {
        final int val = decode4_bigEndian();
	return (long) (val & 0xFFFFFFFFL);
    }

    /**
     * Reads a short integer from the stream.
     *
     * @return The short read.
     */
    @Override
    public final short decode2() {
	return (short) bs.readShortLE();
    }

    @Override
    public final int decode2_unsigned() {
	final int val = decode2();
	return (val & 0xFFFF);
    }
    
    
    /**
     * Reads a big-endian short integer.
     *
     * @return The short integer read.
     */
    @Override
    public final short decode2_BigEndian() {
        return (short) bs.readShortBE();
    }
    /**
     * Reads an unsigned big-endian short integer.
     *
     * @return The short integer read.
     */
    @Override
    public final int decode2_BigEndian_unsigned() {
	final int val = decode2_BigEndian();
	return (val & 0xFFFF);
    }


    /**
     * Reads a single character from the stream.
     *
     * @return The character read.
     */
    @Override
    public final char decode2Char() {
	return (char) decode2();
    }

    /**
     * Reads a long integer from the stream.
     *
     * @return The long integer read.
     */
    @Override
    public final long decode8() {
	return bs.readLongLE();
    }
    
    /**
     * Reads a big-endian long integer.
     *
     * @return The long integer read.
     */
    @Override
    public final long decode8_bigEndian() {
        return bs.readLongBE();
    }

    /**
     * Reads a floating point integer from the stream.
     *
     * @return The float-type integer read.
     */
    @Override
    public final float decodeFloat() {
	return Float.intBitsToFloat(decode4());
    }

    /**
     * Reads a double-precision integer from the stream.
     *
     * @return The double-type integer read.
     */
    @Override
    public final double decodeDouble() {
	return Double.longBitsToDouble(decode8());
    }

    /**
     * Reads an ASCII string from the stream with length <code>n</code>.
     *
     * @param n Number of characters to read.
     * @return The string read.
     */
    @Override
    public final String decodeAsciiString(final int n) {
        if (n < 0)
            throw new EndOfFileException("string length is below 0, n = " + n);
        
	final byte ret[] = new byte[n];
	bs.readBytes(ret, 0, n);
        final String retString = new String(ret, CHARSET);
        return retString;
    }

    /**
     * Reads a null-terminated string from the stream.
     *
     * @return The string read.
     */
    @Override
    public final String decodeNullTerminatedAsciiString() {
	final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	byte b = 1;
	while (b != 0) {
	    b = decode1();
	    baos.write(b);
	}
	final byte[] buf = baos.toByteArray();
	final char[] chrBuf = new char[buf.length];
	for (int x = 0; x < buf.length; x++) {
	    chrBuf[x] = (char) buf[x];
	}
	return new String(chrBuf);
    }

    /**
     * Gets the number of bytes read from the stream so far.
     *
     * @return A long integer representing the number of bytes read.
     * @see net.sf.odinms.tools.data.input.ByteInputStream#getBytesRead()
     */
    @Override
    public final long getBytesRead() {
	return bs.getBytesRead();
    }

    /**
     * Reads a MapleStory convention lengthed ASCII string.
     * This consists of a short integer telling the length of the string,
     * then the string itself.
     *
     * @return The string read.
     */
    @Override
    public final String decodeMapleAsciiString() {
        short stringLen = decode2();
        
	return decodeAsciiString(stringLen);
    }
    
    @Override
    public final String decodeByteToStringAsciiString() {
	int len = decode2();
        
        if (len < 0)
            throw new EndOfFileException("string length is below 0, n = " + len);
        
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < len; i++) {
	    sb.append(Integer.toHexString(decode1()));
	}
	return sb.toString();
    }

    /**
     * Reads a MapleStory Position information.
     * This consists of 2 short integer.
     *
     * @return The Position read.
     */
    @Override
    public final Point decodeXYPosition() {
	final int x = decode2();
	final int y = decode2();
        
	return new Point(x, y);
    }

    /**
     * Reads <code>num</code> bytes off the stream.
     *
     * @param num The number of bytes to read.
     * @return An array of bytes with the length of <code>num</code>
     */
    @Override
    public final byte[] decodeBuffer(final int num) {
        if (num < 0)
            throw new EndOfFileException("buffer length is below 0, n = " + num);
                
	byte[] ret = new byte[num];
	bs.readBytes(ret, 0, num);
	return ret;
    }

    /**
     * Skips the current position of the stream <code>num</code> bytes ahead.
     *
     * @param num Number of bytes to skip.
     */
    @Override
    public void skip(final int num) {
        if (num < 0)
            throw new EndOfFileException("amount of bytes to skip is below 0, n = " + num);
                
	bs.skip(num);
    }

    /**
     * @see net.sf.odinms.tools.data.input.ByteInputStream#available
     */
    @Override
    public final long available() {
	return bs.available();
    }

    /**
     * @see java.lang.Object#toString
     */
    @Override
    public final String toString() {
	return bs.toString();
    }

    @Override
    public void print(int count) {
	if (bs.available() > 0) {
	    System.out.println(HexTool.toString(decodeBuffer(Math.min((int) available(), count))));
	}
    }

    @Override
    public void printAll() {
	if (bs.available() > 0) {
	    System.out.println(HexTool.toString(decodeBuffer((int) available())));
	}
    }
}
