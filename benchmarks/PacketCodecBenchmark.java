import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntConsumer;
import game.packets.MapleAESOFB;
import game.packets.MapleCustomEncryption;
import game.packets.input.ByteArrayByteStream;
import game.packets.input.GenericLittleEndianAccessor;
import game.packets.output.GenericLittleEndianWriter;
import game.packets.output.COutPacket;
import org.apache.mina.core.buffer.IoBuffer;

public final class PacketCodecBenchmark {

    private static final int WARMUP_MILLIS = 1_000;
    private static final int MEASURE_MILLIS = 2_000;
    private static final int[] PACKET_SIZES = {32, 128, 512, 1460, 8192};
    private static final byte[] IV = {0x4d, 0x23, (byte) 0xc7, 0x2b};
    private static final short VERSION = 83;
    private static final String EXPECTED_CUSTOM_512_SHA256 = "2d688dc792c90bde94f4d090060733efbced1794ec0d748236a056a414e96235";
    private static final String EXPECTED_AES_512_SHA256 = "79f299d804b39e3b1f0114b0fe64cec5f5df65dafd5f4185bd36329c18cf86bb";
    private static final byte[] STRING_BYTES = createPayload(24, 0x51f15e);
    private static final byte[] SMALL_BUFFER = createPayload(64, 0x51f15f);
    private static final byte[][] PAYLOADS = createPayloads();
    private static final byte[] ENCRYPTED_512 = encrypted512();
    private static final MapleAESOFB AES_512 = new MapleAESOFB(IV.clone(), VERSION, false);
    private static final MapleAESOFB AES_MIXED = new MapleAESOFB(IV.clone(), VERSION, false);
    private static final MapleAESOFB AES_FULL_PIPELINE = new MapleAESOFB(IV.clone(), VERSION, false);
    private static final MapleAESOFB AES_FUSED_PIPELINE = new MapleAESOFB(IV.clone(), VERSION, false);

    private static long blackhole;
    private static Object objectBlackhole;

    private PacketCodecBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        int repeats = 5;
        boolean handoffOnly = false;
        boolean allocationsOnly = false;
        boolean aesOnly = false;
        boolean pipelineOnly = false;
        boolean gcOnly = false;
        for (String arg : args) {
            if (arg.startsWith("--repeats=")) {
                repeats = Integer.parseInt(arg.substring("--repeats=".length()));
            } else if (arg.equals("--handoff-only")) {
                handoffOnly = true;
            } else if (arg.equals("--allocations")) {
                allocationsOnly = true;
            } else if (arg.equals("--aes-only")) {
                aesOnly = true;
            } else if (arg.equals("--pipeline-only")) {
                pipelineOnly = true;
            } else if (arg.equals("--gc")) {
                gcOnly = true;
            }
        }

        verifyMinaHandoff();
        if (allocationsOnly) {
            verifyCorrectness();
            benchmarkAllocations();
            return;
        }
        if (handoffOnly) {
            benchmark("mina-handoff-copy-1460", repeats, PacketCodecBenchmark::minaHandoffCopy1460);
            benchmark("mina-handoff-view-1460", repeats, PacketCodecBenchmark::minaHandoffView1460);
            return;
        }

        if (aesOnly) {
            verifyCorrectness();
            benchmark("aes-crypt-512", repeats, PacketCodecBenchmark::aesCrypt512);
            benchmark("aes-crypt-mixed", repeats, PacketCodecBenchmark::aesCryptMixed);
            benchmark("full-send-pipeline-mixed", repeats, PacketCodecBenchmark::fullSendPipelineMixed);
            return;
        }

        if (pipelineOnly) {
            verifyCorrectness();
            benchmark("full-send-pipeline-mixed", repeats, PacketCodecBenchmark::fullSendPipelineMixed);
            benchmark("fused-send-pipeline-mixed", repeats, PacketCodecBenchmark::fusedSendPipelineMixed);
            return;
        }

        if (gcOnly) {
            verifyCorrectness();
            gcBenchmark("full-send-pipeline-mixed", PacketCodecBenchmark::fullSendPipelineMixed);
            gcBenchmark("fused-send-pipeline-mixed", PacketCodecBenchmark::fusedSendPipelineMixed);
            return;
        }

