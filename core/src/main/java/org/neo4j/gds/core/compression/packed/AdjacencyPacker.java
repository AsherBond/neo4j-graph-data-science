/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.compression.packed;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.gds.api.compress.AdjacencyListBuilder;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compression.common.AdjacencyCompression;
import org.neo4j.gds.core.compression.common.MemoryTracker;
import org.neo4j.gds.mem.BitUtil;
import org.neo4j.internal.unsafe.UnsafeUtil;

import java.util.Arrays;

import static org.neo4j.gds.core.compression.common.VarLongEncoding.encodedVLongsSize;

public final class AdjacencyPacker {

    private AdjacencyPacker() {}

    public static long align(long length) {
        return BitUtil.align(length, AdjacencyPacking.BLOCK_SIZE);
    }

    public static int align(int length) {
        return (int) BitUtil.align(length, AdjacencyPacking.BLOCK_SIZE);
    }

    static final int BYTE_ARRAY_BASE_OFFSET = UnsafeUtil.arrayBaseOffset(byte[].class);

    public static long compress(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree
    ) {
        Arrays.sort(values, 0, length);
        return deltaCompress(allocator, slice, values, length, aggregation, degree);
    }

    private static long deltaCompress(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree
    ) {
        if (length > 0) {
            length = AdjacencyCompression.deltaEncodeSortedValues(values, 0, length, aggregation);
        }

        degree.setValue(length);

        return preparePacking(allocator, slice, values, length);
    }

