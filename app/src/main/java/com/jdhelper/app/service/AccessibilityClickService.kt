package com.jdhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.util.Log
import com.jdhelper.app.service.LogConsole
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityClick"

        private var instance: AccessibilityClickService? = null

        fun getInstance(): AccessibilityClickService? = instance

        var isServiceRunning = false
            private set

        // 点击持续时间（毫秒），默认100ms，最小50ms
        var clickDuration: Long = 100L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        LogConsole.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        LogConsole.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceRunning = false
    }

    /**
     * 执行全局点击
     */
    @Suppress("DEPRECATION")
    fun performGlobalClick(x: Int, y: Int): Boolean {
        return try {
            val dispatchResult = dispatchGesture(
                createClickGesture(x, y),
                null,
                null
            )
            LogConsole.d(TAG, "执行点击 ($x, $y): $dispatchResult")
            dispatchResult
        } catch (e: Exception) {
            LogConsole.e(TAG, "点击失败", e)
            false
        }
    }

    /**
     * 创建点击手势
     */
    private fun createClickGesture(x: Int, y: Int): GestureDescription {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val builder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(
            path,
            0,
            clickDuration
        )
        builder.addStroke(strokeDescription)
        return builder.build()
    }

    /**
     * 查找目标按钮并点击
     * @return null表示未找到按钮，true表示点击成功且按钮包含"浏览"，false表示点击成功但不包含"浏览"
     */
    @Suppress("DEPRECATION")
    fun findAndClickTarget(): Boolean? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            val result = findTargetButtonWithText(rootNode)
            if (result != null) {
                performGlobalClick(result.x, result.y)
                // 检查按钮文字是否包含"浏览"
                val containsBrowse = result.buttonText.contains("浏览")
                LogConsole.d(TAG, "点击目标按钮: (${result.x}, ${result.y}), 包含浏览: $containsBrowse")
                containsBrowse
            } else {
                null
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 查找目标按钮，返回坐标和按钮文字
     */
    private fun findTargetButtonWithText(root: AccessibilityNodeInfo): TargetButtonResult? {
        val result = searchTargetButton(root)
        // 注意：searchTargetButton 内部已经处理了递归回收，这里不再重复回收
        return result
    }

    /**
     * 递归搜索目标按钮
     */
    private fun searchTargetButton(node: AccessibilityNodeInfo): TargetButtonResult? {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        // 检查是否是目标按钮
        if (isTargetKeyword(text) || isTargetKeyword(contentDesc)) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                return TargetButtonResult(
                    x = bounds.centerX(),
                    y = bounds.centerY(),
                    buttonText = text
                )
            }
        }

        // 递归搜索子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = searchTargetButton(child)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    /**
     * 判断是否包含目标关键词
     */
    private fun isTargetKeyword(text: String): Boolean {
        val keywords = listOf("点击浏览", "点击解锁", "立即支付", "提交订单", "去结算")
        return keywords.any { text.contains(it) }
    }

    /**
     * 检查是否有弹窗（客服/店铺/分类）
     */
    @Suppress("DEPRECATION")
    fun hasPopup(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            checkPopupPresent(rootNode)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 递归检查弹窗
     */
    private fun checkPopupPresent(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        // 检查是否是弹窗关键词
        if (text.contains("客服") || text.contains("店铺") || text.contains("分类") ||
            contentDesc.contains("客服") || contentDesc.contains("店铺") || contentDesc.contains("分类")) {
            // 检查是否可点击
            if (node.isClickable || node.childCount > 0) {
                return true
            }
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (checkPopupPresent(child)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }

    /**
     * 执行返回手势
     */
    fun performBack(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_BACK)
            LogConsole.d(TAG, "执行返回: $result")
            result
        } catch (e: Exception) {
            LogConsole.e(TAG, "返回失败", e)
            false
        }
    }

    /**
     * 创建返回手势（已弃用，使用performGlobalAction）
     */
    @Deprecated("使用performBack()替代")
    private fun createBackGesture(): GestureDescription {
        val path = Path()
        val builder = GestureDescription.Builder()
        return builder.build()
    }

    /**
     * 执行上滑手势
     */
    fun performSwipeUp(): Boolean {
        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val path = Path()
            // 从底部80%位置滑动到顶部30%位置
            path.moveTo(screenWidth / 2f, screenHeight * 0.8f)
            path.lineTo(screenWidth / 2f, screenHeight * 0.3f)

            val builder = GestureDescription.Builder()
            // 增加滑动时间使动作更明显
            val strokeDescription = GestureDescription.StrokeDescription(
                path, 0, 500
            )
            builder.addStroke(strokeDescription)

            val gesture = builder.build()
            val result = dispatchGesture(gesture, null, null)
            LogConsole.d(TAG, "执行上滑: $result, 屏幕: ${screenWidth}x${screenHeight}")
            result
        } catch (e: Exception) {
            LogConsole.e(TAG, "上滑失败", e)
            false
        }
    }

    /**
     * 执行下滑手势（用于返回顶部）
     */
    fun performSwipeDown(): Boolean {
        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val path = Path()
            path.moveTo(screenWidth / 2f, screenHeight * 0.3f)
            path.lineTo(screenWidth / 2f, screenHeight * 0.8f)

            val builder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(
                path, 0, 500
            )
            builder.addStroke(strokeDescription)

            val result = dispatchGesture(builder.build(), null, null)
            LogConsole.d(TAG, "执行下滑: $result")
            result
        } catch (e: Exception) {
            LogConsole.e(TAG, "下滑失败", e)
            false
        }
    }

    /**
     * 查找目标按钮
     */
    @Suppress("DEPRECATION")
    fun findTargetButton(): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            ButtonFinder(applicationContext).findTargetButton(rootNode)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 查找第一阶段按钮（一键送礼）
     */
    @Suppress("DEPRECATION")
    fun findFirstStageButton(): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            searchButtonByKeywords(rootNode, listOf("一键送礼", "送给"))
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 查找第二阶段按钮（付款并赠送）
     */
    @Suppress("DEPRECATION")
    fun findSecondStageButton(): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            searchButtonByKeywords(rootNode, listOf("付款并赠送"))
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 通过关键词搜索按钮 - 优化版本
     */
    private fun searchButtonByKeywords(root: AccessibilityNodeInfo, keywords: List<String>): Point? {
        // 预编译关键词匹配器
        val keywordArray = keywords.toTypedArray()
        return searchButtonOptimized(root, keywordArray)
    }

    /**
     * 优化的递归搜索 - 避免不必要的对象创建
     */
    private fun searchButtonOptimized(node: AccessibilityNodeInfo, keywords: Array<String>): Point? {
        // 快速检查：先检查text，如果没有匹配再检查contentDescription
        val text = node.text?.toString()
        if (text != null) {
            for (keyword in keywords) {
                if (text.contains(keyword)) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) {
                        return Point(bounds.centerX(), bounds.centerY())
                    }
                }
            }
        }

        // 检查contentDescription
        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null && contentDesc != text) {
            for (keyword in keywords) {
                if (contentDesc.contains(keyword)) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) {
                        return Point(bounds.centerX(), bounds.centerY())
                    }
                }
            }
        }

        // 递归搜索子节点
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = searchButtonOptimized(child, keywords)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    /**
     * 结果数据类
     */
    data class TargetButtonResult(
        val x: Int,
        val y: Int,
        val buttonText: String
    )
}