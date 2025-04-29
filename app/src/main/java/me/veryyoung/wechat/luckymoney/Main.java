package me.veryyoung.wechat.luckymoney;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedBridge.invokeOriginalMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;


/**
 * 微信红包自动领取Xposed模块
 * 
 * 本模块实现以下功能：
 * 1. 监听微信红包通知并自动领取
 * 2. 自动点击红包领取按钮
 * 3. 提供命令控制（开启/关闭机器人、设置延时等）
 */
public class Main implements IXposedHookLoadPackage {

    // 微信应用包名
    private static final String WECHAT_PACKAGE_NAME = "com.tencent.mm";
    
    // 红包接收UI类名
    private static final String LUCKY_MONEY_RECEIVE_UI_CLASS_NAME = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
    
    // 通知类名
    private static final String NOTIFICATION_CLASS_NAME = "com.tencent.mm.booter.notification.b";

    // 红包机器人开关
    static boolean open = true;
    
    // 延时开关
    static boolean delay = false;
    
    // 延时时间（毫秒）
    static long delayTime = 300;

    /**
     * Xposed框架回调方法，处理应用加载事件
     * 
     * @param lpparam 应用加载参数
     */
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (lpparam.packageName.equals(WECHAT_PACKAGE_NAME)) {
            // 钩子函数：监听红包通知并自动领取
            findAndHookMethod(NOTIFICATION_CLASS_NAME, lpparam.classLoader, "a", NOTIFICATION_CLASS_NAME, String.class, String.class, int.class, int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!open) {
                        return;
                    }
                    
                    // 检查消息类型是否为红包
                    String msgtype = "436207665";
                    if (param.args[3].toString().equals(msgtype)) {
                        // 解析XML消息
                        String xmlmsg = param.args[2].toString();
                        String xl = xmlmsg.substring(xmlmsg.indexOf("<msg>"));
                        
                        // 解析nativeurl
                        String p = "nativeurl";
                        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                        factory.setNamespaceAware(true);
                        XmlPullParser pz = factory.newPullParser();
                        pz.setInput(new StringReader(xl));
                        int v = pz.getEventType();
                        String saveurl = "";
                        
                        while (v != XmlPullParser.END_DOCUMENT) {
                            if (v == XmlPullParser.START_TAG) {
                                if (pz.getName().equals(p)) {
                                    pz.nextToken();
                                    saveurl = pz.getText();
                                    break;
                                }
                            }
                            v = pz.next();
                        }
                        
                        // 构建红包对象
                        String nativeurl = saveurl;
                        Uri nativeUrl = Uri.parse(nativeurl);
                        int msgType = Integer.parseInt(nativeUrl.getQueryParameter("msgtype"));
                        int channelId = Integer.parseInt(nativeUrl.getQueryParameter("channelid"));
                        String sendId = nativeUrl.getQueryParameter("sendid");
                        String headImg = "";
                        String nickName = "";
                        String sessionUserName = param.args[1].toString();
                        String ver = "v1.0";
                        
                        final Object ab = newInstance(
                            findClass("com.tencent.mm.plugin.luckymoney.c.ab", lpparam.classLoader),
                            msgType, channelId, sendId, nativeurl, headImg, nickName, sessionUserName, ver
                        );
                        
                        // 获取应用上下文
                        Context context = (Context) callStaticMethod(
                            findClass("com.tencent.mm.sdk.platformtools.z", lpparam.classLoader), "getContext"
                        );
                        
                        // 构建红包处理对象
                        final Object i = newInstance(
                            findClass("com.tencent.mm.plugin.luckymoney.c.i", lpparam.classLoader), context, null
                        );
                        
                        // 处理延时
                        if (delay) {
                            Thread.sleep(delayTime);
                        }
                        
                        // 调用红包处理方法
                        callMethod(i, "a", ab, false);
                    }
                }
            });

            // 钩子函数：自动点击红包领取按钮
            findAndHookMethod(LUCKY_MONEY_RECEIVE_UI_CLASS_NAME, lpparam.classLoader, "d", int.class, int.class, String.class, "com.tencent.mm.s.j", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class receiveUI = findClass(LUCKY_MONEY_RECEIVE_UI_CLASS_NAME, lpparam.classLoader);

                    // 获取领取按钮
                    Button button = (Button) callStaticMethod(receiveUI, "e", param.thisObject);
                    
                    // 检查按钮状态并点击
                    if (button.isShown() && button.isClickable()) {
                        button.performClick();
                        callMethod(param.thisObject, "finish");
                    } else {
                        callMethod(param.thisObject, "finish");
                    }
                }
            });

            // 钩子函数：处理命令输入
            findAndHookMethod("com.tencent.mm.pluginsdk.ui.chat.ChatFooter$2", lpparam.classLoader, "onClick", View.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
                    // 获取聊天输入框
                    Object chatFooter = getObjectField(methodHookParam.thisObject, "iWt");
                    EditText editText = (EditText) getObjectField(chatFooter, "fdR");
                    
                    // 获取命令
                    String command = editText.getEditableText().toString().trim();
                    Context context = (Context) callStaticMethod(
                        findClass("com.tencent.mm.sdk.platformtools.z", lpparam.classLoader), "getContext"
                    );
                    
                    // 处理命令
                    if (command.equals("open")) {
                        open = true;
                        Toast.makeText(context, "红包机器人打开", Toast.LENGTH_SHORT).show();
                    } else if (command.equals("close")) {
                        open = false;
                        Toast.makeText(context, "红包机器人关闭", Toast.LENGTH_SHORT).show();
                    } else if (command.equals("delay")) {
                        delay = true;
                        Toast.makeText(context, "延时已经开启", Toast.LENGTH_SHORT).show();
                    } else if (command.equals("nodelay")) {
                        delay = false;
                        Toast.makeText(context, "延时已经关闭", Toast.LENGTH_SHORT).show();
                    } else if (command.matches("delay\\d{2,}")) {
                        String tmp = command.replace("delay", "");
                        delayTime = Long.valueOf(tmp);
                        Toast.makeText(context, "延时设置成功: " + tmp + "毫秒", Toast.LENGTH_SHORT).show();
                    } else if (command.equals("help")) {
                        Toast.makeText(context, 
                            "命令说明：\n" +
                            "open打开红包机器人\n" +
                            "close关闭红包机器人\n" +
                            "delay开启延时\n" +
                            "nodelay关闭延时\n" +
                            "delay后面跟毫秒数设置延时\n" +
                            "help 显示本帮助", 
                            Toast.LENGTH_LONG).show();
                    } else {
                        // 未识别命令，调用原始方法
                        return invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                    }
                    
                    // 清空输入框
                    editText.setText("");
                    return null;
                }
            });
        }
    }
}