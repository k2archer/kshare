package com.kwei.kshare;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ArrayList<String> mFilePath = new ArrayList<>();
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
                        String[] fileList = Smb.getFileList(uri);
                        if (fileList != null) {
                            mList.clear();
                            mList.addAll(Arrays.asList(fileList));
                            progressHandler.sendEmptyMessage(0);
                        }
                    }
                }).start();
            }
        });
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String uri = (String) mList.get(position);
            pd = ProgressDialog.show(this, "上传文件", "上传中...");
            new Thread(() -> {
                String title = "上传文件";
                String message = "上传成功!!";
                int count = 0;
                for (String path : mFilePath) {
                    String errorMessage = Smb.PutFile(uri, path);
                    if (errorMessage != null) {
                        count++;
                    }
                }
                if (count > 0) {
                    message = count + "个上传失败\n" + (mFilePath.size() - count) + "个上传成功";
                }
                String finalMessage = message;
                runOnUiThread(() -> {
                    pd.setTitle(title);
                    pd.setMessage(finalMessage);
                    Toast.makeText(MainActivity.this, finalMessage, Toast.LENGTH_LONG).show();
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                pd.dismiss();
            }).start();
            return true;
        });

        boolean isShare = getShareIntent();

        // 判断 WiFi 是否连接
        if (!isWiFiConnected(this)) {
            Toast.makeText(MainActivity.this, "WiFi 没有连接！", Toast.LENGTH_SHORT).show();
        } else if (isShare) {
            ScanSmbServer();
        }

    }

    private void ScanSmbServer() {
        pd = ProgressDialog.show(this, "扫描 SMB 服务器", "扫描中...");
        pd.setCanceledOnTouchOutside(false);
        pd.setCancelable(true);
        pd.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode,
                                 KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
                return false;
            }
        });

        new Thread(() -> {
            Smb smb = new Smb(MainActivity.this, new SmbCallback() {
                @Override
                public void scanFinished(List list) {
                    mList.clear();
                    mList.addAll(list);
                    progressHandler.sendEmptyMessage(0);
                }
            });
            smb.scanServer();
        }).start();
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
        String type = intent.getType();
        ArrayList<Uri> urilist = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); // 附件
        if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            if (type.startsWith("message/rfc822")) {
                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT); // 主题
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);       // 正文
                ArrayList<Uri> uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); // 附件
                if (uriList.size() > 0) {
                    mFilePath.clear();
                    for (int i = 0; i < uriList.size(); i++) {
                        Uri uri = uriList.get(i);
                        String filePath = String.valueOf(uri).substring(5); // 获得 file:// 后面路径
                        mFilePath.add(filePath);
                    }
                    return true;
                }
            } else if (type.startsWith("image/") || type.startsWith("video/")) {
                ArrayList<Uri> uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uriList.size() > 0) {
                    mFilePath.clear();
                    for (Uri uri : uriList) {
                        String filePath = String.valueOf(getRealPathFromUri(this, uri));
                        mFilePath.add(filePath);
                    }
                    return true;
                }
            } else if (type.startsWith("text/")) {
                ArrayList<Uri> uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uriList.size() > 0) {
                    mFilePath.clear();
                    for (int i = 0; i < uriList.size(); i++) {
                        Uri uri = uriList.get(i);
                        String filePath = String.valueOf(uri).substring(5); // 获得 file:// 后面路径
                        mFilePath.add(filePath);
                    }
                    return true;
                }
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)) {
                try {
                    Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                    String uriStr = String.valueOf(extras.getParcelable(Intent.EXTRA_STREAM));
                    if (uri != null) {
                        mFilePath.clear();
                        int end = uriStr.indexOf('/');
                        String filPathType = uriStr.substring(0, end);
                        if (filPathType.equals("file:")) {
                            String filePath = String.valueOf(uri).substring(5); // 获得 file:// 后面路径
                            mFilePath.add(filePath);
                        } else if (filPathType.equals("content:")) {
                            String filePath = String.valueOf(getRealPathFromUri(this, uri));
                            mFilePath.add(filePath);
                        }
                        return true;
                    }
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

    @Override
    public void onBackPressed() {
        if (mList != null) {
            String path = (String) mList.get(0);
            int start = path.indexOf('/');
            start = path.indexOf('/', start + 1);
            start = path.indexOf('/', start + 1);

            int end = path.lastIndexOf('/', path.length() - 2);
            end = path.lastIndexOf('/', end - 2);
            path = path.substring(0, end + 1);

            if (start > end) {
                ScanSmbServer();
                return;
            }

            String finalPath = path;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String[] fileList = Smb.getFileList(finalPath);
                    if (fileList != null) {
                        mList.clear();
                        mList.addAll(Arrays.asList(fileList));
                        progressHandler.sendEmptyMessage(0);
                    }
                }
            }).start();
        }

    }
}
