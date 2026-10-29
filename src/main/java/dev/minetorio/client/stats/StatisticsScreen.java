package dev.minetorio.client.stats;

import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.client.gui.FactoryButton;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.stats.StatsPayloads;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Live production and consumption events; selecting a resource plots its actual saved history. */
public final class StatisticsScreen extends Screen {
    private static final int PANEL_W=450,PANEL_H=260,ROWS=8,ROW_H=20;
    private StatsPayloads.Snapshot snapshot;
    private int window,scroll,ticks;
    private boolean fluids;
    private String selected="";
    private int left,top;
    private float uiScale=1;
    private final java.util.function.Consumer<StatsPayloads.Snapshot> receiver=this::receive;
    public StatisticsScreen(){super(Component.translatable("minetorio.stats.title"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init() {
        uiScale=Math.min(1,Math.min(width/(float)(PANEL_W+12),height/(float)(PANEL_H+12)));
        width=(int)Math.floor(width/uiScale);height=(int)Math.floor(height/uiScale);
        left=(width-PANEL_W)/2;top=(height-PANEL_H)/2;StatsPayloads.clientHandler=receiver;
        String[] windows={"1m","10m","1h","all"};
        for(int i=0;i<windows.length;i++){int index=i;addRenderableWidget(new FactoryButton(left+8+i*43,top+25,40,18,Component.translatable("minetorio.stats.window."+windows[i]),button->{window=index;scroll=0;request();}));}
        addRenderableWidget(new FactoryButton(left+192,top+25,90,18,Component.translatable("minetorio.stats.items"),button->{fluids=false;scroll=0;selected="";request();}));
        addRenderableWidget(new FactoryButton(left+286,top+25,90,18,Component.translatable("minetorio.stats.fluids"),button->{fluids=true;scroll=0;selected="";request();}));
        addRenderableWidget(new FactoryButton(left+398,top+25,44,18,Component.literal("×"),button->onClose()));request();
    }
    private void request(){if(minecraft!=null && minecraft.getConnection()!=null)PacketDistributor.sendToServer(new StatsPayloads.Request(window,fluids,selected));}
    private void receive(StatsPayloads.Snapshot incoming){if(incoming.window()!=window || incoming.fluids()!=fluids)return;snapshot=incoming;selected=incoming.selected();scroll=Math.clamp(scroll,0,Math.max(0,incoming.rows().size()-ROWS));}
    @Override public void tick(){if(++ticks%20==0)request();}
    @Override public void removed(){if(StatsPayloads.clientHandler==receiver)StatsPayloads.clientHandler=ignored->{};}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,snapshot==null?0:snapshot.rows().size()-ROWS));return true;}
    @Override public boolean mouseClicked(double x,double y,int button) {
        x/=uiScale;y/=uiScale;
        if(button==0 && snapshot!=null && x>=left+8 && x<left+244 && y>=top+65 && y<top+65+ROWS*ROW_H) {
            int row=scroll+(int)(y-top-65)/ROW_H;if(row<snapshot.rows().size()){selected=snapshot.rows().get(row).id();request();return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    private static String amount(double n){return n>=1_000_000?String.format(Locale.ROOT,"%.1fM",n/1_000_000):n>=10000?String.format(Locale.ROOT,"%.1fk",n/1000):n==Math.rint(n)?Long.toString((long)n):String.format(Locale.ROOT,"%.1f",n);}
    private Component name(String id){return id.startsWith("fluid:")?Component.translatable("minetorio.fluid."+id.substring(6)):MachineItems.stack(id,1).getHoverName();}
    private void label(GuiGraphics g,Component text,int x,int y){FactoryTheme.label(g,font,text,left+x,top+y);}
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);renderContents(g,(int)(x/uiScale),(int)(y/uiScale),partial);g.pose().popPose();
    }
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/uiScale,y/uiScale,button);}
    private void renderContents(GuiGraphics g,int x,int y,float partial) {
        FactoryTheme.panel(g,left,top,PANEL_W,PANEL_H);FactoryTheme.title(g,font,title,left+10,top+6);
        // Controls share the procedural industrial panel treatment.
        for(var widget:renderables)widget.render(g,x,y,partial);
        label(g,Component.translatable("minetorio.stats.resource"),8,52);label(g,Component.translatable("minetorio.stats.produced"),142,52);label(g,Component.translatable("minetorio.stats.consumed"),195,52);
        FactoryTheme.inset(g,left+7,top+63,239,ROWS*ROW_H+2);FactoryTheme.inset(g,left+252,top+63,190,122);
        if(snapshot==null || snapshot.window()!=window || snapshot.fluids()!=fluids) {label(g,Component.translatable("minetorio.stats.loading"),14,76);return;}
        if(snapshot.rows().isEmpty()) {label(g,Component.translatable("minetorio.stats.empty"),14,76);return;}
        for(int i=0;i<ROWS && i+scroll<snapshot.rows().size();i++) {
            var row=snapshot.rows().get(i+scroll);int ry=65+i*ROW_H;
            if(row.id().equals(selected))g.fill(left+9,top+ry,left+244,top+ry+ROW_H,0x885F552E);
            if(row.id().startsWith("fluid:"))g.fill(left+12,top+ry+3,left+26,top+ry+17,0xFF000000|dev.minetorio.core.fluid.FluidData.color(row.id().substring(6)));
            else g.renderItem(MachineItems.stack(row.id(),1),left+11,top+ry+2);
            label(g,Component.literal(font.plainSubstrByWidth(name(row.id()).getString(),105)),31,ry+6);
            g.drawString(font,amount(row.produced()),left+144,top+ry+6,FactoryTheme.GREEN,false);
            g.drawString(font,amount(row.consumed()),left+198,top+ry+6,FactoryTheme.ACCENT,false);
        }
        double max=1;for(double n:snapshot.produced())max=Math.max(max,n);for(double n:snapshot.consumed())max=Math.max(max,n);
        int gx=left+258,gy=top+78,gh=94;
        for(int grid=0;grid<4;grid++)g.fill(gx,gy+grid*gh/3,gx+180,gy+grid*gh/3+1,0xFF414638);
        for(int i=0;i<60;i++) {int ph=(int)(snapshot.produced()[i]/max*gh),ch=(int)(snapshot.consumed()[i]/max*gh);g.fill(gx+i*3,gy+gh-ph,gx+i*3+2,gy+gh,FactoryTheme.GREEN);g.fill(gx+i*3+2,gy+gh-ch,gx+i*3+3,gy+gh,FactoryTheme.ACCENT);}
        label(g,Component.literal(amount(max)),256,66);label(g,Component.literal("0"),256,175);
        label(g,Component.literal(font.plainSubstrByWidth(name(snapshot.selected()).getString(),184)),254,194);
        var resource=snapshot.rows().stream().filter(row->row.id().equals(snapshot.selected())).findFirst().orElseThrow();
        if(window<3){double factor=1200.0/StatsPayloads.WINDOWS[window];label(g,Component.translatable("minetorio.stats.rate",amount(resource.produced()*factor),amount(resource.consumed()*factor)),254,208);}
        label(g,Component.translatable("minetorio.stats.history_resolution"),8,235);
        label(g,Component.literal((scroll+1)+"–"+Math.min(scroll+ROWS,snapshot.rows().size())+" / "+snapshot.rows().size()),8,224);
        label(g,Component.translatable("minetorio.stats.legend"),254,224);
        if(x>=left+8 && x<left+244 && y>=top+65 && y<top+65+ROWS*ROW_H){int row=scroll+(y-top-65)/ROW_H;if(row<snapshot.rows().size())g.renderTooltip(font,name(snapshot.rows().get(row).id()),x,y);}
    }
}
