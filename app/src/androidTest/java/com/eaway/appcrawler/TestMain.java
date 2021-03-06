/*
 *  Android App Crawler using UiAutomator 2.0
 *
 *  AppCrawler is an automatic app UI crawler test tool, it scan your app screens, find testable views, and test them.
 *
 * Features:
 *     - Powerful screenshot tool for test, review and other reference.
 *     - No need of writing test script.
 *     - Able to detect ANR and Crash.
 *     - Report with  logcat, Screenshot and Human reproducible steps (compare to monkey)
 *     - Can dismiss common popups, including system and 3rd party.
 *
 * Screenshot folder:
 *     /sdcard/AppCrawler/<package>/
 *
 * Run test from command line:
 *     adb shell am instrument -e target [package] -w com.eaway.appcrawler.test/android.support.test.runner.AndroidJUnitRunner
 *
 *     [Options]
 *         -e target [package]                         package to be tested
 *         -e max-steps [number]                   maximum test steps (e.g. click, scroll), default 999
 *         -e max-depth [number]                  maximum depth from the root activity (default launchable activity), default 30
 *         -e max-screenshot [number]           maximum screenshot file, default 999
 *         -e max-screenloop [number]          maximum screens loop to avoid infinite loop, default 20
 *         -e max-runtime [second]                maximum run time in second, default 3600
 *         -e capture-steps [true|false]             take screenshot for every steps, this generate more screenshots (may duplicated), default false.
 *         -e random-text [true|false]              input some random text to EditText if any, default true.
 *         -e launch-timeout [millisecond]      timeout millisecond for launch app package, default 5000
 *         -e waitidle-timeout [millisecond]    timeout millisecond for wait app idle, default 100
 *
 *     [Examples]
 *         # adb shell am instrument -e target com.google.android.youtube -w com.eaway.appcrawler.test/android.support.test.runner.AndroidJUnitRunner
 *
 *         Please refer to the link below for other am instrument flags:
 *             http://developer.android.com/intl/zh-tw/tools/testing/testing_otheride.html#AMSyntax
 */

package com.eaway.appcrawler;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;

import com.eaway.appcrawler.common.UiHelper;
import com.eaway.appcrawler.performance.PerformanceMonitor;
import com.eaway.appcrawler.strategy.Crawler;
import com.eaway.appcrawler.strategy.DepthFirstCrawler;
import com.eaway.appcrawler.strategy.RandomCrawler;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * AppCrawler test using Android UiAutomator 2.0
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 18)
public class TestMain {
    private static final String TAG = Config.TAG;
    private static final String TAG_MAIN = Config.TAG_MAIN;
    private static final String TAG_DEBUG = Config.TAG_DEBUG;
    public static UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    ArrayList<String> txt = new ArrayList<>();

    @BeforeClass
    public static void beforeClass() throws Exception {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");
        grantAllPerms(InstrumentationRegistry.getTargetContext().getPackageName());

        // Get command line parameters
        getArguments();

        // Create screenshot folder
        File path = Environment.getExternalStorageDirectory();
        Config.sOutputDir = new File(String.format("%s/AppCrawler/%s", path.getAbsolutePath(), Config.sTargetPackage));
        deleteRecursive(Config.sOutputDir);
        if (!Config.sOutputDir.exists()) {
            if (!Config.sOutputDir.mkdirs()) {
                Log.d(TAG, "Failed to create screenshot folder: " + Config.sOutputDir.getPath());
            }
        }

        // Init File log
        Config.sFileLog = Config.sOutputDir + "/" + Config.TAG + ".log";
        FileLog.i(TAG_MAIN, "Version: " + Config.VERSION);
        FileLog.i(TAG_MAIN,"[model name]=" + mDevice.executeShellCommand("getprop ro.product.model").replace("\n",""));
        FileLog.i(TAG_MAIN,"[serial no]=" + mDevice.executeShellCommand("getprop ro.serialno").replace("\n",""));
        FileLog.i(TAG_MAIN,"[build info]=" + mDevice.executeShellCommand("getprop ro.build.description").replace("\n",""));


        // Init Performance log
        Config.sPerformanceLog = Config.sOutputDir + "/Performance.csv";
        PerformanceMonitor.init();

        //  Set timeout longer so we can see the ANR dialog?
        Configurator conf = Configurator.getInstance();
        conf.setActionAcknowledgmentTimeout(200L); // Generally, this timeout should not be modified, default 3000
        conf.setScrollAcknowledgmentTimeout(100L); // Generally, this timeout should not be modified, default 200
        conf.setWaitForIdleTimeout(0L);
        conf.setWaitForSelectorTimeout(0L);
        //conf.setKeyInjectionDelay(0L);
        logConfiguration();

        // Register UiWatchers: ANR, CRASH, ....
        UiHelper.registerAnrAndCrashWatchers();

        // Good practice to start from the home screen (launcher)
//        UiHelper.launchHome();
    }

