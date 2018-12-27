package com.kwei.kshare;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jcifs.smb.SmbFile;

public class MainActivity extends AppCompatActivity {

    private String mFilePath;
    private List mList;
    private ListView mListView;
    private ListAdapter mAdapter;
    private ProgressDialog pd;

    private final ProgressHandler progressHandler = new ProgressHandler(this);

    static class ProgressHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public ProgressHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mActivity.get() == null) return;
            MainActivity activity = mActivity.get();
            activity.pd.dismiss();
            activity.mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mList = new ArrayList();

        mListView = findViewById(R.id.list_view);
        mAdapter = new ListAdapter(this, mList);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String uri = (String) mList.get(position);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SmbFile smbFile = new SmbFile(uri);
                            smbFile.connect(); // 等待连接直到连接超时
                            if (smbFile.isDirectory()) {
                                String[] fileList = smbFile.list();
                                for (int i = 0; i < fileList.length; i++) {
                                    fileList[i] = uri + fileList[i] + '/';
                                }
                                mList.clear();
                                mList.addAll(Arrays.asList(fileList));
                                progressHandler.sendEmptyMessage(0);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }).start();
            }
        });
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String uri = (String) mList.get(position);
            pd = ProgressDialog.show(this, "上传文件", "上传中...");
            new Thread(() -> {
                String title = "上传失败";
                String message = "发生异常!";
                try {
                    SmbFile smbFile = new SmbFile(uri);
                    smbFile.connect(); // 等待连接直到连接超时
                    if (smbFile.isDirectory()) {
                        File localFile = new File(mFilePath);
                        String fileName = localFile.getName();
                        SmbFile sFile = new SmbFile(uri + fileName);
                        if (sFile.exists()) {
                            title = "上传失败";
                            message = "文件已存在!";
                        } else if (!smbFile.canWrite()) {
                            Smb.smbPut(uri, mFilePath);
                            title = "上传文件";
                            message = "上传成功!";
                        } else {
                            title = "上传失败";
                            message = "无写入权限!";
                        }
                    } else {
                        title = "上传失败";
                        message = "这不是一个目录!";
                    }
                    pd.setTitle(title);
                    pd.setMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    pd.setTitle(title);
                    pd.setMessage(message);
                    String finalMessage = message;
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, finalMessage, Toast.LENGTH_LONG).show();
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    pd.dismiss();
                }
            }).start();
            return true;
        });

        boolean isShare = getShareIntent();

        // 判断 WiFi 是否打开
        if (!isWiFiConnected(this)) {
            Toast.makeText(MainActivity.this, "WiFi 没有连接！", Toast.LENGTH_SHORT).show();
        } else if (isShare) {
            pd = ProgressDialog.show(this, "扫描 SMB 服务器", "扫描中...");
            new Thread(() -> {
                Smb smb = new Smb(MainActivity.this, new SmbCallback() {
                    @Override
                    public void scanFinished(List list) {
                        mList.addAll(list);
                        progressHandler.sendEmptyMessage(0);
                    }
                });
                smb.scanServer();
            }).start();
        }

    }

    public boolean isWiFiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null
                && networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    private boolean getShareIntent() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                try {
                    Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                    Log.i("TTAG", "uri:" + uri.toString());
                    mFilePath = String.valueOf(getRealPathFromUri(this, uri));
                    Log.d("TTAG", "文件路径信息：" + mFilePath);
                    return true;
                } catch (Exception e) {
                }
            }
        }
        return false;
    }

    public static String getRealPathFromUri(Context context, Uri contentUri) {
        String path = null;
        String[] data = {MediaStore.Images.Media.DATA};
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(contentUri, data, null, null, null)) {
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                path = cursor.getString(columnIndex);
            }
        }
        return path;
    }
}
