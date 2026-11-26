package com.github.kr328.clash.design.model


/**
 * The streaming-service probe list — the one source of truth for the
 * latency screen and the dashboard services card.
 *
 * The probe itself is the core's [com.github.kr328.clash.core.Clash.probeUrl];
 * this catalog only decides which URLs are probed and how a response code is
 * classified.
 */
object ProbeServices {
    data class Service(
        val name: String,
        val url: String,
        val okCodes: Set<Int>,
        val restrictedCodes: Set<Int> = emptySet(),
        val category: String = "General",
    )

    val ALL: List<Service> = listOf(
        Service(
            "Google",
            "https://www.google.com/generate_204",
            okCodes = setOf(204),
        ),
        Service(
            "Cloudflare",
            "https://www.cloudflare.com/cdn-cgi/trace",
            okCodes = setOf(200),
        ),
        Service(
            "GitHub",
            "https://github.com/generate_204",
            okCodes = setOf(204),
            category = "Developer",
        ),
        Service(
            "GitLab",
            "https://gitlab.com/users/sign_in",
            okCodes = setOf(200, 302),
            category = "Developer",
        ),
        Service(
            "YouTube",
            "https://www.youtube.com/generate_204",
            okCodes = setOf(204),
            category = "Streaming",
        ),
        Service(
            "Netflix",
            "https://www.netflix.com/title/81280792",
            okCodes = setOf(200),
            restrictedCodes = setOf(404),
            category = "Streaming",
        ),
        Service(
            "Disney+",
            "https://www.disneyplus.com/",
            okCodes = setOf(200),
            category = "Streaming",
        ),
        Service(
            "ChatGPT",
            "https://ios.chat.openai.com/public-api/mobile/server_status/v1",
            okCodes = setOf(200),
            category = "AI",
        ),
        Service(
            "Claude",
            "https://claude.ai/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "AI",
        ),
        Service(
            "Gemini",
            "https://gemini.google.com/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "AI",
        ),
        Service(
            "Perplexity",
            "https://www.perplexity.ai/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "AI",
        ),
        Service(
            "Sora",
            "https://sora.com/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "AI",
        ),
        Service(
            "Baidu",
            "https://www.baidu.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "Bilibili",
            "https://www.bilibili.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "Tencent",
            "https://www.qq.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "Alibaba Cloud",
            "https://www.aliyun.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "WeChat",
            "https://weixin.qq.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "Douyin",
            "https://www.douyin.com/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "Domestic",
        ),
        Service(
            "JD",
            "https://www.jd.com/",
            okCodes = setOf(200),
            category = "Domestic",
        ),
        Service(
            "Weibo",
            "https://weibo.com/",
            okCodes = setOf(200, 302),
            restrictedCodes = setOf(403, 429),
            category = "Domestic",
        ),
    )

    val names: List<String> = ALL.map { it.name }

}
