# 思考强度适配

核对日期：2026-11-08。实现位于 `kirari-llm-core/ReasoningEffort.kt`。

“自动”保留服务自身默认行为，不等于关闭思考。每次请求根据当前模型和接口解析设置；未知型号或未经验证的协议组合仅提供自动，避免直接发送不支持的字段。模型名称识别不代表模型具备图片输入能力，仍需选择支持视觉的模型用于多模态解题。

## 档位与协议

| 模型范围 | 调节方式 |
| --- | --- |
| GPT-5、5.1、5.2、5.4、5.5、GPT-6 Astra/Sol/Luna、6.1 Sol/Terra；o1/o3/o4-mini 的已知系列 | Chat 的 `reasoning_effort` 或 Responses 的 `reasoning.effort`。按型号显示支持的档位；例如 GPT-6 Astra 不提供关闭，GPT-5.4 不提供 max。特殊 Chat/Codex/Pro 型号未统一猜测。 |
| Claude 4.6、4.7、4.8 及已核对的 5/5.1/5.5 系列 | 原生 Anthropic 接口，adaptive thinking + `output_config.effort`；4.6 不展示 xhigh。 |
| Claude 4.5 及已核对的早期 thinking 型号 | `thinking.budget_tokens`；Opus 4.5 同时设置 effort。预算低/中/高为 1,024/8,192/24,576，输出上限留出答案空间。 |
| Gemini 2.5 Pro/Flash/Flash-Lite | 原生 Google 接口，`thinkingBudget`；低/中为 1,024/8,192，高为 Pro 32,768、Flash 24,576。Pro 不提供关闭。 |
| 已核对的 Gemini 3/3.1/3.5/3.6/3.7/3.8 与 4 Argon | `thinkingLevel`；逐型号区分 minimal/low/medium/high，不将预算字段发给这些型号。 |
| DeepSeek V4/V4.1、deepseek-flash | 原生 Chat 的 thinking 开关 + effort，Responses 使用嵌套 effort。medium 映射 high，xhigh 映射 high；界面展示实际 low/high/max 档位。 |
| Qwen3.8 已核对的 Max/Flash/Omni/开源型号 | effort 为 low/medium/xhigh；high、max 映射 xhigh。不同时发送预算与 effort。 |
| Qwen3/3.5/3.6/3.7 支持混合思考的系列 | Chat 的 `enable_thinking` 和 `thinking_budget`；预算预设 1,024/8,192/24,576。thinking-only 型号不提供关闭，coder/instruct/omni 等未经统一验证的变体不猜测。 |
| GLM-5.3 与 Kimi K3/K3.1 Pro | 原生 Chat 的 low/high/max；不提供关闭强制思考。 |
| GLM-5.2 | 按官方规则映射 high/max，保留关闭选项。 |
| GLM-4.6/4.7/5/5.1、Kimi K2.5/K2.6、DeepSeek V3.1/V3.2 | 原生 Chat 的思考开关，不虚构低中高档位。 |

预算数值是本应用预设，并非厂商把各档位统一定义成这些 token 数。不同模型的同名档位也不代表相同的成本或能力。

## 托管服务与上下文

识别百炼官方 `dashscope*.aliyuncs.com` 和业务空间 `*.maas.aliyuncs.com` 地址，根据百炼文档使用 `enable_thinking`、相应档位映射和模型路由差异。第三方中转站仍可能自行改写参数；无法确认的接口可使用自动。

一次解题中的搜索工具往返会保留 Chat 的 `reasoning_content`、Claude thinking 签名、Gemini thought signature 及 Responses 加密 reasoning item，不把这些中间数据显示为最终答案。每次右球截图仍是独立题目，追问沿用当前题目的已保存对话。不同服务对跨轮思考保留有额外要求，当前没有对所有服务完成实网验收。

## 官方资料

- [OpenAI reasoning](https://developers.openai.com/api/docs/guides/reasoning#reasoning-effort)、[GPT-5](https://developers.openai.com/api/docs/models/gpt-5)、[GPT-5.1](https://developers.openai.com/api/docs/models/gpt-5.1)、[GPT-5.2](https://developers.openai.com/api/docs/models/gpt-5.2)、[GPT-5.4](https://developers.openai.com/api/docs/models/gpt-5.4)、[GPT-5.5](https://developers.openai.com/api/docs/models/gpt-5.5)、[GPT-6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra)、[GPT-6 指南](https://developers.openai.com/api/docs/guides/latest-model)。
- [Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[旧版 thinking budget](https://platform.claude.com/docs/en/build-with-claude/extended-thinking)、[型号配置表](https://platform.claude.com/docs/en/build-with-claude/thinking-troubleshooting)。
- [Google thinking 档位与预算表](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/thinking)。
- [DeepSeek thinking](https://api-docs.deepseek.com/guides/thinking_mode/)。
- [百炼 Chat 参数与型号映射](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions)、[百炼 Responses](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-responses)。
- [Z.AI Chat 参数](https://docs.z.ai/api-reference/llm/chat-completion)、[GLM thinking](https://docs.z.ai/guides/capabilities/thinking-mode)。
- [Kimi thinking 与型号差异](https://platform.kimi.com/docs/guide/use-thinking-models)。

验证使用本地 MockWebServer 检查真实请求序列化、参数互斥、关闭/默认行为和工具往返上下文；不调用用户付费模型接口。模型行为与 UI 观感仍需实际使用确认。