        verifyCorrectness();

        System.out.println("Packet codec benchmark");
        System.out.println("warmupMillis=" + WARMUP_MILLIS + " measureMillis=" + MEASURE_MILLIS + " repeats=" + repeats);
        benchmark("writer-primitives", repeats, PacketCodecBenchmark::writerPrimitives);
        benchmark("writer-buffer-1460", repeats, PacketCodecBenchmark::writerBuffer1460);
        benchmark("mina-handoff-copy-1460", repeats, PacketCodecBenchmark::minaHandoffCopy1460);
        benchmark("mina-handoff-view-1460", repeats, PacketCodecBenchmark::minaHandoffView1460);
        benchmark("accessor-primitives", repeats, PacketCodecBenchmark::accessorPrimitives);
        benchmark("accessor-buffer-1460", repeats, PacketCodecBenchmark::accessorBuffer1460);
        benchmark("custom-encrypt-512", repeats, PacketCodecBenchmark::customEncrypt512);
        benchmark("custom-decrypt-512", repeats, PacketCodecBenchmark::customDecrypt512);
        benchmark("custom-roundtrip-mixed", repeats, PacketCodecBenchmark::customRoundTripMixed);
        benchmark("aes-crypt-512", repeats, PacketCodecBenchmark::aesCrypt512);
        benchmark("aes-crypt-mixed", repeats, PacketCodecBenchmark::aesCryptMixed);
        benchmark("full-send-pipeline-mixed", repeats, PacketCodecBenchmark::fullSendPipelineMixed);
        benchmark("fused-send-pipeline-mixed", repeats, PacketCodecBenchmark::fusedSendPipelineMixed);
        System.out.println("blackhole=" + blackhole);
    }

    private static void benchmark(String name, int repeats, IntConsumer operation) {
        runFor(operation, WARMUP_MILLIS);

        double best = 0.0d;
        double total = 0.0d;
        for (int i = 0; i < repeats; i++) {
            Result result = runFor(operation, MEASURE_MILLIS);
            double opsPerSecond = result.operations * 1_000_000_000.0d / result.nanos;
            best = Math.max(best, opsPerSecond);
            total += opsPerSecond;
        }

        System.out.printf(Locale.ROOT, "%-28s best=%12.2f ops/s avg=%12.2f ops/s%n", name, best, total / repeats);
    }

    private static Result runFor(IntConsumer operation, int millis) {
        long end = System.nanoTime() + millis * 1_000_000L;
        long operations = 0;
        int index = 0;

        long start = System.nanoTime();
        do {
            operation.accept(index++);
            operations++;
        } while (System.nanoTime() < end);

        return new Result(operations, System.nanoTime() - start);
    }

    private static void writerPrimitives(int index) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(192);
        GenericLittleEndianWriter writer = new GenericLittleEndianWriter(baos);
        writer.encode2(0x1234);
        writer.encode4(0x12345678);
        writer.encode8(0x0123456789ABCDEFL + index);
        writer.encodeBoolean((index & 1) == 0);
        writer.encodeString("Packet-" + (index & 0x3f));
        writer.encodeBuffer(STRING_BYTES);
        writer.writeZeroBytes(12);
        blackhole += baos.size();
    }

    private static void writerBuffer1460(int index) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1460);
        GenericLittleEndianWriter writer = new GenericLittleEndianWriter(baos);
        writer.encodeBuffer(PAYLOADS[3]);
        blackhole += baos.size() + index;
    }

    private static void benchmarkAllocations() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocation measurement is not supported by this JVM");
        }
        bean.setThreadAllocatedMemoryEnabled(true);

        System.out.println("Packet codec allocation benchmark");
        allocationBenchmark(bean, "writer-primitives", PacketCodecBenchmark::writerPrimitives);
        allocationBenchmark(bean, "writer-buffer-1460", PacketCodecBenchmark::writerBuffer1460);
        allocationBenchmark(bean, "mina-handoff-copy-1460", PacketCodecBenchmark::minaHandoffCopy1460);
        allocationBenchmark(bean, "mina-handoff-view-1460", PacketCodecBenchmark::minaHandoffView1460);
        allocationBenchmark(bean, "accessor-primitives", PacketCodecBenchmark::accessorPrimitives);
        allocationBenchmark(bean, "accessor-buffer-1460", PacketCodecBenchmark::accessorBuffer1460);
        allocationBenchmark(bean, "custom-encrypt-512", PacketCodecBenchmark::customEncrypt512);
        allocationBenchmark(bean, "custom-decrypt-512", PacketCodecBenchmark::customDecrypt512);
        allocationBenchmark(bean, "custom-roundtrip-mixed", PacketCodecBenchmark::customRoundTripMixed);
        allocationBenchmark(bean, "aes-crypt-512", PacketCodecBenchmark::aesCrypt512);
        allocationBenchmark(bean, "aes-crypt-mixed", PacketCodecBenchmark::aesCryptMixed);
        allocationBenchmark(bean, "full-send-pipeline-mixed", PacketCodecBenchmark::fullSendPipelineMixed);
        allocationBenchmark(bean, "fused-send-pipeline-mixed", PacketCodecBenchmark::fusedSendPipelineMixed);
    }

    private static void allocationBenchmark(
            com.sun.management.ThreadMXBean bean, String name, IntConsumer operation) {
        final int warmupIterations = 20_000;
        final int measureIterations = 100_000;
        for (int i = 0; i < warmupIterations; i++) {
            operation.accept(i);
        }

        long threadId = Thread.currentThread().threadId();
        long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < measureIterations; i++) {
            operation.accept(i);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - allocatedBefore;
        System.out.printf(Locale.ROOT, "%-28s %10.2f bytes/op%n",
                name, (double) allocated / measureIterations);
    }

    private static void gcBenchmark(String name, IntConsumer operation) {
        final int warmupIterations = 20_000;
        final int measureIterations = 500_000;
        for (int i = 0; i < warmupIterations; i++) {
            operation.accept(i);
        }
        System.gc();

        long collectionsBefore = gcCollectionCount();
        long gcMillisBefore = gcCollectionTime();
        long start = System.nanoTime();
        for (int i = 0; i < measureIterations; i++) {
            operation.accept(i);
        }
        long elapsed = System.nanoTime() - start;
        long collections = gcCollectionCount() - collectionsBefore;
        long gcMillis = gcCollectionTime() - gcMillisBefore;
        double opsPerSecond = measureIterations * 1_000_000_000.0d / elapsed;
        System.out.printf(Locale.ROOT,
                "%-28s %12.2f ops/s  collections=%d  gcMillis=%d%n",
                name, opsPerSecond, collections, gcMillis);
    }

    private static long gcCollectionCount() {
        long count = 0;
        for (java.lang.management.GarbageCollectorMXBean bean
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value >= 0) {
                count += value;
            }
        }
        return count;
    }

    private static long gcCollectionTime() {
        long millis = 0;
        for (java.lang.management.GarbageCollectorMXBean bean
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionTime();
            if (value >= 0) {
                millis += value;
            }
        }
        return millis;
    }

    private static void minaHandoffCopy1460(int index) {
        COutPacket packet = new COutPacket(1460);
        packet.encodeBuffer(PAYLOADS[3]);
        IoBuffer buffer = IoBuffer.wrap(packet.getPacket());
        blackhole += buffer.get(index & (buffer.limit() - 1)) & 0xff;
        objectBlackhole = buffer;
    }

    private static void minaHandoffView1460(int index) {
        COutPacket packet = new COutPacket(1460);
        packet.encodeBuffer(PAYLOADS[3]);
        IoBuffer buffer = packet.getIoBuffer();
        blackhole += buffer.get(index & (buffer.limit() - 1)) & 0xff;
        objectBlackhole = buffer;
    }

    private static void accessorPrimitives(int index) {
        byte[] packet = new byte[128];
        int pos = 0;
        for (int i = 0; i < 16; i++) {
            int value = 0x10203040 + index + i;
            packet[pos++] = (byte) value;
            packet[pos++] = (byte) (value >>> 8);
            packet[pos++] = (byte) (value >>> 16);
            packet[pos++] = (byte) (value >>> 24);
        }

        GenericLittleEndianAccessor accessor = new GenericLittleEndianAccessor(new ByteArrayByteStream(packet));
        long sum = 0;
        for (int i = 0; i < 16; i++) {
            sum += accessor.decode4();
        }
        blackhole += sum;
    }

    private static void accessorBuffer1460(int index) {
        GenericLittleEndianAccessor accessor = new GenericLittleEndianAccessor(new ByteArrayByteStream(PAYLOADS[3]));
        byte[] bytes = accessor.decodeBuffer(PAYLOADS[3].length);
        blackhole += bytes[index & (bytes.length - 1)] & 0xff;
    }

    private static void customEncrypt512(int index) {
        byte[] data = framePayload(PAYLOADS[2]);
        MapleCustomEncryption.encryptData(data);
        blackhole += data[4 + (index & (PAYLOADS[2].length - 1))] & 0xff;
    }

    private static void customDecrypt512(int index) {
        byte[] data = ENCRYPTED_512.clone();
        MapleCustomEncryption.decryptData(data);
        blackhole += data[index & (data.length - 1)] & 0xff;
    }

    private static void customRoundTripMixed(int index) {
        byte[] source = PAYLOADS[index % PAYLOADS.length];
        byte[] packet = framePayload(source);
        MapleCustomEncryption.encryptData(packet);
        byte[] data = Arrays.copyOfRange(packet, 4, packet.length);
        MapleCustomEncryption.decryptData(data);
        blackhole += data[index & (data.length - 1)] & 0xff;
    }

    private static void aesCrypt512(int index) {
        byte[] data = framePayload(PAYLOADS[2]);
        AES_512.crypt(data);
        blackhole += data[4 + (index & (PAYLOADS[2].length - 1))] & 0xff;
    }

    private static void aesCryptMixed(int index) {
        byte[] source = PAYLOADS[index % PAYLOADS.length];
        byte[] data = framePayload(source);
        AES_MIXED.crypt(data);
        blackhole += data[4 + (index & (source.length - 1))] & 0xff;
    }

    private static void fullSendPipelineMixed(int index) {
        byte[] source = PAYLOADS[index % PAYLOADS.length];
        byte[] data = framePayload(source);
        byte[] packet = AES_FULL_PIPELINE.getPacketHeaderEx(source.length);
        MapleCustomEncryption.encryptData(data);
        AES_FULL_PIPELINE.crypt(data);
        System.arraycopy(data, 4, packet, 4, source.length);
        blackhole += packet[index & (packet.length - 1)] & 0xff;
    }

    private static void fusedSendPipelineMixed(int index) {
        byte[] source = PAYLOADS[index % PAYLOADS.length];
        byte[] packet = AES_FUSED_PIPELINE.encryptPacket(source);
        blackhole += packet[index & (packet.length - 1)] & 0xff;
    }

    private static void verifyCorrectness() {
        verifyMinaHandoff();
        verifyPacketEncryption();

        for (byte[] source : PAYLOADS) {
            byte[] packet = framePayload(source);
            MapleCustomEncryption.encryptData(packet);
            byte[] encrypted = Arrays.copyOfRange(packet, 4, packet.length);
            MapleCustomEncryption.decryptData(encrypted);
            if (!Arrays.equals(source, encrypted)) {
                throw new IllegalStateException("MapleCustomEncryption round trip failed for length " + source.length);
            }

            byte[] aesData = framePayload(source);
            MapleAESOFB first = new MapleAESOFB(IV.clone(), VERSION, false);
            first.crypt(aesData);
            MapleAESOFB second = new MapleAESOFB(IV.clone(), VERSION, false);
            second.crypt(aesData);
            if (!Arrays.equals(source, Arrays.copyOfRange(aesData, 4, aesData.length))) {
                throw new IllegalStateException("MapleAESOFB round trip failed for length " + source.length);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
        new GenericLittleEndianWriter(baos).encode8(0x0123456789ABCDEFL);
        GenericLittleEndianAccessor accessor = new GenericLittleEndianAccessor(new ByteArrayByteStream(baos.toByteArray()));
        long decoded = accessor.decode8();
        if (decoded != 0x0123456789ABCDEFL) {
            throw new IllegalStateException("decode8 round trip failed: decoded=0x" + Long.toHexString(decoded));
        }

        byte[] customPacket512 = framePayload(PAYLOADS[2]);
        MapleCustomEncryption.encryptData(customPacket512);
        byte[] custom512 = Arrays.copyOfRange(customPacket512, 4, customPacket512.length);
        assertDigest("custom-512", custom512, EXPECTED_CUSTOM_512_SHA256);

        byte[] aesPacket512 = framePayload(PAYLOADS[2]);
        new MapleAESOFB(IV.clone(), VERSION, false).crypt(aesPacket512);
        byte[] aes512 = Arrays.copyOfRange(aesPacket512, 4, aesPacket512.length);
        assertDigest("aes-512", aes512, EXPECTED_AES_512_SHA256);
    }

    private static void verifyPacketEncryption() {
        byte[] source = PAYLOADS[3];
        byte[] expectedCustomPacket = framePayload(source);
        MapleCustomEncryption.encryptData(expectedCustomPacket);
        byte[] expectedCustom = Arrays.copyOfRange(expectedCustomPacket, 4, expectedCustomPacket.length);
        byte[] expectedAesPacket = framePayload(source);
        new MapleAESOFB(IV.clone(), VERSION, false).crypt(expectedAesPacket);
        byte[] expectedAes = Arrays.copyOfRange(expectedAesPacket, 4, expectedAesPacket.length);
        byte[] packetCustom = new byte[source.length + 4];
        System.arraycopy(source, 0, packetCustom, 4, source.length);
        MapleCustomEncryption.encryptData(packetCustom);
        if (!Arrays.equals(expectedCustom, Arrays.copyOfRange(packetCustom, 4, packetCustom.length))) {
            throw new IllegalStateException("packet custom encryption output changed");
        }
        byte[] packetAes = new byte[source.length + 4];
        System.arraycopy(source, 0, packetAes, 4, source.length);
        new MapleAESOFB(IV.clone(), VERSION, false).crypt(packetAes);
        if (!Arrays.equals(expectedAes, Arrays.copyOfRange(packetAes, 4, packetAes.length))) {
            throw new IllegalStateException("packet AES output changed");
        }

        MapleAESOFB legacyCipher = new MapleAESOFB(IV.clone(), VERSION, false);
        byte[] legacyPayload = framePayload(source);
        byte[] legacyPacket = legacyCipher.getPacketHeaderEx(source.length);
        MapleCustomEncryption.encryptData(legacyPayload);
        legacyCipher.crypt(legacyPayload);
        System.arraycopy(legacyPayload, 4, legacyPacket, 4, source.length);
        byte[] fusedPacket = new MapleAESOFB(IV.clone(), VERSION, false).encryptPacket(source);
        if (!Arrays.equals(legacyPacket, fusedPacket)) {
            throw new IllegalStateException("fused packet output changed");
        }
    }

    private static void verifyMinaHandoff() {
        COutPacket output = new COutPacket(4);
        output.encodeBuffer(SMALL_BUFFER);
        IoBuffer minaBuffer = output.getIoBuffer();
        byte[] minaBytes = new byte[minaBuffer.remaining()];
        minaBuffer.get(minaBytes);
        if (!Arrays.equals(SMALL_BUFFER, minaBytes)) {
            throw new IllegalStateException("zero-copy MINA handoff changed packet contents or bounds");
        }
    }

    private static byte[] encrypted512() {
        byte[] packet = framePayload(PAYLOADS[2]);
        MapleCustomEncryption.encryptData(packet);
        return Arrays.copyOfRange(packet, 4, packet.length);
    }

    private static void assertDigest(String name, byte[] data, String expected) {
        String actual = sha256(data);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + " digest changed: expected=" + expected + " actual=" + actual);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[][] createPayloads() {
        byte[][] payloads = new byte[PACKET_SIZES.length][];
        for (int i = 0; i < PACKET_SIZES.length; i++) {
            payloads[i] = createPayload(PACKET_SIZES[i], 0x1234ABCD + i);
        }
        return payloads;
    }

    private static byte[] createPayload(int size, int seed) {
        byte[] bytes = new byte[size];
        Random random = new Random(seed);
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] framePayload(byte[] payload) {
        byte[] packet = new byte[payload.length + 4];
        System.arraycopy(payload, 0, packet, 4, payload.length);
        return packet;
    }

    private record Result(long operations, long nanos) {
    }
}
