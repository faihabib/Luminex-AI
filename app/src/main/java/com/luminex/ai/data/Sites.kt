package com.luminex.ai.data

data class AiSite(
    val name: String,
    val url: String
)

val AI_SITES: List<AiSite> = listOf(
    AiSite("Arena AI", "https://arena.ai"),
    AiSite("AI Studio", "https://aistudio.google.com/u/0/prompts/new_chat"),
    AiSite("ChatGPT", "https://chatgpt.com"),
    AiSite("Gemini", "https://gemini.google.com/app"),
    AiSite("DeepSeek", "https://chat.deepseek.com"),
    AiSite("Claude", "https://claude.ai"),
    AiSite("Perplexity", "https://www.perplexity.ai"),
    AiSite("Grok", "https://grok.com"),
    AiSite("Mistral", "https://chat.mistral.ai"),
    AiSite("NotebookLM", "https://notebooklm.google.com"),
    AiSite("Ptero", "https://ptero.pro"),
    AiSite("Genspark", "https://www.genspark.ai"),
    AiSite("Duck.ai", "https://duck.ai")
)
