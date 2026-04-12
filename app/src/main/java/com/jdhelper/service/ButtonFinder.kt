package com.jdhelper.service

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonFinder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ButtonFinder"
        // 目标按钮文字关键词（按优先级排序）
        private val TARGET_KEYWORDS = listOf("点击浏览", "点击解锁", "立即支付", "提交订单", "去结算")
        // 默认位置偏移
        private const val DEFAULT_OFFSET_X = 100
        private const val DEFAULT_OFFSET_Y = 70
    }

    /**
     * 查找目标按钮
     * 注意：不回收 root 节点，由调用者负责回收
     * @param root  AccessibilityNodeInfo 根节点
     * @return 按钮中心点坐标，如果未找到返回null
     */
    fun findTargetButton(root: AccessibilityNodeInfo?): Point? {
        if (root == null) return null

        return findButtonByKeywords(root)
    }

    /**
     * 通过关键词查找按钮（优先查找可点击的叶子节点）
     */
    private fun findButtonByKeywords(node: AccessibilityNodeInfo): Point? {
        // 优先检查当前节点是否是符合条件的可点击节点
        if (isTargetNode(node) && isClickableNode(node)) {
            return getNodeCenter(node)
        }

        // 检查当前节点是否符合条件（即使不是叶子节点）
        if (isTargetNode(node)) {
            val center = getNodeCenter(node)
            if (center != null) {
                return center
            }
        }

        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findButtonByKeywords(child)
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
     * 判断节点是否是叶子节点（可点击的最小单位）
     */
    private fun isClickableNode(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否可点击
        if (node.isClickable) {
            return true
        }
        // 或者检查是否是叶子节点（没有子节点）
        return node.childCount == 0
    }

    /**
     * 判断节点是否符合目标条件
     */
    private fun isTargetNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        // 匹配关键词
        val keywordMatch = TARGET_KEYWORDS.any { keyword ->
            text.contains(keyword) || contentDesc.contains(keyword)
        }

        if (!keywordMatch) return false

        // 过滤掉非按钮类型的节点（如文本容器）
        val isButtonType = className.contains("Button") ||
                className.contains("TextView") ||
                className.contains("ImageView")

        return isButtonType || node.isClickable
    }

    /**
     * 获取节点中心点坐标（使用更精确的边界计算）
     */
    private fun getNodeCenter(node: AccessibilityNodeInfo): Point? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 如果边界无效，返回null
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) {
            return null
        }

        // 计算中心点，并进行微调校正
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        return Point(centerX, centerY)
    }

    /**
     * 获取默认点击位置（屏幕右下角向左140，向上100）
     */
    fun getDefaultPosition(): Point {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        return Point(
            metrics.widthPixels - DEFAULT_OFFSET_X,
            metrics.heightPixels - DEFAULT_OFFSET_Y
        )
    }
}