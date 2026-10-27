package com.rasha.omni;

public final class RunAll {
    public static void main(String[] args) {
        String[] names = {"Accordion", "SpringMath", "ListOps", "AppFilter", "CategoryRules", "Inputs", "Ptv", "PtvCatalog", "PreviewPolicy", "Colour", "Wallpaper", "WeatherFormat", "ControlRows", "SearchHit", "EventRules", "SetupFlow", "Intents", "KeyMap", "CrashRules", "UpdateRules"};
        Runnable[] tests = {AccordionTest::run, SpringMathTest::run, ListOpsTest::run, AppFilterTest::run, CategoryRulesTest::run, InputsTest::run, PtvTest::run, PtvCatalogTest::run, PreviewPolicyTest::run, ColourTest::run, WallpaperTest::run, WeatherFormatTest::run, ControlRowsTest::run, SearchHitTest::run, EventRulesTest::run, SetupFlowTest::run, IntentsTest::run, KeyMapTest::run, CrashRulesTest::run, UpdateRulesTest::run};
        int failed = 0;
        for (int i = 0; i < tests.length; i++) {
            try { tests[i].run(); System.out.println("PASS " + names[i]); }
            catch (Throwable t) { failed++; System.out.println("FAIL " + names[i] + ": " + t.getMessage()); }
        }
        System.out.println(T.checks + " checks, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
