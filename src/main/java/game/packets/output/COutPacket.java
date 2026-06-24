package game.packets.output;

import game.SendPacketOpcode;
import java.io.ByteArrayOutputStream;
import game.packets.input.ByteArrayByteStream;
import game.packets.input.GenericSeekableLittleEndianAccessor;
import game.packets.input.CInPacket;
import game.tools.HexTool;

/**
 * Writes a maplestory-packet little-endian stream of bytes.
 * 
 * @author Frz
 * @version 1.0
 * @since Revision 352
 */
public class COutPacket extends GenericLittleEndianWriter {

    /**
     * Constructor - initializes this stream with a default size.
     */
    public COutPacket() {
	ByteArrayOutputStream baos = new ByteArrayOutputStream(20);
	super.setByteOutputStream(baos);
    }

    /**
     * Constructor - initializes this stream with size <code>size</code>.
     *
     * @param packetHeader the packet header
     */
    public COutPacket(SendPacketOpcode packetHeader) {
	ByteArrayOutputStream baos = new ByteArrayOutputStream(20);
	super.setByteOutputStream(baos);

        super.encode2(packetHeader.getValue());
    }

    /**
     * Gets a <code>MaplePacket</code> instance representing this
     * sequence of bytes.
     *
     * @return A <code>MaplePacket</code> with the bytes in this stream.
     */
    public byte[] getPacket() {
	//MaplePacket packet = new ByteArrayMaplePacket(baos.toByteArray());
	//System.out.println("Packet to be sent:\n" +packet.toString() + "\n\n");
	return getBao().toByteArray();
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
