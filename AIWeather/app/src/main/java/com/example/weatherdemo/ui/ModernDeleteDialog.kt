package com.example.weatherdemo.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
import com.example.weatherdemo.databinding.DialogDeleteCityBinding

class ModernDeleteDialog(
    private val context: Context,
    private val cityName: String
) {
    
    private lateinit var binding: DialogDeleteCityBinding
    private lateinit var dialog: Dialog
    
    interface OnDeleteConfirmListener {
        fun onConfirmDelete()
        fun onCancel()
    }
    
    private var deleteConfirmListener: OnDeleteConfirmListener? = null
    
    fun setOnDeleteConfirmListener(listener: OnDeleteConfirmListener) {
        deleteConfirmListener = listener
    }
    
    fun show() {
        setupDialog()
        setupContent()
        setupClickListeners()
        
        dialog.show()
        animateEntry()
    }
    
    private fun setupDialog() {
        binding = DialogDeleteCityBinding.inflate(LayoutInflater.from(context))
        
        dialog = Dialog(context).apply {
            setContentView(binding.root)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                attributes?.windowAnimations = 0  // 禁用默认动画
            }
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
    }
    
    private fun setupContent() {
        binding.messageText.text = "确定要删除「${cityName}」的天气信息吗？"
    }
    
    private fun setupClickListeners() {
        binding.cancelButton.setOnClickListener {
            animateButtonPress(it) {
                deleteConfirmListener?.onCancel()
                dismiss()
            }
        }
        
        binding.deleteButton.setOnClickListener {
            animateButtonPress(it) {
                deleteConfirmListener?.onConfirmDelete()
                dismiss()
            }
        }
        
        // 点击外部关闭
        dialog.setOnCancelListener {
            deleteConfirmListener?.onCancel()
        }
    }
    
    private fun animateEntry() {
        // 初始状态：缩小、透明、稍微向下偏移
        binding.root.apply {
            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0f
            translationY = 50f
        }
        
        // 创建入场动画
        val animatorSet = AnimatorSet()
        
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 0.7f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 0.7f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.root, "alpha", 0f, 1f)
        val translationY = ObjectAnimator.ofFloat(binding.root, "translationY", 50f, 0f)
        
        animatorSet.apply {
            playTogether(scaleX, scaleY, alpha, translationY)
            duration = 400
            interpolator = OvershootInterpolator(1.1f)
        }
        
        animatorSet.start()
    }
    
    private fun animateExit(onComplete: () -> Unit) {
        // 退出动画：缩小、透明化、向下偏移
        val animatorSet = AnimatorSet()
        
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 1f, 0.8f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 1f, 0.8f)
        val alpha = ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0f)
        val translationY = ObjectAnimator.ofFloat(binding.root, "translationY", 0f, 30f)
        
        animatorSet.apply {
            playTogether(scaleX, scaleY, alpha, translationY)
            duration = 200
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
    
    private fun animateButtonPress(view: View, onComplete: () -> Unit) {
        // 按钮按下动画
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction {
                        onComplete()
                    }
                    .start()
            }
            .start()
    }
    
    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) {
            animateExit {
                dialog.dismiss()
            }
        }
    }
    
    fun isShowing(): Boolean {
        return ::dialog.isInitialized && dialog.isShowing
    }
} 