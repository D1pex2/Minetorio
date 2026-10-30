package dev.minetorio.client.rail;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.rail.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Собственная трехмерная геометрия. Модель кузова не служит формулой физики Factorio. */
public final class RailwayRenderers {
    public static final ModelResourceLocation RAIL=MachineRender.model("block/rail_segment");
    public static final ModelResourceLocation LOCO=MachineRender.model("block/rail_locomotive");
    public static final ModelResourceLocation CARGO=MachineRender.model("block/rail_cargo");
    public static final ModelResourceLocation FLUID=MachineRender.model("block/rail_fluid");
    public static final List<ModelResourceLocation> MODELS=List.of(RAIL,LOCO,CARGO,FLUID);
    private static void draw(ModelResourceLocation model,PoseStack pose,MultiBufferSource buffers,int light) {
        MachineRender.renderModel(Minecraft.getInstance().getBlockRenderer().getModelRenderer(),model,pose,buffers,light,OverlayTexture.NO_OVERLAY);
    }
    public static final class Cells implements BlockEntityRenderer<RailCellBlockEntity> {
        public Cells(BlockEntityRendererProvider.Context context) {}
        @Override public boolean shouldRenderOffScreen(RailCellBlockEntity cell) { return true; }
        @Override public int getViewDistance() { return 128; }
        @Override public void render(RailCellBlockEntity cell,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            for(Piece piece:cell.pieces()) {
                int count=(int)Math.ceil(length(piece)*4);double segment=length(piece)/count;
                for(int i=0;i<count;i++) {
                    Pose at=sample(piece,(i+.5)*segment);
                    // Каждый загруженный участок рисует свои сегменты, без зависимости от первой клетки piece.
                    if(!net.minecraft.core.BlockPos.containing(at.position().x(),at.position().y(),at.position().z()).equals(cell.getBlockPos()))continue;
                    pose.pushPose();
                    pose.translate(at.position().x()-cell.getBlockPos().getX(),.02,at.position().z()-cell.getBlockPos().getZ());
                    pose.mulPose(Axis.YP.rotation((float)Math.atan2(at.tangent().x(),at.tangent().z())));
                    pose.scale(1,1,(float)segment);pose.translate(-.5,0,-.5);draw(RAIL,pose,buffers,light);pose.popPose();
                }
            }
        }
    }
    public static final class Stock extends EntityRenderer<RollingStockEntity> {
        public Stock(EntityRendererProvider.Context context) { super(context);shadowRadius=1.2f; }
        @Override public void render(RollingStockEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(StockPickShape.rotation(entity.getYRot())));pose.scale(1,1,(float)StockPickShape.LENGTH_SCALE);pose.translate(-.5,0,-.5);
            draw(switch(entity.kind()){case LOCOMOTIVE->LOCO;case CARGO->CARGO;case FLUID->FLUID;},pose,buffers,light);pose.popPose();
            super.render(entity,yaw,partial,pose,buffers,light);
        }
        @Override public ResourceLocation getTextureLocation(RollingStockEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
