# Maple Packet Codec Optimisation

Benchmark-driven optimisation work for the Maple packet encryption, input, and output stack in OdinMS MapleStory encryption library.

This update focuses on improving hot paths in the packet codec without changing encrypted output, packet structure, or IO semantics. The benchmark suite validates AES/OFB output, custom encryption output, round trips, and 64-bit primitive decoding before reporting throughput.

## Background

Using reward-guided iterative optimization loops, guided by GPT-5.5 Pro and Claude Opus 4.8, on the original MapleStory encryption library from OdinMS-based MapleStory sources to test whether the most obvious performance gains had already been exhausted in the library.
To my surprise, there was still substantial low-hanging fruit. The resulting optimization pass delivered major speed-ups across encryption, input handling, output handling, and the full send pipeline.

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
| writer primitives | 1,762,965 | 15,106,089 | 8.57x |
| writer buffer 1460 | 8,885,047 | 12,403,336 | 1.40x |
| accessor primitives | 22,960,743 | 24,598,713 | 1.07x |
| accessor buffer 1460 | 12,265,128 | 12,513,157 | 1.02x |
| custom encrypt 512 | 242,129 | 573,783 | 2.37x |
| custom decrypt 512 | 123,690 | 512,506 | 4.14x |
| custom mixed roundtrip | 30,951 | 70,601 | 2.28x |
| AES crypt 512 | 647,740 | 1,964,219 | 3.03x |
| AES crypt mixed | 174,562 | 503,898 | 2.89x |
| full send pipeline mixed | 44,446 | 116,832 | 2.63x |

<img width="1188" height="690" alt="image" src="https://github.com/user-attachments/assets/4f1b8119-3de1-4761-aeb2-7240d3890e87" />

<img width="1389" height="790" alt="image" src="https://github.com/user-attachments/assets/a0e57f5d-baaf-4600-ba45-98db033f6261" />


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
javac -cp '.\target\classes' -d '.\target\benchmark-classes' '.\benchmarks\PacketCodecBenchmark.java'
java -cp '.\target\classes;.\target\benchmark-classes' PacketCodecBenchmark --repeats=5
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

### Custom Encryption

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

Rejected AES trials:

- reusing IV output storage and copying back was slower
- swapping reusable IV buffers was still slower than the simpler `getNewIv` path in this benchmark

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

## Plateau Decision

The benchmark plateau was reached after iteration 10. Later trials targeted IV allocation reduction, rolling rotate index handling, and writer primitive scratch buffering. These changes either regressed the full send pipeline or only moved noise-level numbers while adding complexity.

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
