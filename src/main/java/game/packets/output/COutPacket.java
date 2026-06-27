package game.packets.output;

import game.SendPacketOpcode;
import game.packets.input.ByteArrayByteStream;
import game.packets.input.GenericSeekableLittleEndianAccessor;
import game.packets.input.CInPacket;
import game.tools.HexTool;
import org.apache.mina.core.buffer.IoBuffer;

/**
 * Writes a maplestory-packet little-endian stream of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 352
 */
public class COutPacket extends GenericLittleEndianWriter {

    private static final int DEFAULT_CAPACITY = 20;
    private final PacketByteArrayOutputStream packetStream;

    /**
     * Constructor - initializes this stream with a default size.
     */
    public COutPacket() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Initializes this stream with enough room for the expected packet size.
     * Supplying an accurate size avoids backing-array growth and copies.
     *
     * @param initialCapacity expected packet size in bytes
     */
    public COutPacket(int initialCapacity) {
        packetStream = new PacketByteArrayOutputStream(initialCapacity);
        super.setByteOutputStream(packetStream);
    }

    /**
     * Constructor - initializes this stream with size <code>size</code>.
     *
     * @param packetHeader the packet header
     */
    public COutPacket(SendPacketOpcode packetHeader) {
        packetStream = new PacketByteArrayOutputStream(DEFAULT_CAPACITY);
        super.setByteOutputStream(packetStream);

        super.encode2(packetHeader.getValue());
    }

    /**
     * Gets a <code>MaplePacket</code> instance representing this
     * sequence of bytes.
     *
     * @return A <code>MaplePacket</code> with the bytes in this stream.
     */
    public byte[] getPacket() {
	return getBao().toByteArray();
    }

    /**
     * Returns a zero-copy view of this packet for {@code IoSession.write(...)}.
     * Treat this as the terminal handoff: later writes may grow the packet and
     * are not guaranteed to be visible through a previously returned view.
     *
     * @return a new MINA buffer view positioned at the first packet byte
     */
    public IoBuffer getIoBuffer() {
        return packetStream.asIoBuffer();
    }
    
    /**
     * Gets an array of int32 for representing this
     * @return 
     */
    public int[] getInt32Arrays() {
         CInPacket slea = new GenericSeekableLittleEndianAccessor(new ByteArrayByteStream(getPacket()));
        int[] ints = new int[(int) slea.available() / 4];
         
        int i = 0;
         while (slea.available() > 4) {
             ints[i] = slea.decode4();
             
             i++;
         }
         return ints;
    }

    public void printAll() {
	System.out.println(HexTool.toString(getBao().toByteArray()));
    }

    /**
     * Changes this packet into a human-readable hexadecimal stream of bytes.
     *
     * @return This packet as hex digits.
     */
    @Override
    public String toString() {
	return HexTool.toString(getBao().toByteArray());
    }
}
