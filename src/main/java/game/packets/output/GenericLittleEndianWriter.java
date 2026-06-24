package game.packets.output;

import game.tools.HexTool;
import game.tools.KoreanDateUtil;
import game.tools.StringUtil;
import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Provides a generic writer of a little-endian sequence of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 323
 */
public class GenericLittleEndianWriter implements LittleEndianWriter {

    // For the list of charset: 
    // https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html
    // https://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html
    // Korean = MS949
    // English = ISO-8859-1, UTF-8
    private static final Charset CHARSET = Charset.forName("MS932");
    private static final byte[] ZERO_BYTES = new byte[256];
    
    private ByteArrayOutputStream bos;
 //   public int pos = 0;

    /**
     * Class constructor - Protected to prevent instantiation with no arguments.
     */
    protected GenericLittleEndianWriter() {
	// Blah!
    }

    /**
     * Sets the byte-output stream for this instance of the object.
     *
     * @param bos The new output stream to set.
     */
    protected void setByteOutputStream(ByteArrayOutputStream bos) {
	this.bos = bos;
    }
    
    protected ByteArrayOutputStream getBao() {
	return bos;
    }

    /**
     * Class constructor - only this one can be used.
     *
     * @param bos The stream to wrap this objecr around.
     */
    public GenericLittleEndianWriter(ByteArrayOutputStream bos) {
	this.bos = bos;
    }

    /**
     * Write the number of zero bytes
     *
     * @param i The bytes to write.
     */
    @Override
    public void writeZeroBytes(int i) {
	while (i > 0) {
	    int length = Math.min(i, ZERO_BYTES.length);
	    bos.write(ZERO_BYTES, 0, length);
	    i -= length;
	}
    }

    /**
     * Write an array of bytes to the stream.
     *
     * @param b The bytes to write.
     */
    @Override
    public void encodeBuffer(byte[] b) {
	bos.write(b, 0, b.length);
    }

    /**
     * Write a byte to the stream.
     *
     * @param b The byte to write.
     */
    @Override
    public void encode1(byte b) {
	bos.write(b);
//	pos++;
    }

    /**
     * Write a byte in integer form to the stream.
     *
     * @param b The byte as an <code>Integer</code> to write.
     */
    @Override
    public void encode1(int b) {
	bos.write((byte) b);
//	pos++;
    }

    /**
     * Write a short integer to the stream.
     *
     * @param i The short integer to write.
     */
    @Override
    public void encode2(int i) {
	bos.write(i & 0xFF);
	bos.write((i >>> 8) & 0xFF);
    }

    /**
     * Writes an integer to the stream.
     *
     * @param i The integer to write.
     */
    @Override
    public void encode4(int i) {
	bos.write(i & 0xFF);
	bos.write((i >>> 8) & 0xFF);
	bos.write((i >>> 16) & 0xFF);
	bos.write((i >>> 24) & 0xFF);
    }

    @Override
    public void encode4(long i) {
	bos.write((int) i & 0xFF);
	bos.write((int) (i >>> 8) & 0xFF);
	bos.write((int) (i >>> 16) & 0xFF);
	bos.write((int) (i >>> 24) & 0xFF);
    }
    
    /**
     * Write a long integer to the stream.
     * @param l The long integer to write.
     */
    @Override
    public void encode8(long l) {
	bos.write((int) l & 0xFF);
	bos.write((int) (l >>> 8) & 0xFF);
	bos.write((int) (l >>> 16) & 0xFF);
	bos.write((int) (l >>> 24) & 0xFF);
	bos.write((int) (l >>> 32) & 0xFF);
	bos.write((int) (l >>> 40) & 0xFF);
	bos.write((int) (l >>> 48) & 0xFF);
	bos.write((int) (l >>> 56) & 0xFF);
    }
    
    @Override
    public void encodeBoolean(boolean value) {
        encode1(value ? 1 : 0);
    }
    
    /**
     * Writes an ASCII string the the stream.
     *
     * @param s The ASCII string to write.
     */
    @Override
    public void encodeAsciiString(String s) {
        byte[] buffer = s.getBytes(CHARSET);
        //System.out.println("Buffer: [Len = " + buffer.length + "] ");
	encodeBuffer(buffer);
    }

    @Override
    public void encodeAsciiString(String s, int max) {
	encodeBuffer(s.getBytes(CHARSET));
	for (int i = s.length(); i < max; i++) {
	    encode1(0);
	}
    }

    /**
     * Writes a maple-convention ASCII string to the stream.
     *
     * @param s The ASCII string to use maple-convention to write.
     */
    @Override
    public void encodeString(String s) {
        byte[] bytes = s.getBytes(CHARSET);
        
	encode2((short) bytes.length);
	encodeBuffer(bytes);
    }

    @Override
    public void encodeString(String s, int max, char end) {
	String mod = StringUtil.getRightPaddedStr(s, end, max);
	
	encode2((short)mod.length());
	encodeAsciiString(mod);
    }

    /**
     * Writes a null-terminated ASCII string to the stream.
     *
     * @param s The ASCII string to write.
     */
    @Override
    public void encodeNullTerminatedAsciiString(String s) {
	encodeAsciiString(s);
	encode1(0);
    }

    /**
     * Writes a 2D 4 byte position information
     *
     * @param s The Point position to write.
     */
    @Override
    public void encodeXYCoordinate(Point s) {
	encode2(s.x);
	encode2(s.y);
    }

    @Override
    public void encodeHex(String hex) {
	encodeBuffer(HexTool.getByteArrayFromHexString(hex));
    }
    
    /**
     * Writes an int64 file timestamp to the client
     * @param timeStampinMillis
     * @param roundToMinutes 
     */
    @Override
    public void encodeFileTimestamp(long timeStampinMillis, boolean roundToMinutes) {
        this.encode8(KoreanDateUtil.getFileTimestamp(timeStampinMillis, roundToMinutes));
    }
    
    /**
     * Writes an int32 quest timestamp to the client
     * @param realTimestamp 
     */
    @Override
    public void encodeQuestTimestamp(final long realTimestamp) {
        this.encode4(KoreanDateUtil.getQuestTimestamp(realTimestamp));
    }
    
    /**
     *  Writes an int32 iten timestamp to the client that starts from the year 2000.
     * @param realTimestamp 
     */
    @Override
    public void encodeItemTimestamp(final long realTimestamp) {
        this.encode4(KoreanDateUtil.getItemTimestamp(realTimestamp));
    }
    
    /**
     * Writes an int64 tempban timestamp to the client.
     * 100 nsseconds from 1/1/1601 -> 1/1/1970
     * @param realTimestamp 
     */
    @Override
    public void encodeTempBanTimestamp(final long realTimestamp) {
        this.encode8(KoreanDateUtil.getTempBanTimestamp(realTimestamp));
    }
}
