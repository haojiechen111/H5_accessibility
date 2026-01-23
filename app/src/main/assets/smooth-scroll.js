/**
 * 通用平滑滚动方案 - 统一处理横向和竖向滚动
 * 
 * 使用方法：
 * 1. 给需要平滑滚动的容器添加 class="smooth-scroll"
 * 2. 在</body>前引入此脚本：<script src="smooth-scroll.js"></script>
 * 
 * 支持：
 * - 横向滚动容器（overflow-x: auto）
 * - 竖向滚动容器（overflow-y: auto）
 * - body元素的整页滚动
 */

(function() {
  'use strict';
  
  // 配置参数
  var config = {
    duration: 300,          // 动画时长（毫秒）
    distanceThreshold: 400, // 触发距离阈值（像素）- 提高到400px减少误判
    timeThreshold: 200      // 时间间隔阈值（毫秒）- 提高到200ms减少误判
  };
  
  // 等待DOM加载完成
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
  
  function init() {
    // 查找所有需要平滑滚动的容器
    var containers = document.querySelectorAll('.smooth-scroll');
    
    if (containers.length === 0) {
      console.warn('⚠️ [SmoothScroll] 没有找到 .smooth-scroll 容器');
      return;
    }
    
    console.log('✅ [SmoothScroll] 找到 ' + containers.length + ' 个滚动容器');
    
    // 为每个容器启用平滑滚动
    containers.forEach(function(container, index) {
      enableSmoothScroll(container, index);
    });
  }
  
  function enableSmoothScroll(container, index) {
    // 判断容器类型
    var isBody = container.tagName.toLowerCase() === 'body';
    var containerName = isBody ? 'body' : (container.className || 'container-' + index);
    
    // 保存容器状态
    var state = {
      lastScrollLeft: isBody ? window.pageXOffset : container.scrollLeft,
      lastScrollTop: isBody ? window.pageYOffset : container.scrollTop,
      lastTime: Date.now(),
      isAnimating: false
    };
    
    // 对于body元素，监听window的scroll事件
    var scrollTarget = isBody ? window : container;
    var getScrollLeft = isBody ? function() { return window.pageXOffset; } : function() { return container.scrollLeft; };
    var getScrollTop = isBody ? function() { return window.pageYOffset; } : function() { return container.scrollTop; };
    var setScrollLeft = isBody ? function(val) { window.scrollTo(val, window.pageYOffset); } : function(val) { container.scrollLeft = val; };
    var setScrollTop = isBody ? function(val) { window.scrollTo(window.pageXOffset, val); } : function(val) { container.scrollTop = val; };
    
    // 监听滚动事件
    scrollTarget.addEventListener('scroll', function() {
      var currentScrollLeft = getScrollLeft();
      var currentScrollTop = getScrollTop();
      var currentTime = Date.now();
      
      var timeDiff = currentTime - state.lastTime;
      var distanceX = Math.abs(currentScrollLeft - state.lastScrollLeft);
      var distanceY = Math.abs(currentScrollTop - state.lastScrollTop);
      var distance = Math.max(distanceX, distanceY);

      // 详细日志：每次滚动都打印
            console.log('📊 [SmoothScroll]', containerName, '滚动事件:', {
              scrollLeft: currentScrollLeft,
              scrollTop: currentScrollTop,
              lastScrollLeft: state.lastScrollLeft,
              lastScrollTop: state.lastScrollTop,
              distanceX: distanceX,
              distanceY: distanceY,
              maxDistance: distance,
              timeDiff: timeDiff,
              isAnimating: state.isAnimating
            });
      // 检测是否为语音触发的滚动
      // 特征：滚动距离大 且 时间间隔长 且 不在动画中
      // 提高阈值减少手势大幅滑动的误判
      if (distance > config.distanceThreshold
          && timeDiff > config.timeThreshold 
          && !state.isAnimating) {
        
        console.log('🎯 [SmoothScroll] 检测到语音滚动:', containerName, 
                    'distanceX=' + distanceX + 'px',
                    'distanceY=' + distanceY + 'px',
                    'timeDiff=' + timeDiff + 'ms');
        
        // 保存目标位置
        var targetScrollLeft = currentScrollLeft;
        var targetScrollTop = currentScrollTop;
        
        console.log('↩️ [SmoothScroll] 回滚到起点:', {
          from: { left: currentScrollLeft, top: currentScrollTop },
          to: { left: state.lastScrollLeft, top: state.lastScrollTop }
        });
        
        // 回滚到起始位置
        setScrollLeft(state.lastScrollLeft);
        setScrollTop(state.lastScrollTop);
        
        // 更新状态
        state.lastTime = currentTime;
        state.isAnimating = true;
        
        // 准备动画参数
        var startScrollLeft = state.lastScrollLeft;
        var startScrollTop = state.lastScrollTop;
        var distanceToScrollX = targetScrollLeft - startScrollLeft;
        var distanceToScrollY = targetScrollTop - startScrollTop;
        var startTime = performance.now();
        
        console.log('🎬 [SmoothScroll] 开始动画:', {
          start: { left: startScrollLeft, top: startScrollTop },
          target: { left: targetScrollLeft, top: targetScrollTop },
          distance: { x: distanceToScrollX, y: distanceToScrollY },
          duration: config.duration
        });
        
        // 执行平滑动画
        function animate(currentTime) {
          var elapsed = currentTime - startTime;
          var progress = Math.min(elapsed / config.duration, 1);
          
          // 缓动函数：easeOutSine（开始快，结束慢）
          var easeProgress = Math.sin(progress * Math.PI / 2);
          
          var newScrollLeft = startScrollLeft + distanceToScrollX * easeProgress;
          var newScrollTop = startScrollTop + distanceToScrollY * easeProgress;
          
          setScrollLeft(newScrollLeft);
          setScrollTop(newScrollTop);
          
          if (progress < 1) {
            requestAnimationFrame(animate);
          } else {
            // 动画完成
            state.isAnimating = false;
            console.log('✨ [SmoothScroll] 动画完成:', containerName, {
              final: { left: getScrollLeft(), top: getScrollTop() }
            });
          }
        }
        
        requestAnimationFrame(animate);
        
        // 更新最后位置
        state.lastScrollLeft = targetScrollLeft;
        state.lastScrollTop = targetScrollTop;
        return;
      }
      
      // 正常滚动（手指滑动），只更新状态
      state.lastScrollLeft = currentScrollLeft;
      state.lastScrollTop = currentScrollTop;
      state.lastTime = currentTime;
    });
    
    console.log('✅ [SmoothScroll] 已启用:', containerName);
  }
  
  // 导出配置修改接口（可选）
  window.SmoothScrollConfig = config;
  
})();
