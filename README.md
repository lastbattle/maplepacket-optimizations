# Maple Packet Codec Optimisation

Benchmark-driven optimisation work for the Maple packet encryption, input, and output stack in OdinMS MapleStory encryption library.

Using reward-guided iterative optimization loops, guided by GPT-5.5 Pro and Claude Opus 4.8, on the original MapleStory encryption library from OdinMS-based MapleStory sources to test whether the most obvious performance gains had already been exhausted in the library.
To my surprise, there was still substantial low-hanging fruit. The resulting optimization pass delivered major speed-ups across encryption, memory/ GC, input handling, output handling, and the full send pipeline without changing encrypted output, packet structure, or IO semantics. 

## Overview

The affected areas are:

- packet output via `GenericLittleEndianWriter`
- packet input via `GenericLittleEndianAccessor` and `ByteInputStream` implementations
- Maple custom packet encryption via `MapleCustomEncryption`
- Maple AES/OFB packet encryption via `MapleAESOFB`
- standalone packet codec benchmarking via `PacketCodecBenchmark`

## Goals

- Increase throughput on packet encode, decode, encrypt, decrypt, and full send-pipeline paths.
- Preserve exact encrypted output for known payloads.
- Preserve AES/OFB and custom encryption round-trip behaviour.
- Fix the discovered 64-bit decode correctness issue before measuring performance.
- Keep changes local and low-risk, avoiding broader architectural rewrites.

## Performance Summary

Compared with the corrected baseline, the final keeper set produced the following improvements:

| Benchmark | Baseline best ops/s | Final avg ops/s | Improvement |
|---|---:|---:|---:|
| writer primitives | 1,762,965 | 13,776,166 | 7.81x |
| writer buffer 1460 | 8,885,047 | 11,546,920 | 1.30x |
| accessor primitives | 22,960,743 | 23,921,469 | 1.04x |
| accessor buffer 1460 | 12,265,128 | 11,670,174 | 0.95x |
| custom encrypt 512 | 242,129 | 542,256 | 2.24x |
| custom decrypt 512 | 123,690 | 504,756 | 4.08x |
| custom mixed roundtrip | 30,951 | 68,912 | 2.23x |
| AES crypt 512 | 647,740 | 2,083,894 | 3.22x |
| AES crypt mixed | 174,562 | 527,807 | 3.02x |
| full send pipeline mixed | 44,446 | 123,392 | 2.78x |
| MINA handoff 1460 | 4,204,041 copy path | 10,822,299 view path | 2.57x |
| fused send pipeline, isolated | 114,488 unfused | 123,723 fused | 1.08x |

<img width="1390" height="789" alt="image" src="https://github.com/user-attachments/assets/8c10c68f-cf2d-45fb-821a-1489d2b7aa6d" />



## Benchmark Methodology

Benchmark helper:

```text
benchmarks/PacketCodecBenchmark.java
```

Run shape:

- warmup: `1000 ms` per benchmark
- measurement: `2000 ms` per repeat
- final run: `5 repeats`
- earlier iteration runs: `3 repeats`
- packet sizes: `32`, `128`, `512`, `1460`, `8192`

The benchmark validates correctness before reporting throughput:

- custom encryption/decryption round trips
- AES/OFB round trips
- `encode8`/`decode8` round trip
- SHA-256 digest checks for known custom-encrypted and AES-encrypted 512-byte payloads

### Running the Benchmark

```powershell
mvn -f .\pom.xml -DskipTests compile
$mina = "$env:USERPROFILE\.m2\repository\org\apache\mina\mina-core\2.2.9\mina-core-2.2.9.jar"
javac -cp ".\target\classes;$mina" -d '.\target\benchmark-classes' '.\benchmarks\PacketCodecBenchmark.java'
java -Xms1g -Xmx1g -cp ".\target\classes;.\target\benchmark-classes;$mina" PacketCodecBenchmark --repeats=5
```

### Running Tests

```powershell
mvn -f .\pom.xml test
```

Expected result:

```text
BUILD SUCCESS
```

## Implementation Highlights

### Mandatory MINA Encoder and Decoder Integration

`MaplePacketEncoder.java` and `MaplePacketDecoder.java` are application code and
are not included in this library. They **MUST** follow the packet layout below.
`MapleAESOFB.crypt(...)` and `MapleCustomEncryption.encryptData(...)` preserve
the first four bytes as the Maple packet header. Using the former payload-only
integration will misalign the AES keystream and encryption will fail.

`MaplePacketEncoder.java`:

```java
@Override
public void encode(final IoSession session, final Object message,
        final ProtocolEncoderOutput out) throws Exception {
    final Client client = (Client) session.getAttribute(Client.SOCKET_CLIENT_STATE_KEY);

    if (client != null) {
        final MapleAESOFB send_crypto = client.getSendCrypto();
        out.write(IoBuffer.wrap(send_crypto.encryptPacket((byte[]) message)));
    } else { // no client object created yet, send unencrypted (hello)
        out.write(IoBuffer.wrap((byte[]) message));
    }
}
```

