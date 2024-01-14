package net.mehvahdjukaar.polytone.lightmap;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.JsonOps;
import net.mehvahdjukaar.polytone.Polytone;
import net.mehvahdjukaar.polytone.utils.ArrayImage;
import net.mehvahdjukaar.polytone.utils.JsonImgPartialReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class LightmapsManager extends JsonImgPartialReloader {

    public static final ResourceLocation GUI_LIGHTMAP = Polytone.res("lightmaps/gui.png");

    private final Map<ResourceLocation, Lightmap> lightmaps = new HashMap<>();
    private ResourceKey<Level> lastDimension = null;
    private Lightmap currentLightmap = null;

    public LightmapsManager() {
        super("lightmaps");
    }


    @Override
    public void process(Resources resources) {
        var images = resources.textures();
        var jsons = resources.jsons();
        lastDimension = null;
        currentLightmap = null;

        Map<ResourceLocation, Map<String, ArrayImage>> grouped = new HashMap<>();
        for (var e : images.entrySet()) {
            ArrayImage value = e.getValue();
            int height = value.height();
            ResourceLocation location = e.getKey();
            if (height != 16 && height != 32 && height != 64) {
                throw new IllegalStateException("Lightmap must be either 16, 32 or 64 pixels tall. Provided one at " + location + " was " + height + " pixels");
            } else {
                String path = location.getPath();
                if (path.endsWith("_thunder")) {
                    grouped.computeIfAbsent(location.withPath(path.replace("_thunder", "")),
                            g -> new HashMap<>()).put("thunder", value);
                } else if (path.endsWith("_rain")) {
                    grouped.computeIfAbsent(location.withPath(path.replace("_rain", "")),
                            g -> new HashMap<>()).put("rain", value);
                } else {
                    grouped.computeIfAbsent(location, g -> new HashMap<>()).put("normal", value);
                }
            }
        }

        for (var e : grouped.entrySet()) {
            ResourceLocation location = e.getKey();

            JsonElement j = jsons.remove(location);
            Lightmap lightmap;
            if(j != null){
                lightmap = Lightmap.CODEC.decode(JsonOps.INSTANCE, j)
                        .getOrThrow(false, errorMsg -> Polytone.LOGGER.warn("Could not decode Lightmap with json id {} - error: {}", location, errorMsg))
                        .getFirst();
            }else{
                //default samplers
                lightmap = new Lightmap();
            }

            var map = e.getValue();
            lightmap.acceptImages(map.get("normal"), map.get("rain"), map.get("thunder"));

            ResourceLocation localId = Polytone.getLocalId(location);
            lightmaps.put(localId, lightmap);
            lightmaps.put(location, lightmap);
        }

        if(!jsons.isEmpty()){
            throw new IllegalStateException("Found some lightmaps .jsons with no associated textures at"+ jsons);
        }
    }

    @Override
    protected void reset() {
        lightmaps.clear();
    }

    public boolean maybeModifyLightTexture(LightTexture instance,
                                           NativeImage lightPixels,
                                           DynamicTexture lightTexture,
                                           Minecraft minecraft, ClientLevel level,
                                           float flicker, float partialTicks) {
        if (lastDimension != level.dimension()) {
            lastDimension = level.dimension();
            currentLightmap = lightmaps.get(lastDimension.location());
        }
        if(USING_GUI_LIGHTMAP){
            int aa =1;//error
        }
        if (currentLightmap != null) {
            return currentLightmap.applyToLightTexture(instance, lightPixels, lightTexture, minecraft,
                    level, flicker, partialTicks);
        }
        return false;
    }

    private static boolean USING_GUI_LIGHTMAP = false;

    public static void setupForGUI(boolean gui) {
        USING_GUI_LIGHTMAP = gui;
    }

    public static boolean isGui() {
        return USING_GUI_LIGHTMAP;
    }
}