    public static long compressWithVarLongTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree,
        MemoryTracker memoryTracker
    ) {
        Arrays.sort(values, 0, length);
        return deltaCompressWithVarLongTail(allocator, slice, values, length, aggregation, degree, memoryTracker);
    }

    static long compressWithPropertiesWithVarLongTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        MemoryTracker memoryTracker
    ) {
        return preparePackingWithVarLongTail(allocator, slice, values, length, memoryTracker);
    }

    private static long deltaCompressWithVarLongTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree,
        MemoryTracker memoryTracker
    ) {
        if (length > 0) {
            length = AdjacencyCompression.deltaEncodeSortedValues(values, 0, length, aggregation);
        }

        degree.setValue(length);

        return preparePackingWithVarLongTail(allocator, slice, values, length, memoryTracker);
    }

    private static long preparePacking(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length
    ) {
        int blocks = BitUtil.ceilDiv(length, AdjacencyPacking.BLOCK_SIZE);
        var header = new byte[blocks];

        long bytes = 0L;
        int offset = 0;
        int blockIdx = 0;

        for (; blockIdx < blocks - 1; blockIdx++, offset += AdjacencyPacking.BLOCK_SIZE) {
            int bits = bitsNeeded(values, offset, AdjacencyPacking.BLOCK_SIZE);
            bytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }
        // "tail" block, may be smaller than BLOCK_SIZE
        {
            int bits = bitsNeeded(values, offset, length - offset);
            bytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }

        return runPacking(allocator, slice, values, header, bytes);
    }

    private static long runPacking(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        byte[] header,
        long requiredBytes
    ) {
        assert values.length % AdjacencyPacking.BLOCK_SIZE == 0 : "values length must be a multiple of " + AdjacencyPacking.BLOCK_SIZE + ", but was " + values.length;

        long headerSize = header.length * Byte.BYTES;
        // we must add padding between the header and the data bytes
        // to avoid writing unaligned longs
        long alignedHeaderSize = BitUtil.align(headerSize, Long.BYTES);
        long fullSize = alignedHeaderSize + requiredBytes;
        // we must align to long because we write in terms of longs, not single bytes
        long alignedFullSize = BitUtil.align(fullSize, Long.BYTES);
        int allocationSize = Math.toIntExact(alignedFullSize);

        long adjacencyOffset = allocator.allocate(allocationSize, slice);

        Address address = slice.slice();
        long ptr = address.address() + slice.offset();
        long initialPtr = ptr;

        // write header
        UnsafeUtil.copyMemory(header, BYTE_ARRAY_BASE_OFFSET, null, ptr, headerSize);
        ptr += alignedHeaderSize;

        // main packing loop
        int in = 0;
        for (byte bits : header) {
            ptr = AdjacencyPacking.pack(bits, values, in, ptr);
            in += AdjacencyPacking.BLOCK_SIZE;
        }

        if (ptr > initialPtr + allocationSize)
            throw new AssertionError("Written more bytes than allocated. ptr=" + ptr + ", initialPtr=" + initialPtr + ", allocationSize=" + allocationSize);

        return adjacencyOffset;
    }

    private static long preparePackingWithVarLongTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        MemoryTracker memoryTracker
    ) {
        int blocks = length / AdjacencyPacking.BLOCK_SIZE;
        var header = new byte[blocks];

        long blockBytes = 0L;
        int offset = 0;
        int blockIdx = 0;

        for (; blockIdx < blocks; blockIdx++, offset += AdjacencyPacking.BLOCK_SIZE) {
            int bits = bitsNeeded(values, offset, AdjacencyPacking.BLOCK_SIZE);
            memoryTracker.recordHeaderBits(bits);
            blockBytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }

        int tailLength = length - offset;
        long tailBytes = encodedVLongsSize(values, length - tailLength, tailLength);

        return runPackingWithVarLongTail(
            allocator,
            slice,
            values,
            header,
            blockBytes,
            tailBytes,
            length,
            tailLength
        );
    }

    private static long runPackingWithVarLongTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        byte[] header,
        long blockBytes,
        long tailBytes,
        int length,
        int tailLength
    ) {
        assert values.length % AdjacencyPacking.BLOCK_SIZE == 0 : "values length must be a multiple of " + AdjacencyPacking.BLOCK_SIZE + ", but was " + values.length;

        long headerSize = header.length * Byte.BYTES;
        // we must add padding between the header and the data bytes
        // to avoid writing unaligned longs
        long alignedHeaderSize = BitUtil.align(headerSize, Long.BYTES);
        long fullSize = alignedHeaderSize + blockBytes + tailBytes;
        // we must align to long because we write in terms of longs, not single bytes
        long alignedFullSize = BitUtil.align(fullSize, Long.BYTES);
        int allocationSize = Math.toIntExact(alignedFullSize);

        long adjacencyOffset = allocator.allocate(allocationSize, slice);

        Address address = slice.slice();
        long ptr = address.address() + slice.offset();

        // write header
        UnsafeUtil.copyMemory(header, BYTE_ARRAY_BASE_OFFSET, null, ptr, headerSize);
        ptr += alignedHeaderSize;

        // main packing loop
        int in = 0;
        for (byte bits : header) {
            ptr = AdjacencyPacking.pack(bits, values, in, ptr);
            in += AdjacencyPacking.BLOCK_SIZE;
        }

        AdjacencyCompression.compress(values, length - tailLength, tailLength, ptr);

        return adjacencyOffset;
    }

    /**
     * Compress using packing for tail compression.
     */

    public static long compressWithPackedTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree,
        MemoryTracker memoryTracker
    ) {
        Arrays.sort(values, 0, length);
        return deltaCompressWithPackedTail(allocator, slice, values, length, aggregation, degree, memoryTracker);
    }

    static long compressWithPropertiesWithPackedTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        MemoryTracker memoryTracker
    ) {
        return preparePackingWithPackedTail(allocator, slice, values, length, memoryTracker);
    }

    private static long deltaCompressWithPackedTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree,
        MemoryTracker memoryTracker
    ) {
        if (length > 0) {
            length = AdjacencyCompression.deltaEncodeSortedValues(values, 0, length, aggregation);
        }

        degree.setValue(length);

        return preparePackingWithPackedTail(allocator, slice, values, length, memoryTracker);
    }

    private static long preparePackingWithPackedTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        MemoryTracker memoryTracker
    ) {
        boolean hasTail = length == 0 || length % AdjacencyPacking.BLOCK_SIZE != 0;
        int blocks = BitUtil.ceilDiv(length, AdjacencyPacking.BLOCK_SIZE);
        var header = new byte[blocks];

        long bytes = 0L;
        int offset = 0;
        int blockIdx = 0;
        int lastFullBlock = hasTail ? blocks - 1 : blocks;

        for (; blockIdx < lastFullBlock; blockIdx++, offset += AdjacencyPacking.BLOCK_SIZE) {
            int bits = bitsNeeded(values, offset, AdjacencyPacking.BLOCK_SIZE);
            memoryTracker.recordHeaderBits(bits);
            bytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }
        // "tail" block, may be smaller than BLOCK_SIZE
        int tailLength = (length - offset);
        if (hasTail) {
            int bits = bitsNeeded(values, offset, tailLength);
            memoryTracker.recordHeaderBits(bits);
            bytes += bytesNeeded(bits, tailLength);
            header[blockIdx] = (byte) bits;
        }

        return runPackingWithPackedTail(
            allocator,
            slice,
            values,
            header,
            bytes,
            tailLength,
            memoryTracker
        );
    }

    private static long runPackingWithPackedTail(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        byte[] header,
        long bytes,
        int tailLength,
        MemoryTracker memoryTracker
    ) {
        assert values.length % AdjacencyPacking.BLOCK_SIZE == 0 : "values length must be a multiple of " + AdjacencyPacking.BLOCK_SIZE + ", but was " + values.length;

        long headerSize = header.length * Byte.BYTES;
        // we must add padding between the header and the data bytes
        // to avoid writing unaligned longs
        long alignedHeaderSize = BitUtil.align(headerSize, Long.BYTES);
        long fullSize = alignedHeaderSize + bytes;
        // we must align to long because we write in terms of longs, not single bytes
        long alignedFullSize = BitUtil.align(fullSize, Long.BYTES);
        int allocationSize = Math.toIntExact(alignedFullSize);
        memoryTracker.recordHeaderAllocation(alignedHeaderSize);

        long adjacencyOffset = allocator.allocate(allocationSize, slice);

        Address address = slice.slice();
        long ptr = address.address() + slice.offset();
        long initialPtr = ptr;

        // write header
        UnsafeUtil.copyMemory(header, BYTE_ARRAY_BASE_OFFSET, null, ptr, headerSize);
        ptr += alignedHeaderSize;

        // main packing loop
        boolean hasTail = tailLength > 0;
        int in = 0;
        int headerLength = hasTail ? header.length - 1 : header.length;

        for (int i = 0; i < headerLength; i++) {
            byte bits = header[i];
            ptr = AdjacencyPacking.pack(bits, values, in, ptr);
            in += AdjacencyPacking.BLOCK_SIZE;
        }

        // tail packing
        if (hasTail) {
            byte bits = header[header.length - 1];
            ptr = AdjacencyPacking.loopPack(bits, values, in, tailLength, ptr);
        }

        if (ptr > initialPtr + allocationSize)
            throw new AssertionError("Written more bytes than allocated. ptr=" + ptr + ", initialPtr=" + initialPtr + ", allocationSize=" + allocationSize);


        return adjacencyOffset;
    }

    private static int bitsNeeded(long[] values, int offset, int length) {
        long bits = 0L;
        for (int i = offset; i < offset + length; i++) {
            bits |= values[i];
        }
        return Long.SIZE - Long.numberOfLeadingZeros(bits);
    }

    private static int bytesNeeded(int bits) {
        return BitUtil.ceilDiv(AdjacencyPacking.BLOCK_SIZE * bits, Byte.SIZE);
    }

    private static int bytesNeeded(int bits, int length) {
        return BitUtil.ceilDiv(length * bits, Byte.SIZE);
    }
}
