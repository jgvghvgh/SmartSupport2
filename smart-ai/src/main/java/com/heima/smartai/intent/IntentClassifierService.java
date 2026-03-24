package com.heima.smartai.intent;

import com.heima.smartai.Config.AiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntentClassifierService {

    private static final String CACHE_PREFIX = "ai:intent:cache:v1:";
    private static final String RATE_PREFIX = "ai:intent:rate:";

    // 分类调用限流：每分钟最多次数
    private static final long MAX_PER_MINUTE = 5;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AiClient aiClient;

    public IntentResult classify(String question, String userId) {
        if (question == null || question.isBlank()) {
            return IntentResult.fail("question is blank");
        }

        String q = question.trim();
        String hash = DigestUtils.md5DigestAsHex(q.getBytes(StandardCharsets.UTF_8));
        String cacheKey = CACHE_PREFIX + hash;

        String cachedIntent = redisTemplate.opsForValue().get(cacheKey);
        if (cachedIntent != null && !cachedIntent.isBlank()) {
            return IntentResult.fromCached(cachedIntent);
        }

        // 限流（按用户维度）
        if (!tryAcquire(userId)) {
            return IntentResult.fail("rate limited");
        }

        String prompt = """
                你是智能客服系统的意图分类器，请将用户问题归类到以下之一：
                1) FAQ：属于知识库可回答的常见问题
                2) NEED_MORE_INFO：信息不足/表述不清，需要客服补充或用户重新描述
                3) OTHER：不确定/其他类型

                请你只输出一行，格式严格为：
                INTENT=<FAQ|NEED_MORE_INFO|OTHER>;CONFIDENCE=<0-1的小数>;REASON=<简短原因>

                用户问题：
                %s
                """.formatted(q);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);

        String raw = aiClient.chatRaw(messages);
        if (raw == null || raw.isBlank()) {
            return IntentResult.fail("ai classification empty");
        }

        IntentResult result = parse(raw);
        if (!result.ok) {
            return result;
        }

        // 写入意图缓存（只缓存 intent，减少解析开销）
        redisTemplate.opsForValue().set(cacheKey, result.intent, 7, TimeUnit.DAYS);
        return result;
    }

    private boolean tryAcquire(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        String key = RATE_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        return count != null && count <= MAX_PER_MINUTE;
    }

    private IntentResult parse(String raw) {
        Pattern intentPattern = Pattern.compile("INTENT\\s*=\\s*(FAQ|NEED_MORE_INFO|OTHER)", Pattern.CASE_INSENSITIVE);
        Matcher intentMatcher = intentPattern.matcher(raw);
        if (!intentMatcher.find()) {
            return IntentResult.fail("cannot parse intent: " + raw);
        }
        String intent = intentMatcher.group(1).toUpperCase();

        Pattern confPattern = Pattern.compile("CONFIDENCE\\s*=\\s*([0-9]+(\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher confMatcher = confPattern.matcher(raw);
        double conf = 0.0;
        if (confMatcher.find()) {
            try {
                conf = Double.parseDouble(confMatcher.group(1));
            } catch (Exception ignored) {
            }
        }

        Pattern reasonPattern = Pattern.compile("REASON\\s*=\\s*(.*)", Pattern.CASE_INSENSITIVE);
        Matcher reasonMatcher = reasonPattern.matcher(raw);
        String reason = null;
        if (reasonMatcher.find()) {
            reason = reasonMatcher.group(1).trim();
        }

        return IntentResult.ok(intent, conf, reason);
    }

    public static class IntentResult {
        public final boolean ok;
        public final String intent;
        public final double confidence;
        public final String reason;

        private IntentResult(boolean ok, String intent, double confidence, String reason) {
            this.ok = ok;
            this.intent = intent;
            this.confidence = confidence;
            this.reason = reason;
        }

        public static IntentResult ok(String intent, double confidence, String reason) {
            return new IntentResult(true, intent, confidence, reason);
        }

        public static IntentResult fail(String reason) {
            return new IntentResult(false, null, 0.0, reason);
        }

        public static IntentResult fromCached(String intent) {
            if (intent == null) {
                return fail("cached intent null");
            }
            return new IntentResult(true, intent.toUpperCase(), 0.0, "from cache");
        }
    }
}

