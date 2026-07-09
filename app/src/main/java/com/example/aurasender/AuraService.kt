package com.example.aurasender

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AuraService : AccessibilityService() {
    private lateinit var limitManager: AuraLimitManager
    private var isProcessing = false
    private var currentUser: String? = null

    override fun onCreate() {
        super.onCreate()
        limitManager = AuraLimitManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || isProcessing) return

        try {
            val eventPackage = event.packageName?.toString() ?: return
            if (eventPackage != getTargetPackage()) return

            val rootNode = rootInActiveWindow ?: return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    
                    if (isProfilePage(rootNode)) {
                        processProfile(rootNode)
                    } else if (isListPage(rootNode)) {
                        processList(rootNode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AuraService", "Error: ${e.message}")
            isProcessing = false
        }
    }

    private fun getTargetPackage(): String {
        val sharedPref = getSharedPreferences("AuraLimits", MODE_PRIVATE)
        return sharedPref.getString("target_package", "") ?: ""
    }

    private fun isListPage(node: AccessibilityNodeInfo): Boolean {
        return !isProfilePage(node)
    }

    private fun isProfilePage(node: AccessibilityNodeInfo): Boolean {
        val profileIndicators = listOf("Profile", "پروفایل", "Bio", "بیو", "About", "درباره", "Username", "نام کاربری")
        val allNodes = findAllNodes(node)
        return allNodes.any { it.text?.let { text ->
            profileIndicators.any { text.contains(it, ignoreCase = true) }
        } ?: false }
    }

    private fun processList(rootNode: AccessibilityNodeInfo) {
        if (isProcessing) return
        isProcessing = true
        Log.d("AuraService", "🔍 در صفحه لیست هستیم...")

        val userNodes = findClickableNodesInList(rootNode)
        if (userNodes.isEmpty()) {
            val listNode = findScrollableNode(rootNode)
            if (listNode != null) {
                listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Log.d("AuraService", "📜 اسکرول به پایین انجام شد")
            }
            Handler(Looper.getMainLooper()).postDelayed({ isProcessing = false }, 1500)
            return
        }

        val firstUserNode = userNodes[0]
        currentUser = getUsernameFromNode(firstUserNode) ?: "User_${System.currentTimeMillis()}"
        Log.d("AuraService", "👤 کلیک روی کاربر: $currentUser")
        clickOnUser(firstUserNode)
    }

    private fun processProfile(rootNode: AccessibilityNodeInfo) {
        if (isProcessing || currentUser == null) return
        isProcessing = true
        Log.d("AuraService", "📄 در صفحه پروفایل: $currentUser")

        if (!limitManager.canSendAura(currentUser!!)) {
            Log.d("AuraService", "❌ محدودیت 5 بار برای $currentUser")
            goBack()
            return
        }

        if (!limitManager.canSendToday()) {
            Log.d("AuraService", "❌ محدودیت روزانه تمام شده")
            goBack()
            return
        }

        val auraButton = findAuraButton(rootNode)
        if (auraButton != null) {
            Log.d("AuraService", "🔥 دکمه نارنجی پیدا شد!")
            clickAuraButton(auraButton, currentUser!!)
        } else {
            Log.d("AuraService", "❌ دکمه نارنجی پیدا نشد")
            goBack()
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findScrollableNode(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findClickableNodesInList(listNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        val allNodes = findAllNodes(listNode)
        for (node in allNodes) {
            if (node.isClickable && !node.isScrollable && node.text != null && node.text.toString().isNotEmpty()) {
                clickableNodes.add(node)
            }
        }
        return clickableNodes
    }

    private fun findAllNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        nodes.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> nodes.addAll(findAllNodes(child)) }
        }
        return nodes
    }

    private fun clickOnUser(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Handler(Looper.getMainLooper()).postDelayed({ isProcessing = false }, 2000)
    }

    private fun getUsernameFromNode(node: AccessibilityNodeInfo): String? {
        node.text?.let { text ->
            if (text.contains("@")) return text.substringAfter("@").toString().trim()
            if (text.length in 3..20 && text.matches(Regex("[a-zA-Z0-9_\\u0600-\\u06FF]+"))) return text.trim()
        }
        return null
    }

    private fun findAuraButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val auraTexts = listOf("AURA", "هاله", "ارسال هاله", "Send Aura", "Aura")
        val allNodes = findAllNodes(rootNode)
        
        val screenHeight = resources.displayMetrics.heightPixels
        val minY = (screenHeight * 0.3).toInt()
        val maxY = (screenHeight * 0.7).toInt()

        for (node in allNodes) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.top in minY..maxY && node.isClickable) {
                node.text?.let { text ->
                    if (auraTexts.any { text.contains(it, ignoreCase = true) }) {
                        return node
                    }
                }
            }
        }
        return null
    }

    private var clickCountForUser = 0
    private fun clickAuraButton(node: AccessibilityNodeInfo, username: String) {
        if (clickCountForUser < 5 && limitManager.canSendAura(username) && limitManager.canSendToday()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            limitManager.incrementDailyCount()
            clickCountForUser++
            Log.d("AuraService", "✅ کلیک $clickCountForUser انجام شد برای $username")

            val delay = limitManager.getRandomDelay()
            Handler(Looper.getMainLooper()).postDelayed({
                clickAuraButton(node, username)
            }, delay)
        } else {
            clickCountForUser = 0
            goBack()
        }
    }

    private fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Handler(Looper.getMainLooper()).postDelayed({
            isProcessing = false
            currentUser = null
            Log.d("AuraService", "🔙 بازگشت به لیست")
        }, 1500)
    }

    override fun onInterrupt() {
        isProcessing = false
    }
}
