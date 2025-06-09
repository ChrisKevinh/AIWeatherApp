package com.example.weatherdemo.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
import com.example.weatherdemo.R
import com.example.weatherdemo.databinding.PopupMenuModernBinding

class ModernPopupMenu(
    private val context: Context,
    private val anchorView: View
) {
    
    private lateinit var binding: PopupMenuModernBinding
    private lateinit var popupWindow: PopupWindow
    private var isEditMode = false
    
    // 回调接口
    interface OnMenuItemClickListener {
        fun onEditModeClick()
        fun onSettingsClick() 
        fun onAboutClick()
    }
    
    private var menuItemClickListener: OnMenuItemClickListener? = null
    
    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener) {
        menuItemClickListener = listener
    }
    
    fun show(editMode: Boolean = false) {
        isEditMode = editMode
        setupPopupWindow()
        setupClickListeners()
        
        // 🔧 新增：编辑模式下隐藏其他选项，只保留完成编辑
        updateMenuVisibility()
        
        // 计算显示位置
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        
        // 在锚点右下方显示
        val x = location[0] + anchorView.width - popupWindow.width
        val y = location[1] + anchorView.height + 8
        
        popupWindow.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, x, y)
        
        // 执行入场动画
        animateEntry()
    }
    
    private fun setupPopupWindow() {
        binding = PopupMenuModernBinding.inflate(LayoutInflater.from(context))
        
        // 更新编辑模式文字
        binding.editModeText.text = if (isEditMode) "完成编辑" else "编辑列表"
        
        popupWindow = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            // 设置透明背景和外部点击消失
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            
            // 设置动画样式
            animationStyle = 0  // 禁用默认动画，使用自定义动画
        }
    }
    
    private fun setupClickListeners() {
        binding.editModeOption.setOnClickListener {
            menuItemClickListener?.onEditModeClick()
            dismiss()
        }
        
        binding.settingsOption.setOnClickListener {
            menuItemClickListener?.onSettingsClick()
            dismiss()
        }
        
        binding.aboutOption.setOnClickListener {
            menuItemClickListener?.onAboutClick()
            dismiss()
        }
    }
    
    private fun animateEntry() {
        // 初始状态：缩小、透明、稍微向上偏移
        binding.root.apply {
            scaleX = 0.3f
            scaleY = 0.3f
            alpha = 0f
            translationY = -20f
        }
        
        // 创建动画组合
        val animatorSet = AnimatorSet()
        
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 0.3f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.root, "alpha", 0f, 1f)
        val translationY = ObjectAnimator.ofFloat(binding.root, "translationY", -20f, 0f)
        
        animatorSet.apply {
            playTogether(scaleX, scaleY, alpha, translationY)
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
        }
        
        animatorSet.start()
    }
    
    private fun animateExit(onComplete: () -> Unit) {
        // 退出动画：缩小、透明化
        val animatorSet = AnimatorSet()
        
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 1f, 0.8f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 1f, 0.8f)
        val alpha = ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0f)
        
        animatorSet.apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 150
            interpolator = AccelerateInterpolator()
        }
        
        animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })
        
        animatorSet.start()
    }
    
    fun dismiss() {
        if (::popupWindow.isInitialized && popupWindow.isShowing) {
            animateExit {
                popupWindow.dismiss()
            }
        }
    }
    
    fun isShowing(): Boolean {
        return ::popupWindow.isInitialized && popupWindow.isShowing
    }
    
    private fun updateMenuVisibility() {
        if (isEditMode) {
            // 编辑模式：只显示完成编辑选项
            binding.editModeOption.visibility = android.view.View.VISIBLE
            binding.settingsOption.visibility = android.view.View.GONE
            binding.aboutOption.visibility = android.view.View.GONE
            // 隐藏分割线
            binding.root.findViewById<android.view.View>(com.example.weatherdemo.R.id.menuDivider1)?.visibility = android.view.View.GONE
            binding.root.findViewById<android.view.View>(com.example.weatherdemo.R.id.menuDivider2)?.visibility = android.view.View.GONE
        } else {
            // 普通模式：显示所有选项
            binding.editModeOption.visibility = android.view.View.VISIBLE
            binding.settingsOption.visibility = android.view.View.VISIBLE
            binding.aboutOption.visibility = android.view.View.VISIBLE
            // 显示分割线
            binding.root.findViewById<android.view.View>(com.example.weatherdemo.R.id.menuDivider1)?.visibility = android.view.View.VISIBLE
            binding.root.findViewById<android.view.View>(com.example.weatherdemo.R.id.menuDivider2)?.visibility = android.view.View.VISIBLE
        }
    }
} 