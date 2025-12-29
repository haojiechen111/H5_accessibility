# TsWebView与CustomWebView对比分析

## 日志分析

### TsWebView实际运行情况

**第一次滚动（向下）：0 → 1362**
```
14:32:57.801  检测到accessibility大幅瞬间滚动，回滚并使用平滑动画
14:32:57.801  onScrollChanged: new=0, old=1362 (回滚触发)
14:32:57.802  执行JS平滑滚动到位置: 1362
14:32:57.803  Java调用JS平滑滚动, 目标: 1362
14:32:57.836  onScrollChanged: new=1362, old=0 (JS动画触发)
14:32:58.111  滚动动画完成
```

**第二次滚动（向上）：1362 → 0**
```
14:33:01.919  检测到accessibility大幅瞬间滚动，回滚并使用平滑动画
14:33:01.919  onScrollChanged: new=1362, old=0 (回滚触发)
14:33:01.920  执行JS平滑滚动到位置: 0
14:33:01.923  Java调用JS平滑滚动, 目标: 0
14:33:01.935  onScrollChanged: new=0, old=1362 (JS动画触发)
14:33:02.245  滚动动画完成
```

## 核心实现对比

### 1. onScrollChanged方法

| 特性 | TsWebView | CustomWebView (v1.4) |
|------|-----------|----------------------|
| **时间戳更新时机** | if块外部 | if块内部（提前更新）✅ |
| **标志位设置** | if块内第一行 | if块内第二行（在时间戳后）✅ |
| **回滚方式** | `scrollTo(0, oldt)` | `CustomWebView.super.scrollTo(0, oldt)` ✅ |
| **变量更新位置** | 仅在if外部 | if内+外（有重复）⚠️ |
| **提前返回** | 无 | 有return语句 ✅ |

**代码对比：**

```java
// TsWebView (第241-256行)
if (distance > 100 && timeDiff > 100 && !isInSmoothScroll) {
    LogUtils.i(TAG, "检测到accessibility大幅瞬间滚动，回滚并使用平滑动画");
    isInSmoothScroll = true;              // 第1步：设置标志
    scrollTo(0, oldt);                     // 第2步：回滚
    smoothScrollToWithJS(t);               // 第3步：JS动画
    postDelayed(() -> {
        isInSmoothScroll = false;
    }, 350);
}
lastScrollY = t;                           // if外更新（总是执行）
lastScrollTime = currentTime;              // if外更新（总是执行）
```

```java
// CustomWebView (第72-96行)
if (distance > 100 && timeDiff > 100 && !isInSmoothScroll) {
    Log.d(TAG, "检测到accessibility大幅瞬间滚动，启用平滑动画");
    
    lastScrollTime = currentTime;          // 第1步：先更新时间戳 ✅
    isInSmoothScroll = true;               // 第2步：设置标志
    
    CustomWebView.super.scrollTo(0, oldt); // 第3步：回滚 ✅
    smoothScrollToWithJS(t);               // 第4步：JS动画
    
    postDelayed(() -> {
        isInSmoothScroll = false;
    }, 350);
    
    lastScrollY = t;                       // if内更新
    return;                                // 提前返回 ✅
}

lastScrollY = t;                           // if外也有更新（冗余）
lastScrollTime = currentTime;
```

### 2. smoothScrollToWithJS方法

| 特性 | TsWebView | CustomWebView (v1.4) |
|------|-----------|----------------------|
| **起点获取** | JS中读取`window.scrollY` | Java中`getScrollY()`传入 ✅ |
| **参数验证** | 无 | 有distance===0检查 ✅ |
| **时间API** | `Date.now()` | `performance.now()` ✅ |
| **缓动函数** | 线性 (`progress`) | 正弦 (`Math.sin`) ✅ |
| **调试日志** | 基础日志 | 详细参数+进度日志 ✅ |

**JS代码对比：**

```javascript
// TsWebView (第264-294行)
(function() {
    console.log('Java调用JS平滑滚动, 目标:', targetY);
    var start = window.scrollY || document.documentElement.scrollTop;  // JS读取
    var target = targetY;
    var distance = target - start;
    var duration = 300;
    var startTime = Date.now();  // Date.now()
    
    function animate() {
        var currentTime = Date.now();
        var elapsed = currentTime - startTime;
        var progress = Math.min(elapsed / duration, 1);
        var easeProgress = progress;  // 线性缓动
        var newScroll = start + distance * easeProgress;
        
        window.scrollTo(0, newScroll);
        
        if (progress < 1) {
            requestAnimationFrame(animate);
        } else {
            console.log('滚动动画完成');
        }
    }
    requestAnimationFrame(animate);
})();
```

