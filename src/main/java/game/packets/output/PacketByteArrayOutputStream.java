package game.packets.output;

import java.io.ByteArrayOutputStream;
import org.apache.mina.core.buffer.IoBuffer;

/** Byte-array output with a zero-copy MINA handoff. */
final class PacketByteArrayOutputStream extends ByteArrayOutputStream {

    PacketByteArrayOutputStream(int initialCapacity) {
        super(initialCapacity);
    }

    IoBuffer asIoBuffer() {
        return IoBuffer.wrap(buf, 0, count);
    }
}