`MaplePacketDecoder.java`:

```java
@Override
protected boolean doDecode(IoSession session, IoBuffer in,
        ProtocolDecoderOutput out) throws Exception {
    final DecoderState decoderState =
            (DecoderState) session.getAttribute(DECODER_STATE_KEY);
    final Client client =
            (Client) session.getAttribute(Client.SOCKET_CLIENT_STATE_KEY);
    if (client == null) {
        return false;
    }

    client.getRecvPacketReentrantLock().lock();
    try {
        if (decoderState.packetlength == -1) {
            if (in.remaining() < 4) {
                return false;
            }
            final int packetHeader = in.getInt();
            if (!client.getReceiveCrypto().checkPacket(packetHeader)) {
                session.closeNow();
                return false;
            }
            decoderState.packetlength = MapleAESOFB.getPacketLength(packetHeader);
        }
        if (in.remaining() < decoderState.packetlength) {
            return false;
        }

        final byte encryptedPacket[] = new byte[decoderState.packetlength + 4];
        in.get(encryptedPacket, 4, decoderState.packetlength);
        decoderState.packetlength = -1;

        client.getReceiveCrypto().crypt(encryptedPacket);
        final byte packet[] = new byte[encryptedPacket.length - 4];
        System.arraycopy(encryptedPacket, 4, packet, 0, packet.length);
        MapleCustomEncryption.decryptData(packet);
        out.write(packet);
    } finally {
        client.getRecvPacketReentrantLock().unlock();
    }
    return true;
}
```

### MINA 2.2 Handoff

`COutPacket.getIoBuffer()` exposes a bounded, zero-copy view of the packet's
backing array for direct use with `IoSession.write(...)`. Existing callers can
continue using `getPacket()`, which retains its snapshot/copy semantics.

Use the expected-size constructor to avoid growth copies while encoding:

```java
COutPacket packet = new COutPacket(expectedSize);
// encode packet fields
session.write(packet.getIoBuffer());
```

Treat `getIoBuffer()` as the terminal handoff; do not append to the packet after
obtaining the view. In the full five-repeat benchmark, the 1460-byte view path
averaged 10.89M ops/s versus 4.24M ops/s for `getPacket()` plus `IoBuffer.wrap`,
a 2.57x improvement that removes one full-packet allocation and copy.

Run only this benchmark with:

```powershell
java -cp ".\target\classes;.\target\benchmark-classes;$env:USERPROFILE\.m2\repository\org\apache\mina\mina-core\2.2.9\mina-core-2.2.9.jar" PacketCodecBenchmark --handoff-only --repeats=5
```

### Output Writer

`GenericLittleEndianWriter` was updated to reduce per-byte overhead:

- `encodeBuffer(byte[])` writes the whole byte array to `ByteArrayOutputStream` in one call.
- `writeZeroBytes(int)` writes chunks from a static zero buffer instead of looping byte-by-byte.
- `encode2`, `encode4`, and `encode8` write directly to the backing stream instead of calling back through `encode1`.

A per-writer scratch buffer for primitive writes was tested but rejected because it was slower and added mutable state.

### Input Accessor and Streams

`ByteInputStream` now exposes default bulk and primitive read methods:

```text
readBytes
readShortLE
readShortBE
readIntLE
readIntBE
readLongLE
readLongBE
skip
```

`ByteArrayByteStream` overrides these methods with array-indexed implementations, reducing repeated EOF checks and repeated `available()` calls. This path matters most for decoded network packets because the packets are already resident in byte arrays.

`InputStreamByteStream` and `RandomAccessByteStream` also gained bulk `readBytes` and `skip` implementations.

`GenericLittleEndianAccessor` now delegates primitive, buffer, and skip operations to the underlying stream and includes the corrected 64-bit little-endian and big-endian decode behaviour.

### Custom Encryption [Shanda -- official name]

`MapleCustomEncryption` was optimised by replacing repeated 8-bit rotate operations with lookup tables:

- static rotate tables replace repeated `BitTools.rollLeft` and `BitTools.rollRight` calls
- fixed rows are cached for common rotations:
  - left 3
  - left 4
  - right 3
  - right 4
- byte overflow behaviour remains explicit with `& 0xFF`

Two custom-encryption trials were rejected:

- splitting the six alternating passes into helper methods slowed direct encrypt/decrypt paths
- maintaining a rolling rotation index improved one narrow decrypt metric but lowered send-side encryption and full pipeline throughput

### AES/OFB Encryption

`MapleAESOFB` was optimised by reducing allocation and provider overhead in the AES stream path:

