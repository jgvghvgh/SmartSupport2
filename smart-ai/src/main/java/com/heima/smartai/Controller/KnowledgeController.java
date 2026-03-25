package com.heima.smartai.Controller;

import com.heima.smartai.AiService.KnowledgeImportService;
import com.heima.smartcommon.Result.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeImportService knowledgeImportService;

    /**
     * 导入知识文本到向量库
     */
    @PostMapping("/import")
    public CommonResult<List<String>> importKnowledge(@RequestParam("text") String text) {
        List<String> ids = knowledgeImportService.importDoc(text);
        return CommonResult.success(ids);
    }

    /**
     * 根据 ID 删除知识
     */
    @DeleteMapping("/{id}")
    public CommonResult<String> deleteKnowledge(@PathVariable("id") String id) {
        knowledgeImportService.deleteById(id);
        return CommonResult.success("删除成功");
    }

    /**
     * 搜索知识库（调试用）
     */
    @GetMapping("/search")
    public CommonResult<List<String>> searchKnowledge(@RequestParam("query") String query,
                                                      @RequestParam(defaultValue = "5") int topK) {
        List<String> results = knowledgeImportService.search(query, topK);
        return CommonResult.success(results);
    }
}
