package by.righttwixys.graffiti.client;

import by.righttwixys.graffiti.config.GraffitiConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.component.Component;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("title.graffiti.config"));

            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("category.graffiti.general"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Переключатель ВКЛ/ВЫКЛ
            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.graffiti.enabled"), GraffitiConfig.get().enabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> GraffitiConfig.get().enabled = newValue)
                    .build());

            // Переключатель Culling
            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.graffiti.culling"), GraffitiConfig.get().useCulling)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("tooltip.graffiti.culling"))
                    .setSaveConsumer(newValue -> GraffitiConfig.get().useCulling = newValue)
                    .build());

            // Дальность прорисовки
            general.addEntry(entryBuilder.startIntSlider(Text.translatable("option.graffiti.distance"), GraffitiConfig.get().renderDistance, 4, 128)
                    .setDefaultValue(32)
                    .setSaveConsumer(newValue -> GraffitiConfig.get().renderDistance = newValue)
                    .build());

            builder.setSavingRunnable(GraffitiConfig::save);
            return builder.build();
        };
    }
}