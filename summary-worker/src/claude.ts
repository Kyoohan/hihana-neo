/**
 * Claude API 호출 — 게시글·클래스룸 공지 요약(이미지 글자 읽기 포함)은 Opus, 아침 브리핑은 Haiku.
 *
 * 모델 고른 근거(2026-11-26, 실제 게시글 16건·브리핑 4일치를 지금 프롬프트 그대로 비교):
 *  - 요약: Opus 가 한 줄 30자 규칙을 한 번도 어기지 않았고, 가정통신문 이미지 속 출금일을 '마감'으로·중복 없이 뽑음
 *    (Gemma 는 '행사'로 잘못 나누거나 학년마다 같은 일정을 되풀이). 요약은 글마다 한 번만 만들어 모두가 나눠 쓰므로 사람 수와 무관.
 *  - 브리핑: 사람마다 따로 만들어 사람 수만큼 부르므로 Haiku. 규칙(두세 문장·수업 나열 안 함)을 가장 잘 지켰고, 1회 약 $0.0009.
 *
 * 키(ANTHROPIC_API_KEY, `wrangler secret put`)가 없거나 실패·거절이면 null — 부르는 쪽이 예전처럼 Workers AI(Gemma)로 대신합니다.
 * 비용 상한은 index.ts 의 하루 횟수 제한(요약 500·브리핑 400)이 그대로 맡습니다.
 */
import Anthropic from "@anthropic-ai/sdk";

export const SUMMARY_MODEL = "claude-opus-5-5";
export const BRIEF_MODEL = "claude-haiku-5-5";

/** Claude 가 받는 이미지 형식 — bmp 는 받지 않아 Gemma 로 읽습니다. */
export const CLAUDE_IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"] as const;
export type ClaudeImageType = (typeof CLAUDE_IMAGE_TYPES)[number];

type Content = string | Anthropic.Beta.Messages.BetaContentBlockParam[];

/** 답 글자만 — 키가 없거나 실패·거절·빈 답이면 null. */
export async function askClaude(
  env: { ANTHROPIC_API_KEY?: string },
  model: string,
  system: string | undefined,
  content: Content,
  maxTokens: number,
): Promise<string | null> {
  if (!env.ANTHROPIC_API_KEY) return null;
  // 재시도는 한 번만, 60초 안에 — 앱은 90초 기다리고, 실패하면 Gemma 가 이어서 답해야 하므로.
  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 1, timeout: 60_000 });
  try {
    const r = await client.beta.messages.create({
      model,
      max_tokens: maxTokens,
      // 요약·브리핑은 깊은 추론보다 빠른 답이 중요 — 비교 시험도 low 로 함. Opus 5.5 는 생각을 끌 수 없어 effort 로만 줄입니다.
      output_config: { effort: "low" },
      ...(system ? { system } : {}),
      messages: [{ role: "user", content }],
      // Opus 는 안전 분류기가 거절하면 서버가 같은 요청을 다른 모델로 이어서 답하게(거절 종류에 맞춰 서버가 고름).
      // Haiku 는 서버 쪽 대체가 없어, 거절이면 아래에서 null → Gemma.
      ...(model === SUMMARY_MODEL ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" as const } : {}),
    });
    if (r.stop_reason === "refusal") {
      console.error("claude refusal", model, r.stop_details?.category ?? null);
      return null;
    }
    if (r.stop_reason === "max_tokens") console.error("claude max_tokens", model);
    const text = r.content.map((b) => (b.type === "text" ? b.text : "")).join("").trim();
    console.log("claude", r.model, r.usage.input_tokens, r.usage.output_tokens);
    return text || null;
  } catch (e) {
    if (e instanceof Anthropic.RateLimitError) console.error("claude rate limited", model);
    else if (e instanceof Anthropic.APIError) console.error("claude api error", model, e.status, e.message);
    else console.error("claude failed", model, String(e));
    return null;
  }
}
