package com.eaway.appcrawler;

import java.io.File;

/**
 * Configurations
 */
public class Config {
    public static final String VERSION = "1.0.0.0";
    public static final String TAG = "AppCrawler";
    public static final String TAG_MAIN = TAG + "Main";
    public static final String TAG_DEBUG = TAG + "Debug";

    public static int sLaunchTimeout = 5000;
    public static int sWaitIdleTimeout = 100;
    public static int sMaxDepth = 10;
    public static int sMaxSteps = 1999;
    public static int sMaxRuntime = 13600;
    public static int sMaxScreenshot = 999;
    public static int sMaxScreenLoop = 120;
    public static int sScreenSignatueLength = 360;

    public static boolean sDebug = true;
    public static boolean sCaptureSteps = true;
    public static boolean sRandomText = true;

    public static File sOutputDir;
    public static String sFileLog;
    public static String sPerformanceLog;

    public static String sCrawlerPackage = "com.eaway.appcrawler";
//    public static String sTargetPackage = "com.android.deskclock";
//    public static String sTargetPackage = "com.android.calculator2"com.android.contacts;
    public static String sTargetPackage = "com.android.settings";


    // Activities to be ignored
    public static final String[] IGNORED_ACTIVITY = {
            "Feedback & Help", "意見與協助"
    };

    // Common buttons that we can handle.
    public static final String[] COMMON_BUTTONS = {
            "OK", "Cancel", "Yes", "No",
            "確定", "取消", "是", "否"
    };
    public static final String[] BLACKLIST_BUTTONS = {
            "Search settings", "Back"

    };

    // Text for EditText testing
    public static final String[] RANDOM_TEXT = {
            "LOVE", "Latte", "Coffee", "Beer",
            "Taiwan", "Taipei", "Saturday", "Morning", "December",
            "Steve", "Jordan", "Michael",
    };

}
