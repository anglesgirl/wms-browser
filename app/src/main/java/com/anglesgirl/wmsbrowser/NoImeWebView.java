package com.anglesgirl.wmsbrowser;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

/**
 * NoImeWebView：硬性拦截软键盘。
 * - forceImeEnabled=false（默认）：返回不关联 Editable 的 BaseInputConnection，
 *   系统认为此 View 不可编辑 → 任何方式（JS focus / 触摸 / 扫码枪模拟键盘）都不弹键盘。
 * - 仅用户点「开启输入法」→ enableIme() 才放行真实 IME。
 * 适配 Android 6+ (minSdk 23)。
 */
public class NoImeWebView extends WebView {

    private boolean forceImeEnabled = false;

    public NoImeWebView(Context context) {
        super(context);
    }

    public NoImeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoImeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void enableIme() {
        forceImeEnabled = true;
    }

    public void disableIme() {
        forceImeEnabled = false;
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        if (!forceImeEnabled) {
            // 关键：不关联任何 Editable → 系统不弹软键盘
            return new BaseInputConnection(this, false);
        }
        return super.onCreateInputConnection(editorInfo);
    }
}
