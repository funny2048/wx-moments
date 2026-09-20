package com.funny.moments.service.social.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.model.out.ImageUploadOut;
import com.funny.moments.service.social.ILocalImageService;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地图片存储实现（无 DB，纯文件系统）：上传校验 + UUID 落盘 + 占位图池幂等生成。
 *
 * <p>文件名一律服务端生成（UUID/固定占位名），天然满足 PatternConsts.IMAGE_FILE_NAME
 * 白名单（/images/** 静态解析防穿越，R6）；不依赖既有 infra FileServiceImpl（公司 FileClient 对象存储）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class LocalImageServiceImpl implements ILocalImageService {

    /**
     * 图片根目录（建议9：绝对路径默认值，免工作目录漂移）。
     * R2-建议3：@Value 单一承载（嵌套默认值可解析），默认值字符串须与 T100 WebConfig 的注入逐字一致。
     */
    @Value("${moments.image.root-path:${user.home}/moments-data/images}")
    private String rootPath;

    /** 上传大小上限 5MB（C17） */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 扩展名白名单（C17，按原始文件名判断） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 占位图池规格（A7）：头像 20 张 200×200 + 内容图 20 张 600×600，循环复用 */
    private static final int AVATAR_COUNT = 20;
    private static final int AVATAR_SIZE = 200;
    private static final int CONTENT_COUNT = 20;
    private static final int CONTENT_SIZE = 600;

    @Override
    public ImageUploadOut upload(MultipartFile file) {
        // 1 缺失/空文件统一 1020（api.md 接口17：文件缺失也走格式错误码）
        if (file == null || file.isEmpty()) {
            throw bizException(SocialErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        // 2 扩展名白名单：无扩展名=空串不在白名单内，同样 1020
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw bizException(SocialErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        // 3 大小上限 5MB（1021）
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw bizException(SocialErrorCode.IMAGE_SIZE_EXCEEDED);
        }
        // 4 服务端生成文件名（32 位 UUID 防覆盖防穿越），UUID+扩展名天然满足 IMAGE_FILE_NAME 白名单
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path dir = Paths.get(rootPath);
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), file.getBytes());
        } catch (IOException e) {
            // 图片目录不可写等系统异常：error 日志含路径（design §9），上抛系统异常由全局兜底转 code=100
            log.error("本地图片落盘失败 rootPath={}, fileName={}, originalName={}", rootPath, fileName,
                    file.getOriginalFilename(), e);
            throw new RuntimeException("图片保存失败", e);
        }
        log.info("本地图片上传成功 fileName={}, size={}, originalName={}", fileName, file.getSize(),
                file.getOriginalFilename());
        ImageUploadOut out = new ImageUploadOut();
        out.setImageUrl("/images/" + fileName);
        return out;
    }

    @Override
    public List<String> ensurePlaceholderPool() {
        List<String> imageUrls = new ArrayList<>();
        try {
            Path dir = Paths.get(rootPath);
            Files.createDirectories(dir);
            for (int i = 1; i <= AVATAR_COUNT; i++) {
                imageUrls.add(ensureImage(dir, String.format("ph_avatar_%02d.png", i), AVATAR_SIZE));
            }
            for (int i = 1; i <= CONTENT_COUNT; i++) {
                imageUrls.add(ensureImage(dir, String.format("ph_content_%02d.png", i), CONTENT_SIZE));
            }
        } catch (IOException e) {
            log.error("占位图池生成失败 rootPath={}", rootPath, e);
            throw new RuntimeException("占位图生成失败", e);
        }
        return imageUrls;
    }

    /**
     * 幂等生成单张占位图：已存在直接跳过（重复调用不重绘不改色），缺失则自绘纯色 PNG 落盘。
     */
    private String ensureImage(Path dir, String fileName, int size) throws IOException {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target)) {
            writeSolidColorPng(target, size);
            log.info("占位图生成 fileName={}, size={}x{}", fileName, size, size);
        }
        return "/images/" + fileName;
    }

    /**
     * 绘制纯色 PNG（A7：无文字无字体依赖，JDK ImageIO 自绘）：
     * 随机柔和色调（pastel：随机色相 + 低饱和 0.25-0.45 + 高亮度 0.85-1.0）。
     */
    private void writeSolidColorPng(Path target, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Color pastel = new Color(Color.HSBtoRGB(random.nextFloat(),
                    0.25f + random.nextFloat() * 0.20f, 0.85f + random.nextFloat() * 0.15f));
            graphics.setColor(pastel);
            graphics.fillRect(0, 0, size, size);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IllegalStateException("PNG 编码器缺失，占位图生成失败 target=" + target);
        }
    }

    /**
     * 提取小写扩展名：null/无点/以点结尾均返回空串（口径同项目内 FileServiceImpl#getExtension）。
     */
    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
