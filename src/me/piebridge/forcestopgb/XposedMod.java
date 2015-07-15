package me.piebridge.forcestopgb;

import java.lang.reflect.Method;
import java.util.Set;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import me.piebridge.forcestopgb.hook.Hook;
import me.piebridge.forcestopgb.hook.HookResult;
import me.piebridge.forcestopgb.hook.SystemHook;

public class XposedMod implements IXposedHookZygoteInit {

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        SystemHook.initPreventRunning();

        // dynamic maintain force stopped package
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Hook.beforeActivity$onCreate((Activity) param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Hook.afterActivity$onDestroy((Activity) param.thisObject);
            }
        });

        // @formatter:off
        XposedHelpers.findAndHookMethod(IntentFilter.class, "match", String.class, String.class, String.class, Uri.class, Set.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                HookResult result = SystemHook.hookIntentFilter$match((IntentFilter) param.thisObject, param.args);
                if (!result.isNone()) {
                    param.setResult(result.getResult());
                }
            }
        });
        // @formatter:on

        XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int pid = (Integer) param.args[0];
                if (Process.myPid() == pid && Hook.stopSelf(pid)) {
                    param.setResult(null);
                }
            }
        });

        XposedHelpers.findAndHookMethod(System.class, "exit", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Hook.stopSelf(-1);
                param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "moveTaskToBack", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Hook.afterActivity$moveTaskToBack((Activity) param.thisObject, (Boolean) param.getResult());
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];
                if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
                    Hook.beforeActivity$startHomeActivityForResult((Activity) param.thisObject);
                }
            }
        });

        Class<?> ActivityManagerService = Class.forName("com.android.server.am.ActivityManagerService"); // NOSONAR
        for (Method method : ActivityManagerService.getDeclaredMethods()) {
            if ("startProcessLocked".equals(method.getName()) && method.getParameterTypes().length == 3) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!SystemHook.beforeActivityManagerService$startProcessLocked(param.args)) {
                            param.setResult(null);
                        }
                    }
                });
            }
        }
    }

}
