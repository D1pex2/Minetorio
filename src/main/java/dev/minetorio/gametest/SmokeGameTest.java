package dev.minetorio.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class SmokeGameTest {
    @GameTest(template = "empty16")
    public static void serverBoots(GameTestHelper h) {
        h.succeed();
    }
}
