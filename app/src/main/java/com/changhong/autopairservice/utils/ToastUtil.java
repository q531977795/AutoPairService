package com.changhong.autopairservice.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.changhong.autopairservice.R;

public class ToastUtil extends Toast {
    private static ToastUtil defaultInstance;
    private Context context;

    public static synchronized ToastUtil getInstance(Context ctx) {
        ToastUtil toastUtil;
        synchronized (ToastUtil.class) {
            if (defaultInstance == null) {
                defaultInstance = new ToastUtil(ctx);
            }
            toastUtil = defaultInstance;
        }
        return toastUtil;
    }

    public ToastUtil(Context context) {
        super(context);
        this.context = context;
    }

    public void showErrString(String message) {
        errShowString(null, message + "", 0);
    }

    public void showErrString(String message, Drawable drawable) {
        errShowString(drawable, message + "", 1);
    }

    public void showErrString(String message, Drawable drawable, int duration) {
        errShowString(drawable, message + "", duration);
    }

    private void errShowString(Drawable drawable, String message, int duration) {
        View errToast = ((LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.layout_mytoast, null);
        TextView tvMessage = (TextView) errToast.findViewById(R.id.share_txt_toastmsg);
        if (drawable != null) {
            drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
            tvMessage.setCompoundDrawables(drawable, null, null, null);
        }
        tvMessage.setText(message);
        setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 145);
        setDuration(duration);
        setView(errToast);
        show();
    }
}