```javascript
// CustomWebView (第141-176行)
(function() {
    console.log('Java调用JS平滑滚动, 起点:', startY, '目标:', targetY);
    var start = startY;  // Java传入，不读取 ✅
    var target = targetY;
    var distance = target - start;
    var duration = 300;
    var startTime = performance.now();  // performance.now() ✅
    
    console.log('动画参数: start=' + start + ', target=' + target + ', distance=' + distance);
    
    if (distance === 0) {  // 参数验证 ✅
        console.log('距离为0，跳过动画');
        return;
    }
    
    function animate(currentTime) {
        var elapsed = currentTime - startTime;
        var progress = Math.min(elapsed / duration, 1);
        var easeProgress = Math.sin((progress * Math.PI) / 2);  // 正弦缓动 ✅
        var newScroll = start + distance * easeProgress;
        
        window.scrollTo(0, newScroll);
        console.log('动画进度:', (progress * 100).toFixed(0) + '%', 'scrollY:', Math.round(newScroll));
        
        if (progress < 1) {
            requestAnimationFrame(animate);
        } else {
            console.log('动画完成');
        }
    }
    requestAnimationFrame(animate);
})();
```

## 问题分析

### TsWebView存在的问题

1. **时序问题**：
   - 不提前更新`lastScrollTime`
   - 回滚触发的`onScrollChanged`仍然看到大的timeDiff
   - 虽然被`isInSmoothScroll`阻挡，但逻辑不够优雅

2. **JS起点不准确**：
   - 使用`window.scrollY`读取起点
   - 在回滚和JS执行之间可能有时序问题
   - 可能导致起点!=oldt

3. **线性缓动**：
   - `easeProgress = progress`
   - 动画缺乏加速/减速效果
   - 用户体验不够自然

4. **精度问题**：
   - 使用`Date.now()`毫秒精度
   - `performance.now()`微秒精度更准确

5. **缺少验证**：
   - 没有检查distance是否为0
   - 可能执行无效动画

### CustomWebView的优势

1. ✅ **提前更新时间戳**：回滚触发的onScrollChanged看到小timeDiff
2. ✅ **Java获取起点**：准确可靠，不依赖JS读取
3. ✅ **正弦缓动**：动画更自然流畅
4. ✅ **高精度时间**：performance.now()微秒级
5. ✅ **参数验证**：避免distance=0的无效动画
6. ✅ **详细日志**：便于调试和问题定位
7. ✅ **提前返回**：避免重复更新变量

### CustomWebView需要改进

1. ⚠️ **变量重复更新**：
   - if内更新了lastScrollY
   - if外又有更新（虽然有return，但代码不够简洁）
   
2. 建议优化：移除if内的lastScrollY更新，只保留return

## 性能对比

| 指标 | TsWebView | CustomWebView |
|------|-----------|---------------|
| 回滚触发次数 | 1次 | 1次 |
| JS动画触发次数 | 多次（每帧） | 多次（每帧） |
| 时间精度 | 毫秒 | 微秒 ✅ |
| 动画流畅度 | 线性 | 正弦（更流畅）✅ |
| 调试便利性 | 基础日志 | 详细日志 ✅ |

## 实际效果

### TsWebView
- ✅ 动画正常工作
- ✅ 无明显视觉问题
- ⚠️ 线性缓动稍显生硬
- ⚠️ 日志信息较少

### CustomWebView
- ✅ 动画正常工作
- ✅ 正弦缓动更流畅
- ✅ 参数验证更严谨
- ✅ 调试信息详细
- ✅ 代码更健壮

## 结论

1. **TsWebView的实现是基础可行的**，动画能正常工作
2. **CustomWebView是优化增强版**，在多个方面有改进：
   - 更准确的起点获取
   - 更流畅的缓动曲线
   - 更严谨的参数验证
   - 更详细的调试日志
   - 更高的时间精度

3. **两者都能解决accessibility滚动无动画的问题**
4. **CustomWebView在用户体验和代码健壮性上更胜一筹**

## 建议

对于新项目，推荐使用CustomWebView的实现（v1.4版本），因为：
- 代码更健壮
- 动画更流畅
- 调试更方便
- 可维护性更好

对于已有TsWebView的项目，如果动画效果满意，可以保持现状；如果追求更好的用户体验，建议升级到CustomWebView的实现方式。