- cipher initialisation now uses explicit `AES/ECB/NoPadding`
- per-chunk IV expansion allocation was replaced with a reusable 16-byte IV repeat buffer
- repeated `doFinal()` calls were replaced with `cipher.update(...)` into reusable 16-byte buffers
- overlapping input/output blocks were avoided by using two stream buffers and swapping references
- the 16-byte XOR path for full AES blocks was unrolled
- a temporary 2-byte allocation in `checkPacket` was removed
- the four fixed IV-mixing steps are unrolled

Rejected AES trials:

- reusing IV output storage and copying back was slower
- swapping reusable IV buffers was still slower than the simpler `getNewIv` path in this benchmark

### Allocation-aware Send Pipeline

`MapleCustomEncryption.encryptData(...)` and
`MapleAESOFB.cryptPacketData(...)` encrypt directly after the four-byte Maple
header. The final packet is allocated once, populated once, and encrypted in
place. This removes the temporary payload clone and the final payload copy.

```java
byte[] encryptedPacket = aes.encryptPacket(payload);
session.write(IoBuffer.wrap(encryptedPacket));
```

| Mixed send metric | Previous pipeline | Fused pipeline | Change |
|---|---:|---:|---:|
| allocation | 4,193.6 bytes/op | 2,112.0 bytes/op | -49.6% |
| constrained-heap collections | 26 | 13 | -50.0% |
| constrained-heap GC time | 9 ms | 4 ms | -55.6% |
| constrained-heap throughput | 114,846 ops/s | 124,105 ops/s | +8.1% |

Allocation measurements use `ThreadMXBean` over 100,000 operations. GC results
use 500,000 operations with G1 and a fixed 128 MiB heap. Run them with
`--allocations` and `--gc`, respectively.

## Iteration Results

Throughput is reported in `ops/s`. Values are the best value per run unless marked as an average. The original baseline could not be timed because `decode8` failed correctness verification. The first timed baseline is after the `decode8` fix and before performance changes.

