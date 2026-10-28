package dev.minetorio.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.blueprint.BlueprintStrings.BadBlueprintString;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BlueprintStringsTest {
    private static byte[] sample(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        for (int i = 0; i < n; i += 3) b[i] = 7; // some repetition
        return b;
    }

    @Test
    void roundTrip() throws BadBlueprintString {
        byte[] raw = sample(5000);
        String s = BlueprintStrings.encode(raw);
        assertEquals(BlueprintStrings.VERSION, s.charAt(0));
        assertArrayEquals(raw, BlueprintStrings.decode(s));
    }

    @Test
    void whitespaceIsIgnored() throws BadBlueprintString {
        byte[] raw = sample(300);
        String s = BlueprintStrings.encode(raw);
        String wrapped = "  " + s.substring(0, 10) + "\n" + s.substring(10, 40) + "\r\n " + s.substring(40) + "\n";
        assertArrayEquals(raw, BlueprintStrings.decode(wrapped));
    }

    @Test
    void malformedStringsAreRefused() {
        String good = BlueprintStrings.encode(sample(500));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode(null));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode(""));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("   "));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("0"));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("1" + good.substring(1)), "unknown version");
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("0eNq!!!not*base64"));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("0" + Base64.getEncoder().encodeToString("hello world".getBytes())),
                "not deflate");
        // truncated deflate stream
        byte[] packed = BlueprintStrings.deflate(sample(2000));
        String cut = "0" + Base64.getEncoder().encodeToString(Arrays.copyOf(packed, packed.length / 2));
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode(cut));
        // junk after the stream
        byte[] longer = Arrays.copyOf(packed, packed.length + 5);
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("0" + Base64.getEncoder().encodeToString(longer)));
    }

    @Test
    void oversizedIsRefused() {
        // a deflate bomb: tiny string, inflates past the limit
        byte[] zeros = new byte[BlueprintStrings.MAX_RAW_BYTES + 1];
        String bomb = BlueprintStrings.encode(zeros);
        assertTrue(bomb.length() < 10_000, "bomb string " + bomb.length());
        BadBlueprintString e = assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode(bomb));
        assertEquals("too large", e.getMessage());
        // just inside the limit inflates fine
        assertDoesNotThrow(() -> BlueprintStrings.decode(BlueprintStrings.encode(new byte[BlueprintStrings.MAX_RAW_BYTES])));
        // a string longer than any valid one
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.decode("0" + "A".repeat(BlueprintStrings.MAX_STRING_LENGTH)));
        // a compressed payload over the limit
        byte[] noise = new byte[BlueprintStrings.MAX_COMPRESSED_BYTES + 100];
        new Random(1).nextBytes(noise);
        assertThrows(BadBlueprintString.class, () -> BlueprintStrings.inflate(noise));
    }
}
