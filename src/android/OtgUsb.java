package sj.li.usb;

import org.apache.cordova.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.net.Uri;
import android.app.Activity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * This class echoes a string called from JavaScript.
 */
public class OtgUsb extends CordovaPlugin {
    private static final int REQUEST_CODE = 1001;
    private CallbackContext permissionCallback;
    private long totalBytesToCopy = 0;
    private long copiedBytes = 0;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("getUsbPath")) {
            this.getUsbPath(callbackContext);
            return true;
        } else if (action.equals("moveToUSB")) {
            List<String> sourcePaths = new ArrayList<>();
            for (int i = 0; i < args.getJSONArray(0).length(); i++) {
                sourcePaths.add(args.getJSONArray(0).getString(i));
            }
            String targetDirectory = args.getString(1);
            boolean deleteIfExists = args.getBoolean(2);
            this.moveToUSB(sourcePaths, targetDirectory, deleteIfExists, callbackContext);
            return true;
        } else if (action.equals("getPermission")) {
            this.getPermission(callbackContext);
            return true;
        }
        return false;
    }

    private void getUsbPath(final CallbackContext callbackContext) {
        String filePath = "/proc/mounts";
        File file = new File(filePath);
        List<String> lineList = new ArrayList<>();
        InputStream inputStream =null;


        try {
            inputStream = new FileInputStream(file);
            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "GBK");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = "";
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.contains("vfat")) {
                        lineList.add(line);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            String err = e.getMessage();
            callbackContext.error(err);
        } catch (IOException e) {
            e.printStackTrace();
            String err = e.getMessage();
            callbackContext.error(err);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    String err = e.getMessage();
                    callbackContext.error(err);
                }
            }
        }


        String editPath = lineList.get(lineList.size() - 1);
        int start = editPath.indexOf("/mnt");
        int end = editPath.indexOf(" vfat");
        String path = editPath.substring(start, end);

        if (path != null && path.length() > 0) {
            callbackContext.success(path);
        } else {
            callbackContext.error("没有路径");
        }
    }

    private void moveToUSB(List<String> sourcePaths, String targetDirectory, boolean deleteIfExists, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run(){
                // 先计算所有文件的大小
                for (String sourcePath : sourcePaths) {
                    File sourceFile = new File(sourcePath);
                    totalBytesToCopy += sourceFile.exists() && sourceFile.isFile() ? sourceFile.length() : 0;
                }

                for (String sourcePath : sourcePaths) {
                    File sourceFile = new File(sourcePath);
                    File targetFile = new File(targetDirectory, sourceFile.getName());

                    try (FileInputStream inStream = new FileInputStream(sourceFile);
                         FileOutputStream outStream = new FileOutputStream(targetFile)) {

                        byte[] buffer = new byte[1024];
                        int read;

                        while ((read = inStream.read(buffer)) != -1) {
                            outStream.write(buffer, 0, read);

                            copiedBytes += read; // 更新已复制的字节数

                            int progress = (int) Math.floor((double) copiedBytes / totalBytesToCopy * 100);

                            PluginResult result = new PluginResult(PluginResult.Status.OK, progress);
                            result.setKeepCallback(true); // 保持回调以继续发送进度

                            callbackContext.sendPluginResult(result);
                        }

                    // 强制将缓冲区内容刷新到磁盘
                    outStream.flush();
                    // 同步文件描述符以确保所有数据都已写入物理媒介
                    outStream.getFD().sync();

                    if (deleteIfExists) {
                        // 删除源文件
                        sourceFile.delete();
                    }

                        // 如果需要，可在复制每个文件成功后添加相应逻辑
                    } catch (IOException e) {
                        String errorMessage = e.getMessage();
                        PluginResult result = new PluginResult(PluginResult.Status.OK, errorMessage);
                        result.setKeepCallback(false); // 关闭回调
                        callbackContext.sendPluginResult(result);
                    } finally {
                        totalBytesToCopy = 0;
                        copiedBytes = 0;
                    }
                }
                // 下载完成后发送完成消息
                PluginResult result = new PluginResult(PluginResult.Status.OK, "OK");
                result.setKeepCallback(false); // 关闭回调
                callbackContext.sendPluginResult(result);
            }
        });
    }

    private void getPermission(final CallbackContext callbackContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 获取当前Activity的Context
            final CordovaInterface cordova = this.cordova;
            Context context = cordova.getActivity();

            if (Environment.isExternalStorageManager()) {
                Toast.makeText(context, "三江助手已经获得U您的盘管理权限", Toast.LENGTH_LONG).show();
                callbackContext.success("HAVE POWER");
            } else {
                Toast.makeText(context, "请打开权限，否则三江助手无权使用您的U盘！", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + cordova.getActivity().getPackageName()));
                permissionCallback = callbackContext;
                 // 委托给宿主Activity启动新的Intent并监听结果
                cordova.setActivityResultCallback(this);
                cordova.startActivityForResult(this, intent, REQUEST_CODE);
//                 callbackContext.error("NO POWER");
            }
        } else {
            callbackContext.success("NOT ANDROID11");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && permissionCallback != null) {
            // 使用保存的permissionCallback执行相应的回调逻辑
            if (resultCode == Activity.RESULT_OK) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(cordova.getActivity(), "已经获得权限", Toast.LENGTH_LONG).show();
                    permissionCallback.success("HAVE POWER");
                } else {
                    // 权限未实际授予，可能需要进一步检查或提示用户
                    Toast.makeText(cordova.getActivity(), "获取权限失败", Toast.LENGTH_LONG).show();
                    permissionCallback.error("NO POWER");
                }
            } else {
                permissionCallback.error("NO POWER");
            }
            // 清除保存的CallbackContext
            permissionCallback = null;
        }
    }
}
