package net.Ildar.wurm;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.shared.constants.PlayerAction;
import javassist.ClassPool;
import javassist.CtClass;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Unequipper implements WurmClientMod, Initable{
    private static Logger logger = Logger.getLogger(Unequipper.class.getSimpleName());
    private static HeadsUpDisplay hud;
    private static List<Long> lastUnequippedItems;
    @Override
    public void init() {
        try {
            lastUnequippedItems = new ArrayList<>();
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctWurmConsole = classPool.getCtClass("com.wurmonline.client.console.WurmConsole");
            ctWurmConsole.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z").insertBefore("if (net.Ildar.wurm.Unequipper.handleInput($1,$2)) return true;");

            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                hud = (HeadsUpDisplay)proxy;
                return null;
            });
        } catch (Exception e) {
            if (Unequipper.logger != null) {
                Unequipper.logger.log(Level.SEVERE, "Error loading mod", e);
                Unequipper.logger.log(Level.SEVERE, e.getMessage());
            }
        }
    }


    public static boolean handleInput(final String cmd, final String[] data) {
        switch (cmd) {
            case "unequip":
                String usage = "Usage unequip {all|armour|undo}";
                if (data.length < 2) {
                    hud.consoleOutput(usage);
                    return true;
                }
                try {
                    PaperDollInventory pdi = ReflectionUtil.getPrivateField(hud, ReflectionUtil.getField(hud.getClass(), "paperdollInventory"));
                    Map<Long, PaperDollSlot> frameList = ReflectionUtil.getPrivateField(pdi, ReflectionUtil.getField(PaperDollInventory.class, "frameList"));
                    switch (data[1]) {
                        case "armour":
                        case "all":
                            lastUnequippedItems.clear();
                            for (Map.Entry<Long, PaperDollSlot> frame : frameList.entrySet()) {
                                PaperDollSlot slot = frame.getValue();
                                if (slot == null || slot.getEquippedItem() == null) continue;
                                if (data[1].equals("all") || (slot.getEquipmentSlot() >= 2 && slot.getEquipmentSlot() <= 10)) {
                                    hud.consoleOutput("Unequipping " + slot.getEquippedItem().getItemName());
                                    long id = slot.getEquippedItem().getId();
                                    lastUnequippedItems.add(id);
                                    hud.sendAction(PlayerAction.UNEQUIP_ITEM, id);
                                }
                            }
                            break;
                        case "undo":
                            if (lastUnequippedItems.size() > 0) {
                                hud.consoleOutput("Equipping back " + lastUnequippedItems.size() + " items");
                                for (Long id : lastUnequippedItems)
                                    hud.sendAction(PlayerAction.EQUIP_ITEM, id);
                                lastUnequippedItems.clear();
                            } else
                                hud.consoleOutput("You didn't use Unequipper yet");
                            break;
                        default:
                            hud.consoleOutput(usage);
                    }
                } catch (Exception e) {
                    hud.consoleOutput("Unexpected error while unequipping - " + e);
                }
                return true;
            default:
                return false;
        }
    }
}
