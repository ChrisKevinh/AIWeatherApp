package com.example.weatherdemo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherdemo.R
import com.example.weatherdemo.network.SearchResult

class SearchResultAdapter(
    private val onItemClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {
    
    private var searchResults = listOf<SearchResult>()
    
    fun updateResults(newResults: List<SearchResult>) {
        searchResults = newResults
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val result = searchResults[position]
        holder.bind(result)
    }
    
    override fun getItemCount(): Int = searchResults.size
    
    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cityNameText: TextView = itemView.findViewById(R.id.cityNameText)
        private val regionText: TextView = itemView.findViewById(R.id.regionText)
        
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(searchResults[position])
                }
            }
        }
        
        fun bind(result: SearchResult) {
            // 主标题：城市名称（必须显示），确保字符正确显示
            cityNameText.text = cleanAndValidateText(result.name, "未知城市")
            
            // 副标题：区域和国家信息
            val locationDetails = mutableListOf<String>()
            
            if (result.region.isNotEmpty() && result.region.trim().isNotEmpty()) {
                locationDetails.add(cleanAndValidateText(result.region, ""))
            }
            
            if (result.country.isNotEmpty() && result.country.trim().isNotEmpty()) {
                locationDetails.add(cleanAndValidateText(result.country, ""))
            }
            
            regionText.text = if (locationDetails.isNotEmpty()) {
                "📍 " + locationDetails.filter { it.isNotEmpty() }.joinToString(", ")
            } else {
                "📍 位置信息未知"
            }
        }
        
        /**
         * 清理和验证文本，确保特殊字符正确显示
         */
        private fun cleanAndValidateText(input: String, fallback: String): String {
            return try {
                // 移除可能导致显示问题的控制字符
                val cleaned = input.trim()
                    .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "") // 移除控制字符，保留换行和制表符
                    .replace(Regex("\\s+"), " ") // 规范化空白字符
                
                // 检查是否包含有效字符
                if (cleaned.isNotEmpty() && cleaned.any { it.isLetterOrDigit() || it.isWhitespace() || "()[]{},.'-".contains(it) }) {
                    cleaned
                } else {
                    fallback
                }
            } catch (e: Exception) {
                // 如果处理过程中出现异常，返回后备文本
                fallback
            }
        }
    }
}
