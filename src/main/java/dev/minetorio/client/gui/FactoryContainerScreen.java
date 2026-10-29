package dev.minetorio.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
/** Fits industrial multi-panel menus into small windows while keeping slot hit boxes aligned. */
public abstract class FactoryContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private float factoryScale=1;
    protected FactoryContainerScreen(M menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void init() {
        factoryScale=Math.min(1,Math.min(width/(float)(imageWidth+12),height/(float)(imageHeight+12)));
        width=(int)Math.floor(width/factoryScale);height=(int)Math.floor(height/factoryScale);super.init();
    }
    @Override public final void render(GuiGraphics g,int x,int y,float partial){g.pose().pushPose();g.pose().scale(factoryScale,factoryScale,1);renderFactory(g,(int)(x/factoryScale),(int)(y/factoryScale),partial);g.pose().popPose();}
    protected void renderFactory(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);}
    @Override public final boolean mouseClicked(double x,double y,int button){return factoryMouseClicked(x/factoryScale,y/factoryScale,button);}
    protected boolean factoryMouseClicked(double x,double y,int button){return super.mouseClicked(x,y,button);}
    @Override public final boolean mouseScrolled(double x,double y,double horizontal,double vertical){return factoryMouseScrolled(x/factoryScale,y/factoryScale,horizontal,vertical);}
    protected boolean factoryMouseScrolled(double x,double y,double horizontal,double vertical){return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/factoryScale,y/factoryScale,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(x/factoryScale,y/factoryScale,button,dx/factoryScale,dy/factoryScale);}
}
