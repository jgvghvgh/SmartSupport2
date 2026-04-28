package com.heima.smartai.rag;

import com.heima.smartai.mapper.KnowledgeMapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25关键词检索服务（真正实现）
 *
 * BM25公式：
 * Score(D, Q) = Σ IDF(qi) * (tf(qi,D) * (k1+1)) / (tf(qi,D) + k1 * (1 - b + b * |D|/avgdl))
 *
 * 其中：
 * - tf(qi,D): 词qi在文档D中的词频
 * - |D|: 文档D的长度
 * - avgdl: 平均文档长度
 * - k1: 词频饱和参数（通常1.2-2.0）
 * - b: 文档长度归一化参数（通常0.75）
 * - IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
 */
@Slf4j
@Service
public class Bm25Service {

    // BM25标准参数
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final int TOP_K = 20;

    // 文档集合
    private final List<Document> documents = new ArrayList<>();

    // 倒排索引：词 -> [docId, tf]
    private final Map<String, List<InvertedIndexEntry>> invertedIndex = new HashMap<>();

    // 文档长度统计
    private int totalDocLength = 0;
    private double avgDocLength = 0;

    // 分词器
    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @PostConstruct
    public void init() {
        loadDocuments();
    }

    /**
     * 从数据库加载文档，构建倒排索引
     */
    public void loadDocuments() {
        try {
            List<KnowledgeMapper.Bm25Document> docs = knowledgeMapper.getAllDocuments();
            if (docs == null || docs.isEmpty()) {
                log.warn("BM25文档库为空");
                return;
            }

            documents.clear();
            invertedIndex.clear();
            totalDocLength = 0;

            for (KnowledgeMapper.Bm25Document doc : docs) {
                addDocument(new Document(doc.getContent()));
            }

            avgDocLength = documents.isEmpty() ? 0 : (double) totalDocLength / documents.size();
            log.info("BM25索引构建完成，文档数={}, 平均长度={}, 词条数={}",
                    documents.size(), avgDocLength, invertedIndex.size());
        } catch (Exception e) {
            log.error("BM25索引构建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 添加文档并更新倒排索引
     */
    private void addDocument(Document doc) {
        int docId = documents.size();
        doc.setId(docId);
        documents.add(doc);
        totalDocLength += doc.getLength();

        // 分词
        List<String> terms = tokenize(doc.getText());

        // 词频统计
        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        // 构建倒排索引
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            invertedIndex.computeIfAbsent(term, k -> new ArrayList<>())
                    .add(new InvertedIndexEntry(docId, tf));
        }
    }

    /**
     * 中文分词（使用Jieba）
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<SegToken> tokens = segmenter.process(text, JiebaSegmenter.SegMode.INDEX);
        return tokens.stream()
                .map(token -> token.word)
                .filter(word -> word.length() > 1)  // 过滤单字
                .collect(Collectors.toList());
    }

    /**
     * BM25检索
     *
     * @param query 用户查询
     * @return 按相关性排序的文档列表
     */
    public List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 查询词分词
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个文档的BM25分数
        Map<Integer, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            // 获取包含该词的文档列表
            List<InvertedIndexEntry> entries = invertedIndex.get(term);
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            // 计算IDF
            double idf = calculateIDF(entries.size());

            // 遍历每个文档，计算该词的BM25贡献
            for (InvertedIndexEntry entry : entries) {
                int docId = entry.getDocId();
                int tf = entry.getTf();

                // BM25公式计算
                double termScore = idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * documents.get(docId).getLength() / avgDocLength));

                scores.merge(docId, termScore, Double::sum);
            }
        }

        // 按分数降序排序，取topK
        return scores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(TOP_K)
                .map(e -> documents.get(e.getKey()).getText())
                .collect(Collectors.toList());
    }

    /**
     * 计算IDF（逆文档频率）
     *
     * IDF = log((N - n(q) + 0.5) / (n(q) + 0.5) + 1)
     *
     * 其中N为文档总数，n(q)为包含词q的文档数
     */
    private double calculateIDF(int docFreq) {
        int N = documents.size();
        if (N == 0) return 0;
        return Math.log((N - docFreq + 0.5) / (docFreq + 0.5) + 1);
    }

    /**
     * 文档对象
     */
    public static class Document {
        private int id;
        private String text;
        private int length;

        public Document(String text) {
            this.text = text;
            this.length = text.length();
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getText() { return text; }
        public int getLength() { return length; }
    }

    /**
     * 倒排索引条目
     */
    private static class InvertedIndexEntry {
        private final int docId;
        private final int tf;

        public InvertedIndexEntry(int docId, int tf) {
            this.docId = docId;
            this.tf = tf;
        }

        public int getDocId() { return docId; }
        public int getTf() { return tf; }
    }
}