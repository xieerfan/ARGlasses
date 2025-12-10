package com.example.myapplication

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ✅ 提示词管理器
 * 
 * 功能：加载JSON格式的提示词文件，为不同科目提供对应的提示词
 * JSON文件路径：/server/ConfigurationFile/prmot.json
 * 
 * JSON格式：
 * {
 *   "subjects": {
 *     "physics": "物理相关提示...",
 *     "math": "数学相关提示...",
 *     ...
 *   }
 * }
 */
class PromptsManager(private val context: Context) {

    companion object {
        private const val TAG = "PromptsManager"
        private const val PROMPTS_FILE = "prmot.json"  // Assets目录下的提示词文件
    }

    private var subjectPrompts: Map<String, String> = emptyMap()
    private var isLoaded = false

    /**
     * 初始化：加载提示词文件
     */
    fun initialize(): Boolean {
        return try {
            loadPromptsFromAssets()
            isLoaded = true
            Log.d(TAG, "✅ 提示词文件加载成功，共 ${subjectPrompts.size} 个科目")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载提示词文件失败: ${e.message}", e)
            false
        }
    }

    /**
     * 从Assets目录加载JSON文件
     */
    private fun loadPromptsFromAssets() {
        try {
            // 从assets目录读取prmot.json
            val jsonContent = context.assets.open(PROMPTS_FILE).bufferedReader().use { reader ->
                reader.readText()
            }

            val jsonObject = JSONObject(jsonContent)
            val subjectsObj = jsonObject.getJSONObject("subjects")

            // 解析所有科目和提示词
            subjectPrompts = subjectsObj.keys().asSequence().associateWith { key ->
                subjectsObj.getString(key)
            }

            Log.d(TAG, "✅ 加载的科目: ${subjectPrompts.keys.joinToString(", ")}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析JSON失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 获取指定科目的提示词
     */
    fun getPromptForSubject(subject: String): String {
        if (!isLoaded) {
            Log.w(TAG, "⚠️  提示词未加载，请先调用initialize()")
            return getDefaultPrompt(subject)
        }

        return subjectPrompts[subject.lowercase()] ?: getDefaultPrompt(subject)
    }

    /**
     * 获取默认提示词（当JSON中不存在该科目时）
     */
    private fun getDefaultPrompt(subject: String): String {
        return when (subject.lowercase()) {
            "physics" -> "请分析这张物理题目图片的内容，包括题目、解题过程和答案。"
            "math" -> "请分析这张数学题目图片，包括题目、解题步骤和最终答案。"
            "chemistry" -> "请分析这张化学题目图片，包括题目、化学反应过程和答案。"
            "politics" -> "请分析这张政治题目图片，包括题目、分析过程和答案。"
            "english" -> "请分析这张英语题目或文章图片，提供英文内容和中文翻译。"
            "chinese" -> "请分析这张语文题目或文章图片，包括题目、答案和解析。"
            "history" -> "请分析这张历史题目图片，包括题目、历史背景和答案。"
            else -> "请分析这张图片的内容。"
        }
    }

    /**
     * 获取所有支持的科目列表
     */
    fun getSupportedSubjects(): List<String> {
        return if (isLoaded) {
            subjectPrompts.keys.toList().sorted()
        } else {
            listOf("physics", "math", "chemistry", "politics", "english", "chinese", "history", "order")
        }
    }

    /**
     * 获取科目的中文名称
     */
    fun getSubjectChinese(subject: String): String {
        return when (subject.lowercase()) {
            "physics" -> "物理"
            "math" -> "数学"
            "chemistry" -> "化学"
            "politics" -> "政治"
            "english" -> "英语"
            "chinese" -> "中文"
            "history" -> "历史"
            "order" -> "其他"
            else -> "未知"
        }
    }

    /**
     * 检查提示词是否已加载
     */
    fun isInitialized(): Boolean = isLoaded
}
