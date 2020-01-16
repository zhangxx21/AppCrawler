package com.eaway.appcrawler.common;


import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eaway.appcrawler.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * UiScreen represent a unique screen that is worth testing.
 * UI of Android Activity acn change  dynamically (e.g. using Fragment), thus one Activity may produce more than one UiScreens
 */
public class UiScreen {
    private static final String TAG = Config.TAG;
    private static final String TAG_MAIN = Config.TAG_MAIN;
    private static final String TAG_DEBUG = Config.TAG_DEBUG;

    public UiDevice device;
    public UiScreen parentScreen;
    public UiWidget parentWidget; // Widget in parent screen that can take us to this screen
    public List<UiScreen> childScreenList; // Child screens
    public List<UiWidget> widgetList;   // Widgets in the screen that we interested in test (e.g. Clickable)
    public UiObject rootObject; // First UiObject in the screen
    public String pkg;  // Packages
    public String signature;    // Use to identify itself between other screens
    public String name; // Activity name
    public int depth = -1; // Depth in the UiTree
    public int id = -1;
    public int loop = 0; // Avoid infinite loop

    private boolean mFinished = false;    // True if all the child widgets have been tested

    public UiScreen(UiScreen parent, UiWidget widget) {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject root = device.findObject(new UiSelector().index(0));
        init(parent, widget, root);
    }

    public UiScreen(UiScreen parent, UiWidget widget, UiObject root) {
        init(parent, widget, root);
    }

    public void init(UiScreen parent, UiWidget widget, UiObject root) {
        assertThat(root, notNullValue());

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        parentScreen = parent;
        parentWidget = widget;
//        rootObject = root;
        rootObject = device.findObject(new UiSelector().resourceId("com.android.settings:id/list"));
        if(!rootObject.exists()){
            rootObject = root;
        }
        childScreenList = new ArrayList<UiScreen>();
        widgetList = new ArrayList<UiWidget>();
        try {
            pkg = root.getPackageName();
        } catch (UiObjectNotFoundException e) {
            e.printStackTrace();
        }
        signature = "";
        name = device.getCurrentActivityName(); // FIXME: deprecated
        depth = (parentScreen == null) ? 0 : parentScreen.depth + 1;
        id = -1;
        mFinished = false;

        // Keep current timeout configurations
        Configurator conf = Configurator.getInstance();
        long WaitForIdleTimeout = conf.getWaitForIdleTimeout();
        long WaitForSelectorTimeout = conf.getWaitForSelectorTimeout();
        long ActionAcknowledgmentTimeout = conf.getActionAcknowledgmentTimeout();
        long ScrollAcknowledgmentTimeout = conf.getScrollAcknowledgmentTimeout();
        conf.setWaitForIdleTimeout(0L);
        conf.setWaitForSelectorTimeout(0L);
        conf.setActionAcknowledgmentTimeout(0L);
        conf.setScrollAcknowledgmentTimeout(0L);


        // Build screen signature
        parseSignature(rootObject);
//        signature:FrameLayout;LinearLayout;FrameLayout;LinearLayout;ViewGroup;ImageButton;TextView;LinearLayout;TextView;TextView;LinearLayout;TextView;TextView;LinearLayout;TextView
        // Rollback timeout configurations
        conf.setWaitForIdleTimeout(WaitForIdleTimeout);
        conf.setWaitForSelectorTimeout(WaitForSelectorTimeout);
        conf.setActionAcknowledgmentTimeout(ActionAcknowledgmentTimeout);
        conf.setScrollAcknowledgmentTimeout(ScrollAcknowledgmentTimeout);


//        if (0 != pkg.compareToIgnoreCase(Config.sTargetPackage)) {
//            return;
//        }

        // Clickable
        int i = 0;
        UiObject clickable = null;
        String desc = "";
        List blacklist=Arrays.asList(Config.BLACKLIST_BUTTONS);
        do {
            clickable = null;
            clickable = device.findObject(new UiSelector().clickable(true).instance(i++));
            try {
                desc = clickable.getContentDescription();

            } catch (UiObjectNotFoundException e) {
                e.printStackTrace();
            }

            if(blacklist.contains(desc))
                continue;

            if (clickable != null && clickable.exists())
                widgetList.add(new UiWidget(clickable));
        } while (clickable != null && clickable.exists());

        // Nothing testable
        if (widgetList.size() == 0)
            mFinished = true;

        // Debug
        if (Config.sDebug) {
            Log.d(TAG_DEBUG, signature);
            for (UiWidget tmp : widgetList) {
                try {
                    Log.d(TAG_DEBUG, tmp.uiObject.getClassName());
                } catch (UiObjectNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public String toString() {
        // FIXME: Better to use StringBuilder
        String str = "name:" + name +
                ", id:" + id +
                ", depth:" + depth +
                ", finished:" + mFinished +
                ", signature:" + signature +
                ", widgets:" + widgetList.size();
        for (int i = 0; i < widgetList.size(); i++) {
            UiWidget widget = widgetList.get(i);
            str += " " + i + ":" + widget.isFinished();
        }
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UiScreen)) {
            return false;
        }
        UiScreen c = (UiScreen) o;
        return this.signature.equals(c.signature);
    }

    public void update() {
        isFinished();
    }

    public void setFinished(boolean finished) {
        mFinished = finished;

        if (mFinished == true) {
            for (UiWidget child : widgetList) {
                child.setFinished(mFinished);
            }
        }
    }

    public boolean isFinished() {
        if (mFinished == true)
            return mFinished;

        for (UiWidget child : widgetList) {
            if (!child.isFinished())
                return false;
        }
        mFinished = true;
        return mFinished;
    }

    private boolean parseSignature(UiObject uiObject) {
        System.out.println("currentScreen.signature"+uiObject);
        //Log.v(TAG, new Exception().getStackTrace()[0].getMethodName() + "()");
        if ((uiObject == null) || !uiObject.exists())
            return false;

        if (signature.length() > Config.sScreenSignatueLength)
            return true;

        try {
            // Screen signature: use classname list in the view hierarchy
            String classname = "";
            for (String tmp : uiObject.getClassName().split("\\.")) {
                classname = tmp;
            }
            signature = signature.concat(classname + ";");
            int count=uiObject.getChildCount();
            // Add all children recursively
            for (int i = 0; i < count; i++) {
                parseSignature(uiObject.getChild(new UiSelector().index(i)));
//                System.out.println("currentScreen.signature"+uiObject.getChild(new UiSelector().index(i)));
//                System.out.println("currentScreen.signature"+uiObject.getChildCount());

            }
        } catch (UiObjectNotFoundException e) {
            Log.e(TAG, "uiObject does not exists during parsing");
            return false;
        }

        return true;
    }
}
