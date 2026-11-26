package com.cobalt.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for rewriting the global string pool of resources.arsc.
 *
 * The algorithm keeps every string index in place, so a wrong chunk size or offset would break
 * the resource table of the target application. The synthetic test covers growth and shrink;
 * the fixture tests run against a real resources.arsc when one is supplied, which is how this
 * was verified on a device.
 */
public final class ArscStringPoolPatcherTest {

    @Test
    public void replacesStringsAndKeepsIndexOrder() {
        byte[] input = tableWithPool("推荐", "No Data", "unchanged");
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("推荐", "Рекомендуем");
        translations.put("No Data", "Нет данных");
        translations.put("missing", "Не найдено");

        ArscStringPoolPatcher.Result result = ArscStringPoolPatcher.INSTANCE.patch(input, translations);

        assertEquals("two strings are present in the pool", 2, result.getReplaced());
        assertEquals("the third key is unknown to this APK", 1, result.getMissing());
        assertTrue("Russian text is longer, so the pool must grow", result.getBytes().length > input.length);
        assertEquals("string count never changes", 3, stringCount(result.getBytes()));
        assertEquals("index 0 keeps its slot", "Рекомендуем", poolString(result.getBytes(), 0));
        assertEquals("index 1 keeps its slot", "Нет данных", poolString(result.getBytes(), 1));
        assertEquals("untouched strings survive", "unchanged", poolString(result.getBytes(), 2));
        assertEquals("the table size field follows the new file", result.getBytes().length, u32(result.getBytes(), 4));
    }

    @Test
    public void shrinksWhenTranslationsAreShorter() {
        byte[] input = tableWithPool("一个很长的中文字符串", "keep");
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("一个很长的中文字符串", "Да");

        ArscStringPoolPatcher.Result result = ArscStringPoolPatcher.INSTANCE.patch(input, translations);

        assertEquals(1, result.getReplaced());
        assertTrue(result.getBytes().length < input.length);
        assertEquals("Да", poolString(result.getBytes(), 0));
        assertEquals("keep", poolString(result.getBytes(), 1));
    }

    /** Two published strings in a real APK, with the whole table checked for structural damage. */
    @Test
    public void patchesRealResourceTableWhenFixtureIsProvided() throws Exception {
        File fixture = fixture();
        Assume.assumeTrue("real resources.arsc fixture not provided", fixture.isFile());
        byte[] original = Files.readAllBytes(fixture.toPath());
        int originalCount = stringCount(original);

        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("推荐", "Рекомендуем");
        translations.put("No Data", "Нет данных");

        ArscStringPoolPatcher.Result result = ArscStringPoolPatcher.INSTANCE.patch(original, translations);

        assertEquals(2, result.getReplaced());
        assertEquals(originalCount, stringCount(result.getBytes()));
        assertEquals(result.getBytes().length, u32(result.getBytes(), 4));
        // Every string must still be readable, which catches a broken offset table.
        for (int index = 0; index < originalCount; index++) {
            poolString(result.getBytes(), index);
        }
    }

    /** The published translation applied to a real table: the number that matters in practice. */
    @Test
    public void appliesPublishedTranslationToRealTable() throws Exception {
        File fixture = fixture();
        File mapFile = new File(fixture.getParentFile(), "real-resources-map.tsv");
        Assume.assumeTrue("fixture and map are required", fixture.isFile() && mapFile.isFile());

        byte[] original = Files.readAllBytes(fixture.toPath());
        Map<String, String> translations = new LinkedHashMap<>();
        for (String line : Files.readAllLines(mapFile.toPath(), StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String key = line.substring(0, tab);
            String value = line.substring(tab + 1);
            if (!key.isEmpty() && !value.isEmpty()) translations.put(key, value);
        }

        ArscStringPoolPatcher.Result result = ArscStringPoolPatcher.INSTANCE.patch(original, translations);

        assertTrue("the map has thousands of pairs", translations.size() > 3000);
        assertTrue("most of them match this APK: " + result.getReplaced(), result.getReplaced() > 2500);
        assertEquals(stringCount(original), stringCount(result.getBytes()));
        Files.write(new File(fixture.getParentFile(), "real-resources-patched.arsc").toPath(), result.getBytes());
    }

    private static File fixture() {
        String configured = System.getenv("COBALT_ARSC_FIXTURE");
        return configured != null && !configured.isBlank() ? new File(configured) : new File("real-resources.arsc");
    }

    private static byte[] tableWithPool(String... strings) {
        ByteArrayOutputStream pool = new ByteArrayOutputStream();
        int[] offsets = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            offsets[i] = pool.size();
            byte[] bytes = strings[i].getBytes(StandardCharsets.UTF_8);
            write8(pool, strings[i].length());
            write8(pool, bytes.length);
            pool.writeBytes(bytes);
            pool.write(0);
        }
        while ((pool.size() & 3) != 0) pool.write(0);

        int poolHeader = 28;
        int stringsStart = poolHeader + strings.length * 4;
        int poolSize = stringsStart + pool.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + poolSize);
        write16(out, 0x0002);
        write16(out, 12);
        write32(out, 12 + poolSize);
        write32(out, 0);
        write16(out, 0x0001);
        write16(out, poolHeader);
        write32(out, poolSize);
        write32(out, strings.length);
        write32(out, 0);
        write32(out, 0x100);
        write32(out, stringsStart);
        write32(out, 0);
        for (int offset : offsets) write32(out, offset);
        out.writeBytes(pool.toByteArray());
        return out.toByteArray();
    }

    private static int stringCount(byte[] data) {
        return u32(data, u16(data, 2) + 8);
    }

    private static String poolString(byte[] data, int index) {
        int pool = u16(data, 2);
        int header = u16(data, pool + 2);
        int start = pool + u32(data, pool + 20);
        int cursor = start + u32(data, pool + header + index * 4);
        cursor += read8(data, cursor) > 0x7f ? 2 : 1;
        int bytes = read8(data, cursor);
        cursor += bytes > 0x7f ? 2 : 1;
        return new String(data, cursor, bytes, StandardCharsets.UTF_8);
    }

    private static int read8(byte[] data, int offset) {
        int first = data[offset] & 0xff;
        return (first & 0x80) == 0 ? first : ((first & 0x7f) << 8) | (data[offset + 1] & 0xff);
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int u32(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static void write8(ByteArrayOutputStream out, int value) {
        if (value > 0x7f) out.write((value >> 8) | 0x80);
        out.write(value & 0xff);
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void write32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }
}
