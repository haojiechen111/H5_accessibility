package com.best.h5bydcs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.View;
import android.view.View.AccessibilityDelegate;

public class CustomWebView extends WebView {

    private static final String TAG = "CustomWebView";
    private static final int SCROLL_DURATION = 800; // 滚动动画持续时间(毫秒)

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

    private int lastScrollY = 0;
    private long lastScrollTime = 0;
    private boolean isInSmoothScroll = false;
    
    /**
     * 监听滚动变化
     */
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastScrollTime;
        int distance = Math.abs(t - oldt);
        
        Log.d(TAG, "onScrollChanged: new=" + t + ", old=" + oldt + ", distance=" + distance + 
              ", timeDiff=" + timeDiff + "ms, isInSmoothScroll=" + isInSmoothScroll);
        
        // 检测accessibility瞬间滚动的特征：
        // 1. 滚动距离大于100px
        // 2. 距离上次滚动时间超过100ms（说明不是连续手动滑动）
        // 3. 不在平滑滚动过程中
        if (distance > 100 && timeDiff > 100 && !isInSmoothScroll) {
            Log.d(TAG, "检测到accessibility大幅瞬间滚动，启用平滑动画");
            
            // 先更新时间戳和标志位，避免回滚触发的onScrollChanged被误判
            lastScrollTime = currentTime;
            isInSmoothScroll = true;
            
            // 直接回滚到旧位置并立即执行平滑滚动
            CustomWebView.super.scrollTo(0, oldt);
            smoothScrollToWithJS(t);
            
            // 300ms后重置标志
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    isInSmoothScroll = false;
                }
            }, 350);
            
            lastScrollY = t;
            return; // 提前返回，避免重复更新
        }
        
        lastScrollY = t;
        lastScrollTime = currentTime;
    }

    /**
     * 处理平滑滚动
     * @param action accessibility滚动动作
     * @return 是否成功处理
     */
    private boolean handleSmoothScroll(int action) {
        Log.d(TAG, "handleSmoothScroll: action=" + getActionName(action));

        int scrollY = getScrollY();
        int height = getHeight();
        int contentHeight = computeVerticalScrollRange();
        
        // 计算滚动距离 (一屏高度)
        int scrollDistance = height;
        int targetY = scrollY;

        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:  // 向下滚动
                targetY = Math.min(scrollY + scrollDistance, contentHeight - height);
                break;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: // 向上滚动
                targetY = Math.max(scrollY - scrollDistance, 0);
                break;
        }

        // 使用JavaScript实现平滑滚动
        if (targetY != scrollY) {
            smoothScrollToWithJS(targetY);
            Log.d(TAG, String.format("平滑滚动: from=%d, to=%d, distance=%d", 
                scrollY, targetY, targetY - scrollY));
            return true;
        }
        
        return false;
    }
    
    /**
     * 使用JavaScript实现平滑滚动
     * @param targetY 目标Y坐标
     */
    private void smoothScrollToWithJS(int targetY) {
        int startY = getScrollY();  // 获取当前位置作为起点
        String js = String.format(
            "(function() {" +
            "  console.log('Java调用JS平滑滚动, 起点:', %d, '目标:', %d);" +
            "  var start = %d;" +  // 直接使用Java传入的起点，不读取
            "  var target = %d;" +
            "  var distance = target - start;" +
            "  var duration = 300;" +
            "  var startTime = performance.now();" +
            "  " +
            "  console.log('动画参数: start=' + start + ', target=' + target + ', distance=' + distance);" +
            "  " +
            "  if (distance === 0) {" +
            "    console.log('距离为0，跳过动画');" +
            "    return;" +
            "  }" +
            "  " +
            "  function animate(currentTime) {" +
            "    var elapsed = currentTime - startTime;" +
            "    var progress = Math.min(elapsed / duration, 1);" +
            "    var easeProgress = Math.sin((progress * Math.PI) / 2);" +
            "    var newScroll = start + distance * easeProgress;" +
            "    " +
            "    window.scrollTo(0, newScroll);" +
            "    console.log('动画进度:', (progress * 100).toFixed(0) + '%%', 'scrollY:', Math.round(newScroll));" +
            "    " +
            "    if (progress < 1) {" +
            "      requestAnimationFrame(animate);" +
            "    } else {" +
            "      console.log('动画完成');" +
            "    }" +
            "  }" +
            "  " +
            "  requestAnimationFrame(animate);" +
            "})();",
            startY, targetY, startY, targetY
        );
        
        Log.d(TAG, "执行JS平滑滚动");
        evaluateJavascript(js, null);
    }

    /**
     * 使用动画实现平滑滚动
     * @param targetY 目标Y坐标
     */
    private void animateScrollTo(int targetY) {
        final int startY = getScrollY();
        final int distance = targetY - startY;
        
        ValueAnimator animator = ValueAnimator.ofInt(startY, targetY);
        animator.setDuration(SCROLL_DURATION);
        animator.setInterpolator(new DecelerateInterpolator());
        
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int currentY = (int) animation.getAnimatedValue();
                scrollTo(0, currentY);
            }
        });
        
        animator.start();
    }

    /**
     * 获取动作名称(用于日志)
     */
    private String getActionName(int action) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
                return "SCROLL_FORWARD(向下)";
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                return "SCROLL_BACKWARD(向上)";
            default:
                return "UNKNOWN(" + action + ")";
        }
    }
}
