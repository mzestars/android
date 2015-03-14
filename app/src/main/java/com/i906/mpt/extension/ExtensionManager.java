package com.i906.mpt.extension;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.support.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dalvik.system.PathClassLoader;

@Singleton
public class ExtensionManager {

    public static final String EXTENSION_PERMISSION = "com.i906.mpt.permission.MPT_EXTENSION";
    public static final String EXTENSION_METADATA = "com.i906.mpt.extension.ExtensionInfo";

    protected List<ExtensionInfo> mExtensionList;

    @Inject
    protected Context mContext;

    @Inject
    protected PackageManager mPackageManager;

    @Inject
    public ExtensionManager() { }

    public List<ExtensionInfo> getExtensions() {

        mExtensionList = new ArrayList<>();

        List<PackageInfo> packages = mPackageManager.getInstalledPackages(
                PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);

        for (PackageInfo pi : packages) {
            String[] perms = pi.requestedPermissions;

            if (perms != null && Arrays.asList(perms).contains(EXTENSION_PERMISSION)) {
                XmlResourceParser xrp = pi.applicationInfo.loadXmlMetaData(mPackageManager,
                        EXTENSION_METADATA);

                ExtensionInfo ei = parseExtensionInfo(pi.packageName, xrp);
                if (ei != null) mExtensionList.add(ei);
            }
        }

        return mExtensionList;
    }

    @Nullable
    public PrayerView getPrayerView(ExtensionInfo.Screen screen) {
        try {
            String apkName = mPackageManager.getApplicationInfo(screen.apk, 0).sourceDir;
            PathClassLoader myClassLoader = new PathClassLoader(apkName,
                    PrayerView.class.getClassLoader());

            Class<?> handler = Class.forName(screen.view, true, myClassLoader);
            Constructor c = handler.getConstructor(Context.class);
            Context extensionContext = mContext.createPackageContext(screen.apk,
                    Context.CONTEXT_RESTRICTED);

            return (PrayerView) c.newInstance(extensionContext);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    private ExtensionInfo parseExtensionInfo(String packageName, XmlResourceParser xrp) {
        if (xrp == null) return null;
        ExtensionInfo ei = new ExtensionInfo();

        try {
            int et = xrp.getEventType();
            String currentTag;
            String screenName = null;
            String screenView = null;

            while (et != XmlPullParser.END_DOCUMENT) {
                if (et == XmlPullParser.START_TAG) {
                    currentTag = xrp.getName();
                    if ("mpt-extension".equals(currentTag)) {
                        ei.name = xrp.getAttributeValue(null, "name");
                        ei.author = xrp.getAttributeValue(null, "author");
                    }
                    if ("screen".equals(currentTag)) {
                        screenName = xrp.getAttributeValue(null, "name");
                        screenView = xrp.getAttributeValue(null, "view");
                    }
                } else if (et == XmlPullParser.END_TAG) {
                    if ("screen".equals(xrp.getName())) {
                        ExtensionInfo.Screen s = new ExtensionInfo.Screen();
                        s.apk = packageName;
                        s.name = screenName;
                        s.view = screenView;
                        ei.screens.add(s);
                    }
                }
                et = xrp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return ei;
    }
}