| Iteration | Change Set | writer primitives | writer buffer 1460 | accessor primitives | accessor buffer 1460 | custom encrypt 512 | custom decrypt 512 | custom mixed roundtrip | AES 512 | AES mixed | full send mixed | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 0 | Original code | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | Failed `decode8` correctness check |
| 1 | Corrected `decode8` baseline | 1,762,965 | 8,885,047 | 22,960,743 | 12,265,128 | 242,129 | 123,690 | 30,951 | 647,740 | 174,562 | 44,446 | Baseline |
| 2 | Bulk writer/accessor, int rotate loops, AES no-padding/update | 4,297,839 | 12,231,786 | 22,965,983 | 11,936,601 | 285,163 | 296,451 | 37,472 | 1,273,430 | 309,872 | 50,286 | Kept |
| 3 | Split custom encryption passes into helpers | 14,398,635 | 12,392,189 | 22,987,977 | 9,646,730 | 183,706 | 199,256 | 34,312 | 1,269,528 | 320,274 | 55,487 | Rejected: custom encryption regressed |
| 4 | Rotate lookup tables | 15,699,882 | 12,568,966 | 23,076,169 | 12,434,889 | 465,705 | 499,040 | 60,826 | 1,271,329 | 320,046 | 84,984 | Kept |
| 5 | Cached fixed rotate rows | 4,342,138 | 12,672,491 | 23,089,996 | 12,545,444 | 575,985 | 516,775 | 70,723 | 1,274,671 | 319,211 | 102,862 | Kept |
| 6 | AES full-block XOR unroll | 15,298,989 | 12,481,396 | 23,118,202 | 12,408,478 | 576,163 | 512,753 | 70,652 | 1,331,646 | 331,034 | 104,766 | Kept |
| 7 | AES two-buffer update, no input/output overlap | 15,234,873 | 12,352,778 | 23,087,992 | 12,908,243 | 575,985 | 511,033 | 70,514 | 1,972,801 | 508,564 | 117,336 | Kept |
| 8 | Rolling variable rotate index | 15,141,215 | 12,515,496 | 23,095,689 | 12,454,377 | 539,937 | 527,322 | 67,844 | 1,935,755 | 508,714 | 108,257 | Rejected: send/full pipeline regressed |
| 9 | Writer scratch buffer | 14,107,189 | 12,224,080 | 23,030,837 | 12,446,687 | 576,629 | 513,039 | 70,492 | 1,983,265 | 508,199 | 117,554 | Rejected: writer path not better |
| 10 | Array-backed primitive stream reads | 15,184,209 | 12,333,979 | 24,792,198 | 12,517,142 | 575,304 | 539,696 | 72,146 | 1,920,267 | 475,394 | 117,381 | Kept |
| 11 | Reusable IV copy-back | 14,458,906 | 12,437,640 | 24,788,800 | 12,722,858 | 576,279 | 515,488 | 70,746 | 1,882,838 | 488,717 | 116,531 | Rejected: AES regressed |
| 12 | Reusable IV swap | 4,335,044 | 12,658,614 | 24,780,909 | 12,658,419 | 576,025 | 540,594 | 72,441 | 1,892,056 | 491,556 | 116,724 | Rejected: AES still below kept version |
| 13 | Final keeper set, 5-repeat average | 15,106,089 avg | 12,403,336 avg | 24,598,713 avg | 12,513,157 avg | 573,783 avg | 512,506 avg | 70,601 avg | 1,964,219 avg | 503,898 avg | 116,832 avg | Plateau |
| 14 | MINA zero-copy packet handoff, 5-repeat average | 14,420,211 avg | 11,794,384 avg | 22,241,918 avg | 10,213,962 avg | 508,082 avg | 507,131 avg | 69,744 avg | 1,892,671 avg | 504,479 avg | 116,423 avg | Kept: 1460-byte handoff increased from 5,128,690 to 9,954,966 avg ops/s (1.94x) |
| 15.1 | Allocation baseline | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 1,959,458 avg | 503,151 avg | 113,363 avg | Baseline: 552.0 AES bytes/op, 2,105.6 AES-mixed bytes/op, 4,193.6 send bytes/op |
| 15.2 | Update IV in place | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 1,874,614 avg | 488,416 avg | 112,410 avg | Rejected: removed 24 bytes per packet but regressed AES throughput |
| 15.3 | Store IV in AES scratch block | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 1,945,197 avg | 504,723 avg | 114,742 avg | Rejected: fixed-heap AES baseline was 2,085,304 ops/s |
| 15.4 | Reuse two dedicated IV arrays | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 1,952,151 avg | 493,152 avg | 104,754 avg | Rejected: full pipeline regressed materially |
| 15.5 | Unroll four IV-mixing calls | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 2,094,050 avg | 545,261 avg | 114,691 avg | Kept: improved throughput without changing allocation |
| 15.6 | Generic offset-based encryption | not measured | not measured | not measured | not measured | 456,906 avg | not measured | not measured | 1,889,011 avg | not measured | 92,764 avg | Rejected: custom baseline was 561,294, AES baseline 2,104,799, and previous pipeline 91,882 ops/s |
| 15.7 | Fixed four-byte-header encryption loops | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 124,826 avg | Kept: previous pipeline 114,287 ops/s; allocation reduced to 2,112.0 bytes/op |
| 15.8 | Public `encryptPacket(payload)` fused API | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 123,868 avg | Kept: previous pipeline 114,019 ops/s; API-level validation of fused path |
| 15.9 | Constrained 128 MiB G1 run, 500,000 sends | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | 124,105 avg | Kept: previous pipeline 114,846 ops/s, collections 26 → 13, GC time 9 ms → 4 ms |
| 15.10 | Final packet-aware API, 5-repeat full suite | 13,776,166 avg | 11,546,920 avg | 23,921,469 avg | 11,670,174 avg | 542,256 avg | 504,756 avg | 68,912 avg | 2,083,894 avg | 527,807 avg | 123,392 avg | Final full-suite results after `encryptData()` became packet-aware; isolated fused pipeline averaged 123,723 versus 114,488 unfused |

<img width="1390" height="690" alt="image" src="https://github.com/user-attachments/assets/528c9474-40b5-4ea1-a40c-7cbb3b318ea1" />

## Plateau Decision

The throughput and allocation plateau was reached after iteration 15. Three IV-reuse layouts removed another 24 bytes per send but regressed AES throughput, so they were rejected. Generic offset encryption also halved temporary allocation but slowed the send path; fixed four-byte-header loops recovered the throughput and retained the allocation reduction.

The final keeper set is the fastest stable combination measured with exact output validation.

## Validation

The following validation was completed:

- Maven test suite passed with `BUILD SUCCESS`
- benchmark verified AES/custom encryption output digests
- benchmark verified custom encryption and AES round trips
- benchmark verified `encode8`/`decode8` round trip
- checked for misplaced `.class` files under module `src`; none were found

## Remaining Notes

- `PacketCodecBenchmark` is a standalone helper rather than a Maven/JMH benchmark. It is intentionally small and dependency-free.
- `ByteArrayByteStream` is the most important accessor path for decoded network packets in this codebase.
- AES speed is still bounded by one AES provider call per 16-byte stream block.
- Further AES gains would likely require a different AES backend or larger architectural changes rather than small local edits.

## Summary

This optimisation pass improved the Maple packet codec's practical send and encryption paths while preserving exact encrypted output. The largest gains came from direct primitive writes, custom encryption rotate lookup tables, AES/OFB buffer reuse, and safer array-backed primitive reads.
