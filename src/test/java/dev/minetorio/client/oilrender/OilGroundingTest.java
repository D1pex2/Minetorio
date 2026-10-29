package dev.minetorio.client.oilrender;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OilGroundingTest {
    @Test void everyOilMachineBaseRestsOnItsPlacementFloor() throws Exception {
        for(var machine:OilModelParts.PARTS.entrySet()) {
            double bottom=Double.POSITIVE_INFINITY;
            for(var part:machine.getValue()) {
                PoseStack pose=new PoseStack();OilRender.positionPart(pose,part);
                try(var in=getClass().getResourceAsStream("/assets/minetorio/models/block/"+part.model()+".json")) {
                    if(in==null)throw new AssertionError("Missing model "+part.model());
                    var model=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                    for(var element:model.getAsJsonArray("elements")) {
                        float y=element.getAsJsonObject().getAsJsonArray("from").get(1).getAsFloat()/16;
                        bottom=Math.min(bottom,pose.last().pose().transform(new Vector4f(0,y,0,1)).y);
                    }
                }
            }
            assertTrue(bottom>=0 && bottom<0.02,machine.getKey()+" base floats above floor by "+bottom+" blocks");
        }
    }
}
