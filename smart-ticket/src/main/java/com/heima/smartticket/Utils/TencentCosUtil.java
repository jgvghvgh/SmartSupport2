package com.heima.smartticket.Utils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
public class TencentCosUtil {

    @Value("${tencent.cos.secret-id}")
    private String secretId;

    @Value("${tencent.cos.secret-key}")
    private String secretKey;

    @Value("${tencent.cos.region}")
    private String region;

    @Value("${tencent.cos.bucket}")
    private String bucketName;

    @Value("${tencent.cos.base-url}")
    private String baseUrl;

    private COSClient cosClient;

    /**
     * 初始化 COSClient
     */
    @PostConstruct
    public void init() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        this.cosClient = new COSClient(cred, clientConfig);
        log.info(" Tencent COS 客户端初始化成功");
    }

    /**
     * 上传文件（MultipartFile）
     *
     * @param file     Spring 的上传文件对象
     * @param dirName  上传目录（例如 avatar、post）
     * @return 图片的公网访问 URL
     */
    public String uploadFile(MultipartFile file, String dirName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成唯一文件名
        String key = dirName + "/" + UUID.randomUUID() + suffix;

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest request = new PutObjectRequest(bucketName, key, inputStream, metadata);
            cosClient.putObject(request);

            String fileUrl = baseUrl + "/" + key;
            log.info(" 文件上传成功: {}", fileUrl);
            return fileUrl;
        } catch (IOException | CosClientException e) {
            log.error(" 上传文件到 COS 失败: {}", e.getMessage(), e);
            throw new RuntimeException("上传文件失败");
        }
    }

    /**
     * 删除 COS 上的文件
     * @param fileUrl 完整访问 URL
     */
    public void deleteFile(String fileUrl) {
        if (!fileUrl.startsWith(baseUrl)) {
            log.warn(" 非当前存储桶文件，跳过删除：{}", fileUrl);
            return;
        }
        String key = fileUrl.substring(baseUrl.length() + 1);
        try {
            cosClient.deleteObject(bucketName, key);
            log.info(" 已删除文件: {}", key);
        } catch (Exception e) {
            log.error(" 删除文件失败: {}", e.getMessage());
        }
    }

    /**
     * 关闭客户端
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info(" Tencent COS 客户端已关闭");
        }
    }
}

