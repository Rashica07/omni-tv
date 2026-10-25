package com.rasha.omni;

import java.util.ArrayList;
import java.util.List;

/** Maps installed apps onto ShelfStack items. Art comes straight from the app; nothing is invented. */
final class AppItems {
    private AppItems() { }

    static ShelfStack.Item of(AppRepo.App a) {
        ShelfStack.Item it = new ShelfStack.Item(a.pkg, a.label);
        IconArt art = a.art;
        it.art = art.tile;
        it.icon = art.icon;
        it.color = a.color;
        it.shape = art.kind == IconArt.Kind.BANNER ? ShelfStack.SHAPE_BANNER
                : art.kind == IconArt.Kind.SELF ? ShelfStack.SHAPE_SQUARE_SELF : ShelfStack.SHAPE_SQUARE_PLATE;
        it.data = a;
        return it;
    }

    static List<ShelfStack.Item> list(List<AppRepo.App> apps) {
        List<ShelfStack.Item> out = new ArrayList<>(apps.size());
        for (AppRepo.App a : apps) out.add(of(a));
        return out;
    }

    static AppRepo.App app(ShelfStack.Item it) { return (AppRepo.App) it.data; }
}
