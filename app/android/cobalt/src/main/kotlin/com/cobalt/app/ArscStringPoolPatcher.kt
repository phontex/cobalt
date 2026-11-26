package com.cobalt.app

import java.io.ByteArrayOutputStream

/**
 * Rewrites the global string pool in resources.arsc without changing resource IDs.
 *
 * Android resources refer to strings by pool index, not by byte offset. We therefore keep the
 * index order intact, replace only the string bytes, rebuild the pool offsets and move the
 * following chunks when the new pool has a different size. This makes the editable JSON on
 * GitHub the source of truth; a version-specific binary resources.arsc is not required.
 */
object ArscStringPoolPatcher {
    private const val RES_TABLE_HEADER = 0x0002
    private const val RES_STRING_POOL = 0x0001
    private const val UTF8_FLAG = 0x00000100
    private const val STRING_POOL_HEADER_SIZE = 28

    data class Result(
        val bytes: ByteArray,
        val replaced: Int,
        val missing: Int,
    )

    fun patch(input: ByteArray, translations: Map<String, String>): Result {
        require(input.size >= 12) { "resources.arsc слишком короткий" }
        require(u16(input, 0) == RES_TABLE_HEADER) { "Это не resources.arsc" }

        val poolOffset = u16(input, 2)
        require(poolOffset + STRING_POOL_HEADER_SIZE <= input.size) { "Повреждён заголовок resources.arsc" }
        require(u16(input, poolOffset) == RES_STRING_POOL) { "Глобальный string pool не найден" }

        val pool = readPool(input, poolOffset)
        val replacements = arrayOfNulls<String>(pool.strings.size)
        var replaced = 0
        val found = HashSet<String>()

        pool.strings.forEachIndexed { index, original ->
            val translation = translations[original]
                ?.takeIf { it.isNotEmpty() && it != original }
                ?: return@forEachIndexed
            replacements[index] = translation
            found += original
            replaced++
        }

        val missing = translations.keys.count { it.isNotEmpty() && it !in found }
        if (replaced == 0) return Result(input, 0, missing)

        val newPool = buildPool(input, pool, replacements)
        val oldEnd = poolOffset + pool.chunkSize
        val output = ByteArray(input.size - pool.chunkSize + newPool.size)
        input.copyInto(output, destinationOffset = 0, startIndex = 0, endIndex = poolOffset)
        newPool.copyInto(output, destinationOffset = poolOffset)
        input.copyInto(output, destinationOffset = poolOffset + newPool.size, startIndex = oldEnd)

        // ResTable_header.size covers the complete resources.arsc file.
        putU32(output, 4, output.size)
        return Result(output, replaced, missing)
    }

    private data class Pool(
        val chunkSize: Int,
        val stringCount: Int,
        val styleCount: Int,
        val flags: Int,
        val stringsStart: Int,
        val stylesStart: Int,
        val poolOffset: Int,
        val offsets: IntArray,
        val styleOffsets: IntArray,
        val strings: List<String>,
    )

    private fun readPool(input: ByteArray, offset: Int): Pool {
        val headerSize = u16(input, offset + 2)
        val chunkSize = u32(input, offset + 4)
        val stringCount = u32(input, offset + 8)
        val styleCount = u32(input, offset + 12)
        val flags = u32(input, offset + 16)
        val stringsStart = u32(input, offset + 20)
        val stylesStart = u32(input, offset + 24)
        require(headerSize >= STRING_POOL_HEADER_SIZE && chunkSize > stringsStart) { "Повреждён string pool" }
        require(offset + chunkSize <= input.size) { "String pool выходит за пределы APK" }

        val offsets = IntArray(stringCount) { i -> u32(input, offset + headerSize + i * 4) }
        val styleOffsets = IntArray(styleCount) { i ->
            u32(input, offset + headerSize + stringCount * 4 + i * 4)
        }
        val strings = offsets.map { relative ->
            decodeString(input, offset + stringsStart + relative, flags and UTF8_FLAG != 0)
        }
        return Pool(chunkSize, stringCount, styleCount, flags, stringsStart, stylesStart, offset, offsets, styleOffsets, strings)
    }

