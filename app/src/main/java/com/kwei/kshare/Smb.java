package com.kwei.kshare;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jcifs.netbios.NbtAddress;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

class Smb {
    private int SCAN_TIMEOUT = 1000 * 5; // 默认 5 秒
    private Context mContext;
    private List<String> mListServer;
    private SmbCallback mCallback;

    public Smb(Context context, SmbCallback callback) {
        mContext = context;
        mCallback = callback;
    }

    public void scanServer() {
        if (mListServer == null) {
            mListServer = Collections.synchronizedList(new ArrayList<>());
        }

        ExecutorService service = Executors.newCachedThreadPool();

        String localIP = getHostAddress();
        String IP = localIP.substring(0, localIP.lastIndexOf('.') + 1);

//        String remoteUrl = "smb://guest:@" + IP + 112 + '/';
//        Log.i("TTAG", "scanServer: " + remoteUrl);
//        service.submit(new SmbRunnable(remoteUrl));

        for (int i = 2; i < 255; i++) {
            service.submit(new SmbRunnable("smb://guest:@" + IP + i + '/'));
        }

        try {
            Thread.sleep(SCAN_TIMEOUT);
            service.shutdownNow();
            mCallback.scanFinished(mListServer);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public List getServerList() {
        return mListServer;
    }

    public class SmbRunnable implements Runnable {
        private String remoteUrl;

        SmbRunnable(String remoteUrl) {
            this.remoteUrl = remoteUrl;
        }

        public void run() {
            SmbFile smbFile;
            try {
                smbFile = new SmbFile(remoteUrl);
                smbFile.connect(); // 等待连接直到连接超时
                Log.i("TTAG", "run: " + remoteUrl + " is connected.");
                String server = smbFile.getServer();
                NbtAddress[] addresses = NbtAddress.getAllByAddress(server);
                String remoteHostName = addresses[0].getHostName();  // 获得远程计算机名
                mListServer.add(remoteUrl);
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    /**
     * * 把局域网中的共享文件复制并保存在本地目录中
     * * @param remoteUrl 共享路径 如：smb//username:password@192.168.0.1/smb/file.txt
     * * @param localDir 本地路径 如：D:/
     */
    public static void smbGet(String remoteUrl, String localDir) {
        InputStream in = null;
        OutputStream out = null;
        try {
            SmbFile smbFile = new SmbFile(remoteUrl);
            String fileName = smbFile.getName();
            File localFile = new File(localDir + File.separator + fileName);
            in = new BufferedInputStream(new SmbFileInputStream(smbFile));
            out = new BufferedOutputStream(new FileOutputStream(localFile));
            byte[] buffer = new byte[1024];
            while ((in.read(buffer)) != -1) {
                out.write(buffer);
                buffer = new byte[1024];
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String PutFile(String remoteUrl, String localFilePath) {
        String message = null;

        try {
            SmbFile smbFile = new SmbFile(remoteUrl);
//            smbFile.connect(); // 等待连接直到连接超时
            if (smbFile.isDirectory()) {
                File localFile = new File(localFilePath);
                String fileName = localFile.getName();
                SmbFile sFile = new SmbFile(remoteUrl + fileName);
                if (sFile.exists()) {
                    message = "文件已存在!";
                } else if (!smbFile.canWrite()) {
                    Smb.smbPut(remoteUrl, localFilePath);
//                    message = "上传成功!";
                } else {
                    message = "无写入权限!";
                }
            } else {
                message = "这不是一个目录!";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return message;
    }

    /**
     * 把本地的文件上传到局域网共享目录下
     *
     * @param remoteUrl     共享路径 如：smb//username:password@192.168.0.1/share
     * @param localFilePath 本地路径 如：D:/file.txt
     */
    public static void smbPut(String remoteUrl, String localFilePath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            File localFile = new File(localFilePath);
            String fileName = localFile.getName();
            SmbFile remoteFile = new SmbFile(remoteUrl + "/" + fileName);
            in = new BufferedInputStream(new FileInputStream(localFile));
            out = new BufferedOutputStream(new SmbFileOutputStream(remoteFile));
            byte[] buffer = new byte[1024];
            while ((in.read(buffer)) != -1) {
                out.write(buffer);
                buffer = new byte[1024];
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getHostAddress() {
        return getWIFILocalIpAddress(mContext);
    }

    /***
     * 使用 WIFI 时，获取本机IP地址
     * @param mContext
     * @return
     */
    private String getWIFILocalIpAddress(Context mContext) {
        Context context = mContext.getApplicationContext();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return formatIpAddress(ipAddress);
        }

        return null;
    }

    private String formatIpAddress(int ipAddress) {
        return (ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                (ipAddress >> 24 & 0xFF);
    }
}
