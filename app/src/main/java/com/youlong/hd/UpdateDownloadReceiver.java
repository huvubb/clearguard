package com.youlong.hd;
import android.content.*;
/** An untrusted broadcast cannot select a file or start an installation. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){if(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())&&intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID,-1)==AppUpdateDownload.id(c))try{AppUpdateDownload.notifyReady(c);}catch(Exception error){android.util.Log.w("AppUpdate","无法查询下载完成状态",error);}}
}
