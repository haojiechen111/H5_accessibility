package com.best.h5bydcs;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;
import android.view.View.AccessibilityDelegate;

public class CustomWebView extends WebView {

    private static final String TAG = "CustomWebView";

    public CustomWebView(Context context) {
        super(context);
        initAccessibilityDelegate();
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAccessibilityDelegate();
    }

    public CustomWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAccessibilityDelegate();
    }

    private void initAccessibilityDelegate() {
        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                
                // 从 aria-label JSON 中提取 contentDescription 并设置
                CharSequence text = info.getText();
                if (text != null && text.toString().startsWith("{")) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(text.toString());
                        if (json.has("contentDescription")) {
                            String contentDesc = json.getString("contentDescription");
                            if (contentDesc != null && !contentDesc.isEmpty()) {
                                info.setContentDescription(contentDesc);
                                Log.d(TAG, "设置 WebView contentDescription: " + contentDesc);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略非JSON文本
                    }
                }
                
                // 去除冗余的 contentDescription
                CharSequence contentDesc = info.getContentDescription();
                CharSequence textContent = info.getText();
                // 如果 contentDescription 和 text 内容一样，则清空 contentDescription
                if (contentDesc != null && textContent != null && contentDesc.toString().equals(textContent.toString())) {
                    info.setContentDescription(null);
                }
            }
            
            @Override
            public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                android.view.accessibility.AccessibilityNodeProvider provider = super.getAccessibilityNodeProvider(host);
                if (provider != null) {
                    return new CustomAccessibilityNodeProvider(provider);
                }
                return provider;
            }
        });
    }
    
    /**
     * 自定义 AccessibilityNodeProvider 用于拦截和修改虚拟节点
     */
    private class CustomAccessibilityNodeProvider extends android.view.accessibility.AccessibilityNodeProvider {
        private android.view.accessibility.AccessibilityNodeProvider mOriginalProvider;
        
        public CustomAccessibilityNodeProvider(android.view.accessibility.AccessibilityNodeProvider originalProvider) {
            mOriginalProvider = originalProvider;
        }
        
        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            AccessibilityNodeInfo info = mOriginalProvider.createAccessibilityNodeInfo(virtualViewId);
            if (info != null) {
                // 从 text 字段中提取 contentDescription
                CharSequence text = info.getText();
                if (text != null && text.toString().startsWith("{")) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(text.toString());
                        if (json.has("contentDescription")) {
                            String contentDesc = json.getString("contentDescription");
                            if (contentDesc != null && !contentDesc.isEmpty()) {
                                info.setContentDescription(contentDesc);
                                Log.d(TAG, "虚拟节点设置 contentDescription: " + contentDesc + " for virtualViewId: " + virtualViewId);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略非JSON文本
                    }
                }
            }
            return info;
        }
        
        @Override
        public boolean performAction(int virtualViewId, int action, android.os.Bundle arguments) {
            return mOriginalProvider.performAction(virtualViewId, action, arguments);
        }
        
        @Override
        public java.util.List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text, int virtualViewId) {
            return mOriginalProvider.findAccessibilityNodeInfosByText(text, virtualViewId);
        }
        
        @Override
        public AccessibilityNodeInfo findFocus(int focus) {
            return mOriginalProvider.findFocus(focus);
        }
    }

}