    private fun buildPool(input: ByteArray, old: Pool, replacements: Array<String?>): ByteArray {
        val stringData = ByteArrayOutputStream()
        val newOffsets = IntArray(old.stringCount)
        old.strings.forEachIndexed { index, original ->
            newOffsets[index] = stringData.size()
            val value = replacements[index] ?: original
            if (old.flags and UTF8_FLAG != 0) encodeUtf8String(stringData, value)
            else encodeUtf16String(stringData, value)
        }

        val paddedStringDataSize = align4(stringData.size())
        val stringsStart = STRING_POOL_HEADER_SIZE + old.stringCount * 4 + old.styleCount * 4
        val stylesStart = if (old.styleCount == 0) 0 else stringsStart + paddedStringDataSize
        val styleBytes = if (old.styleCount > 0 && old.stylesStart > 0) {
            val start = old.poolOffset + old.stylesStart
            input.copyOfRange(start, old.poolOffset + old.chunkSize)
        } else ByteArray(0)
        val chunkSize = stylesStart.takeIf { it > 0 }?.plus(styleBytes.size)
            ?: stringsStart + paddedStringDataSize

        val out = ByteArrayOutputStream(chunkSize)
        writeU16(out, RES_STRING_POOL)
        writeU16(out, STRING_POOL_HEADER_SIZE)
        writeU32(out, chunkSize)
        writeU32(out, old.stringCount)
        writeU32(out, old.styleCount)
        writeU32(out, old.flags)
        writeU32(out, stringsStart)
        writeU32(out, stylesStart)
        newOffsets.forEach { writeU32(out, it) }
        old.styleOffsets.forEach { writeU32(out, it) }
        out.write(stringData.toByteArray())
        repeat(paddedStringDataSize - stringData.size()) { out.write(0) }
        out.write(styleBytes)
        return out.toByteArray()
    }

    private fun decodeString(input: ByteArray, offset: Int, utf8: Boolean): String {
        if (utf8) {
            var cursor = offset
            cursor += encodedLengthSize(input, cursor)
            val byteLength = readLength8(input, cursor)
            cursor += encodedLengthSize(input, cursor)
            return input.copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_8)
        }
        var cursor = offset
        val (length, size) = readLength16(input, cursor)
        cursor += size
        return input.copyOfRange(cursor, cursor + length * 2).toString(Charsets.UTF_16LE)
    }

    private fun encodeUtf8String(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeLength8(out, value.length)
        writeLength8(out, bytes.size)
        out.write(bytes)
        out.write(0)
    }

    private fun encodeUtf16String(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_16LE)
        writeLength16(out, value.length)
        out.write(bytes)
        out.write(0)
        out.write(0)
    }

    private fun readLength8(input: ByteArray, offset: Int): Int =
        if (input[offset].toInt() and 0x80 == 0) input[offset].toInt() and 0x7f
        else ((input[offset].toInt() and 0x7f) shl 8) or (input[offset + 1].toInt() and 0xff)

    private fun encodedLengthSize(input: ByteArray, offset: Int): Int =
        if (input[offset].toInt() and 0x80 == 0) 1 else 2

    private fun readLength16(input: ByteArray, offset: Int): Pair<Int, Int> {
        val first = u16(input, offset)
        return if (first and 0x8000 == 0) first to 2
        else (((first and 0x7fff) shl 16) or u16(input, offset + 2)) to 4
    }

    private fun writeLength8(out: ByteArrayOutputStream, value: Int) {
        require(value <= 0x7fff) { "Строка слишком длинная для UTF-8 string pool" }
        if (value > 0x7f) out.write((value shr 8) or 0x80)
        out.write(value and 0xff)
    }

    private fun writeLength16(out: ByteArrayOutputStream, value: Int) {
        require(value <= 0x7fff_ffff) { "Строка слишком длинная для UTF-16 string pool" }
        if (value > 0x7fff) {
            writeU16(out, (value ushr 16) or 0x8000)
            writeU16(out, value and 0xffff)
        } else {
            writeU16(out, value)
        }
    }

    private fun align4(value: Int): Int = (value + 3) and -4

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)

    private fun putU32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
        data[offset + 2] = (value ushr 16).toByte()
        data[offset + 3] = (value ushr 24).toByte()
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }
}