    @AfterClass
    public static void afterClass() {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");

        //UiHelper.launchCrawlerApp();
    }

    @Before
    public void setUp() {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");

        //UiHelper.launchApp(Config.sTargetPackage);

    }

    @After
    public void tearDown() throws Exception {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");

        saveLogcat();
    }
    private String readFile(File filePath) throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filePath));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }
    private String getAccountData(){
        try {
            return readFile(new File(Environment.getExternalStorageDirectory(),"black.yml"));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Please check this path --sdcard/max.widget.black");
        }
    }
    private ArrayList getAccount(){
        try {

            JSONObject data = new JSONObject(getAccountData());
            Log.d("zxx", "123456");
            JSONArray accountCount = data.getJSONArray("zxx");//得到账号的总数
            Log.d("zxx", accountCount.length()+"");
//            String[][] str = new String[accountCount.length()][];
            ArrayList<Map<String,String>> black=new ArrayList();
            for(int i=0;i<accountCount.length();i++){
                Map map=new HashMap();
                JSONObject obj = accountCount.getJSONObject(i);//获取第i个账号的数据
                if(obj.has("bounds")){
                    map.put("bounds",obj.getString("bounds"));
                }
                if(obj.has("activity")){
                    map.put("activity",obj.getString("activity"));
                }

                if(obj.has("xpath")){
                    map.put("xpath",obj.getString("xpath"));
                }

                black.add(map);
            }
        return black;
        } catch (JSONException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Package file read failed, please check there tag: "
                    );
        }

    }
    public String getWlanAccount(String wlanName){
        Map<String ,String> map = new HashMap<>();
        try {
            JSONObject data = new JSONObject(getAccountData());
            JSONArray wlan = data.getJSONArray("wlan");
            for(int i=0;i<wlan.length();i++){
                JSONObject obj = wlan.getJSONObject(i);
                Log.e("getWlanAccount: ",""+obj.getString("name")+","+obj.getString("password"));
                map.put(obj.getString("name"),obj.getString("password"));
                Log.e("getWlanAccount: ","Map:"+map);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Package file read failed, please check " +e);
        }
        return map.get(wlanName);
    }
    @Test
    public void testSign() throws YamlException {



        File f = new File(Environment.getExternalStorageDirectory(),"black.yml");
        YamlReader reader = null;
        try {
            reader = new YamlReader(new FileReader(f));
        } catch (FileNotFoundException e) {
            System.out.println("zxx33333");
            e.printStackTrace();
        }
        try {

            ConfigYaml contact = reader.read(ConfigYaml.class);
            System.out.println(contact.sMaxDepth);//
        } catch (YamlException e) {
            System.out.println("zxx22222");
            e.printStackTrace();
            throw e;
        }
////        try {
////            https://www.jb51.net/article/176797.htm
//            Yaml yaml = new Yaml();
//            File f = new File(Environment.getExternalStorageDirectory(),"black.yml");
////            Object obj=yaml.load(new FileInputStream(f));
//            ConfigYaml  obj =(ConfigYaml) yaml.load(this.getClass().getClassLoader()
//                    .getResourceAsStream("black.yml"));
//
//            System.out.println(obj.getBLACKLIST_BUTTONS());
//            System.out.println(obj.getsMaxDepth());
//            System.out.println(obj.getsTargetPackage());
//            System.out.println(obj.getIGNORED_ACTIVITY());
//
////        } catch (Exception e1) {
////            e1.printStackTrace();
////        }



//        ArrayList<Map<String,String>> blacklist=getAccount();
//        for(int i = 0; i<blacklist.size() ; i++) {
//
//            if(blacklist.get(i).containsKey("bounds")){
//                Log.d("zxxlz", blacklist.get(i).get("bounds"));
//            }
//            if(blacklist.get(i).containsKey("activity")){
//                Log.d("zxxlz", blacklist.get(i).get("activity"));
//            }
//            if(blacklist.get(i).containsKey("xpath")){
//                Log.d("zxxlz", blacklist.get(i).get("xpath"));
//            }
//
//        }
//        UiScreen sLastScreen = null;
//        UiWidget sLastActionWidget = null;
//        UiScreen currentScreen = new UiScreen(sLastScreen,sLastActionWidget);
//        System.out.println("currentScreen.signature"+currentScreen.signature);
       

    }
    @Test
    public void myTest() throws Throwable {
        UiObject root = mDevice.findObject(new UiSelector().index(0));
        getDraw(root);
        Log.d("zxx", txt.toString());

    }
    public static String[] getAppPerms(String pacName){
        PackageManager pacManager = InstrumentationRegistry.getTargetContext().getPackageManager();

        try {
            return pacManager.getPackageInfo(pacName,PackageManager.GET_PERMISSIONS).requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void grantAllPerms(String pacName){
        PackageManager pacManager = InstrumentationRegistry.getTargetContext().getPackageManager();

        try {
            String[] permissions = getAppPerms(pacName) ;
            if(permissions == null) return;
            for (String p : permissions) {
                int code = pacManager.checkPermission(p, pacName);
                if (code != PackageManager.PERMISSION_GRANTED){
                    mDevice.executeShellCommand("pm grant " + pacName + " " + p);
                }
            }
        } catch (Throwable e) {
            Log.d(TAG,pacName + ": grant perms fail" );
            e.printStackTrace();
        }
    }
    private String drawClass(UiObject obj) throws UiObjectNotFoundException {
        StringBuilder UiObj = new StringBuilder();
        String classname="";
        for(int i = 0 ;i<1000;i++) {
            UiObject objC = obj.getChild(new UiSelector().instance(i));
            if (objC.exists()) {
                for (String tmp : objC.getClassName().split("\\.")) {
                    classname = tmp;
                }
                UiObj.append(classname).append(";");
//                Log.d("zxx", UiObj.toString());
//                Log.d("zxx", UiObj.toString());
            }else {
                break;
            }
        }
        return UiObj.toString();
    }

    private void getDraw(UiObject obj) throws UiObjectNotFoundException{
        String s = drawClass(obj);
        Log.d("zxx", s);

        for(int i = 0 ;i<1000;i++){
            UiObject objC = obj.getChild(new UiSelector().instance(i));
            if(objC.exists()){
                if(!objC.getText().equals("") && !txt.contains(objC.getText())) {
                    Log.d(TAG, objC.getText());
                    txt.add(objC.getText());
                }
            }else{
                UiObject objS;
                int m = 0;
                do{
                    objS = mDevice.findObject(new UiSelector().scrollable(true).instance(m++));
                    if(objS.exists()){
                        new UiScrollable(new UiSelector().className(objS.getClassName())).scrollForward();
                        String ss = drawClass(obj);
                        if(!ss.equals(s)){
                            getDraw(obj);
                        }
                        else {
                            break;
                        }
                    }
                } while (objS.exists());
                break;
            }
        }
    }
    @Test
    public void testMain() {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");
        grantAllPerms("com.eaway.appcrawler");
        Crawler crawler = new DepthFirstCrawler();

        try {
            crawler.run();
        } catch (IllegalStateException e) {
            Log.v(TAG, "IllegalStateException: UiAutomation not connected!");
        }
    }
    @Test
    public void testRandom() {
        Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");

        Crawler crawler = new RandomCrawler();

        try {
            crawler.run();
        } catch (IllegalStateException e) {
            Log.v(TAG, "IllegalStateException: UiAutomation not connected!");
        }
    }

    public static void getArguments() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        if (arguments.getString("target") != null) {
            Config.sTargetPackage = arguments.getString("target");
        }
        if (arguments.getString("max-steps") != null) {
            Config.sMaxSteps = Integer.valueOf(arguments.getString("max-steps"));
        }
        if (arguments.getString("max-depth") != null) {
            Config.sMaxDepth = Integer.valueOf(arguments.getString("max-depth"));
        }
        if (arguments.getString("max-runtime") != null) {
            Config.sMaxRuntime = Integer.valueOf(arguments.getString("max-runtime"));
        }
        if (arguments.getString("max-screenshot") != null) {
            Config.sMaxScreenshot = Integer.valueOf(arguments.getString("max-screenshot"));
        }
        if (arguments.getString("max-screensloop") != null) {
            Config.sMaxScreenLoop = Integer.valueOf(arguments.getString("max-screenloop"));
        }
        if (arguments.getString("launch-timeout") != null) {
            Config.sLaunchTimeout = Integer.valueOf((arguments.getString("launch-timeout")));
        }
        if (arguments.getString("waitidle-timeout") != null) {
            Config.sWaitIdleTimeout = Integer.valueOf((arguments.getString("waitidle-timeout")));
        }
        if (arguments.getString("capture-steps") != null) {
            Config.sCaptureSteps = (arguments.getString("capture-steps").compareTo("true") == 0);
        }
        if (arguments.getString("random-text") != null) {
            Config.sRandomText = (arguments.getString("random-text").compareTo("true") == 0);
        }
        File f = new File(Environment.getExternalStorageDirectory(),"config.yml");
        YamlReader reader = null;
        try {
            reader = new YamlReader(new FileReader(f));
        } catch (FileNotFoundException e) {
            System.out.println("zxx33333");
            e.printStackTrace();
        }
        try {

            ConfigYaml contact = reader.read(ConfigYaml.class);
            Config.sTargetPackage = contact.sTargetPackage;
            Config.sMaxDepth=contact.sMaxDepth;
            Config.BLACKLIST_BUTTONS=contact.BLACKLIST_BUTTONS_Description;
            Config.IGNORED_ACTIVITY=contact.IGNORED_ACTIVITY;
            System.out.println(Config.sTargetPackage );
            System.out.println(Config.sMaxDepth);
            System.out.println(Config.BLACKLIST_BUTTONS.toString());
            System.out.println(Config.IGNORED_ACTIVITY.toString());
        } catch (YamlException e) {
            System.out.println("zxx22222");
            e.printStackTrace();

        }



    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        boolean delete = fileOrDirectory.delete();
    }

    public static void logConfiguration() {
        Configurator conf = Configurator.getInstance();

        String log = String.format("ActionAcknowledgmentTimeout:%d," +
                        " KeyInjectionDelay:%d, " +
                        "ScrollAcknowledgmentTimeout:%d," +
                        " WaitForIdleTimeout:%d," +
                        " WaitForSelectorTimeout:%d",
                conf.getActionAcknowledgmentTimeout(),
                conf.getKeyInjectionDelay(),
                conf.getScrollAcknowledgmentTimeout(),
                conf.getWaitForIdleTimeout(),
                conf.getWaitForSelectorTimeout());

        FileLog.i(TAG_MAIN, log);

        FileLog.i(TAG_MAIN, "TargetPackage: " + Config.sTargetPackage +
                ", Debug: " + Config.sDebug +
                ", MaxSteps: " + Config.sMaxSteps +
                ", MaxDepth: " + Config.sMaxDepth +
                ", MaxRuntime: " + Config.sMaxRuntime +
                ", MaxScreenshot: " + Config.sMaxScreenshot +
                ", MaxScreenLoop: " + Config.sMaxScreenLoop +
                ", ScreenSignatueLength: " + Config.sScreenSignatueLength +
                ", RandomText: " + Config.sRandomText +
                ", CaptureSteps: " + Config.sCaptureSteps +
                ", LaunchTimeout: " + Config.sLaunchTimeout +
                ", WaitIdleTimeout: " + Config.sWaitIdleTimeout);
    }

    public static void saveLogcat() {
        File file = new File(Config.sOutputDir + "/AppCrawlerLogcat.log");
        try {
            boolean newFile = file.createNewFile();
            if (newFile) {
                String cmd = "logcat -d -s -v time -f " + file.getAbsolutePath() + " " + TAG;
                Runtime.getRuntime().exec(cmd);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
