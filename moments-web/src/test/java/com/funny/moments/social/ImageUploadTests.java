package com.funny.moments.social;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口17 POST /api/images/upload（本地图片上传，multipart，T040）集成测试：TC137-TC143。
 * 做什么：MockMultipartFile 入口级上传，断言 UUID 文件名契约、白名单/大小边界、磁盘落盘核对与静态访问；
 * 失败用例断言"磁盘无新文件"（目录计数快照前后不变）。
 * 口径说明：文件 IO 不受事务回滚——本类不写 DB、不走 @Transactional，成功上传的临时文件统一登记、
 * @AfterEach 显式清理（共享本地目录自造自清）；内容字节任意（服务端仅校验扩展名与大小，不校验图片魔数）。
 */
@Slf4j
class ImageUploadTests extends TimelineBatch2TestBase {

    private static final long FIVE_MB = 5L * 1024 * 1024;

    /** 本类自造文件登记表（@AfterEach 自造自清） */
    private final List<Path> uploadedFiles = new ArrayList<>();

    @AfterEach
    void cleanUploadedFiles() {
        for (Path file : uploadedFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                log.warn("测试文件清理失败 file={}：{}", file, e.getMessage());
            }
        }
        uploadedFiles.clear();
    }

    /** 上传并断言 code=0，返回 imageUrl；成功落盘文件登记待清理。 */
    private String uploadOk(String originalFilename, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(
                        uploadBuilder().file(new MockMultipartFile("file", originalFilename, "application/octet-stream", content)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, codeOf(result), "上传 " + originalFilename + " 应成功");
        String imageUrl = strAt(result, "$.data.imageUrl");
        assertTrue(imageUrl.matches("^/images/[0-9a-f]{32}\\." + extensionOf(originalFilename) + "$"),
                "文件名应为 32 位 UUID.扩展：" + imageUrl);
        Path file = Paths.get(imageRootPath, imageUrl.substring("/images/".length()));
        assertTrue(Files.exists(file), "磁盘应存在该文件（落盘核对）：" + file);
        uploadedFiles.add(file);
        return imageUrl;
    }

    /** 上传并断言业务码（失败路径，无落盘登记）。 */
    private int uploadExpectCode(String originalFilename, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(
                        uploadBuilder().file(new MockMultipartFile("file", originalFilename, "application/octet-stream", content)))
                .andExpect(status().isOk()).andReturn();
        return codeOf(result);
    }

    /** 无 file 字段的 multipart 请求（缺失分支）。 */
    private int uploadWithoutFileExpectCode() throws Exception {
        MvcResult result = mockMvc.perform(uploadBuilder()).andExpect(status().isOk()).andReturn();
        return codeOf(result);
    }

    /** 图片根目录文件计数（目录快照，供"失败无落盘"断言）。 */
    private long dirFileCount() throws Exception {
        Path dir = Paths.get(imageRootPath);
        if (!Files.exists(dir)) {
            return 0;
        }
        try (var stream = Files.list(dir)) {
            return stream.count();
        }
    }

    private static String extensionOf(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 穿越请求安全实质断言（编排裁决9：真实容器实测取证——四变体均不返回任何文件字节）：
     * 满足以下任一形态即通过——①HTTP 404；②HTTP 400；③HTTP 200 且 contentType=application/json
     * 且 body 为 ApiResult 错误信封（code!=0）且不含 "spring:"/"datasource" 等文件内容特征。
     * 用例安全意图不变：穿越不得返回文件内容（R6）。
     */
    private void assertTraversalRejectedWithoutFileContent(
            org.springframework.mock.web.MockHttpServletResponse response) {
        int status = response.getStatus();
        String body = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        if (status == 404 || status == 400) {
            log.info("TC137 穿越请求被拒：status={}（安全实质通过，裁决9）", status);
            return;
        }
        // 200 分支：必须是 ApiResult 错误信封兜底且不含文件内容特征（R6 安全实质）
        String contentType = String.valueOf(response.getContentType());
        assertTrue(contentType.contains("application/json"),
                "穿越请求 200 时 contentType 应为 application/json 错误信封，实际=" + contentType);
        Object code = JsonPathUtils.readNullable(body, "$.code");
        assertTrue(code instanceof Number && ((Number) code).intValue() != 0,
                "穿越请求 200 时应为 ApiResult 错误信封（code!=0），实际 code=" + code);
        assertFalse(body.contains("spring:") || body.contains("datasource"),
                "穿越响应不得含文件内容特征（R6 安全实质）");
        log.info("TC137 穿越请求以错误信封兜底：status={}, code={}, bodyLen={}（安全实质通过，裁决9）",
                status, code, body.length());
    }

    private static byte[] bytesOf(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    /**
     * TC137 主干上传与访问（UUID 文件名 + 静态映射 + 防穿越）。
     * 规则1 png 100KB 上传 code=0，data.imageUrl=/images/{32 位 UUID}.png；
     * 规则2 磁盘 rootPath 存在该文件；GET /images/{fileName} 返回 200；
     * 规则3 防穿越安全实质断言（编排裁决9：真实容器实测取证——/images/../application.yml 等四变体
     * 均不返回任何文件字节，形态为 404 / 400 / 200+ApiResult 错误信封兜底）：穿越请求响应满足
     * ①HTTP 404；或 ②HTTP 400；或 ③HTTP 200 且 contentType=application/json 且 body 为 ApiResult
     * 错误信封（code!=0）且 body 不含 "spring:"/"datasource" 等文件内容特征——即通过。
     * 用例安全意图不变：穿越不得返回文件内容（R6）。
     */
    @Test
    void tc137UploadAndStaticAccess() throws Exception {
        String imageUrl = uploadOk("it_test.png", bytesOf(100 * 1024));
        String fileName = imageUrl.substring("/images/".length());

        // 规则2：静态映射可访问
        int staticStatus = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/images/{fileName}", fileName))
                .andReturn().getResponse().getStatus();
        assertEquals(200, staticStatus, "GET /images/{fileName} 应 200");

        // 规则3：穿越拒绝（安全实质断言，裁决9）
        org.springframework.mock.web.MockHttpServletResponse traversalResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/images/../application.yml"))
                .andReturn().getResponse();
        assertTraversalRejectedWithoutFileContent(traversalResponse);
    }

    /**
     * TC138 白名单与大小边界（5 格式合并 + 5MB 边界）。
     * 规则1 jpg/jpeg/png/gif/webp 各 1 笔小文件均 code=0；
     * 规则2 恰 5MB 的 png（上界含边界）也成功。
     */
    @Test
    void tc138WhitelistFormatsAnd5mbBoundary() throws Exception {
        for (String ext : new String[] {"jpg", "jpeg", "png", "gif", "webp"}) {
            uploadOk("it_fmt." + ext, bytesOf(1024));
        }
        uploadOk("it_exact_5mb.png", bytesOf((int) FIVE_MB));
    }

    /**
     * TC139 格式拒绝（1020×4 合并）。
     * 规则1 .txt / .bat / 无扩展名 / 伪造内容改扩展名 .exe 均 code=1020；
     * 规则2 磁盘无新文件（目录计数不变）。
     */
    @Test
    void tc139FormatRejectedNoDiskWrite() throws Exception {
        long before = dirFileCount();
        assertEquals(1020, uploadExpectCode("it_bad.txt", bytesOf(128)), ".txt 应 1020");
        assertEquals(1020, uploadExpectCode("it_bad.bat", bytesOf(128)), ".bat 应 1020");
        assertEquals(1020, uploadExpectCode("it_noext", bytesOf(128)), "无扩展名应 1020");
        // 伪造内容改扩展名：内容为 PNG 魔数但伪装 .exe——按原始文件名判扩展，仍 1020
        byte[] fakePngHeader = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        assertEquals(1020, uploadExpectCode("it_fake.exe", fakePngHeader), "伪装扩展名应按文件名判 1020");
        assertEquals(before, dirFileCount(), "全部失败磁盘无新文件");
    }

    /**
     * TC140 超限与缺失（1021/1020）。
     * 规则1 ①5MB+1B 文件 → 1021；②不携带 file 字段 → 1020；
     * 规则2 磁盘无文件。
     */
    @Test
    void tc140SizeExceededAndFileMissing() throws Exception {
        long before = dirFileCount();
        assertEquals(1021, uploadExpectCode("it_over.png", bytesOf((int) FIVE_MB + 1)), "超 5MB 应 1021");
        assertEquals(1020, uploadWithoutFileExpectCode(), "缺失 file 字段应 1020");
        assertEquals(before, dirFileCount(), "失败请求磁盘无文件");
    }

    /**
     * TC141 幂等-重复上传（非幂等-每次新文件）。
     * 规则1 同一文件二传均 code=0；
     * 规则2 两个不同 URL（UUID 不同）、磁盘两个文件（不覆盖）。
     */
    @Test
    void tc141RepeatUploadCreatesNewFileEachTime() throws Exception {
        byte[] content = bytesOf(2048);
        String first = uploadOk("it_dup.png", content);
        String second = uploadOk("it_dup.png", content);
        assertNotEquals(first, second, "两次上传应返回不同 URL（UUID 防覆盖）");
        assertEquals(2, uploadedFiles.stream().filter(Files::exists).count(), "磁盘应存在两个文件（不覆盖）");
    }

    /**
     * TC142 幂等-并发上传（文件名隔离）。
     * 规则1 并发 3 笔同文件均 code=0；
     * 规则2 3 个不同文件名互不覆盖（UUID 唯一性）。
     */
    @Test
    void tc142ConcurrentUploadFileIsolation() throws Exception {
        byte[] content = bytesOf(4096);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>();
        try {
            CountDownLatch ready = new CountDownLatch(3);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 3; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult result = mockMvc.perform(uploadBuilder()
                                        .file(new MockMultipartFile("file", "it_conc.png",
                                                "application/octet-stream", content)))
                                .andExpect(status().isOk()).andReturn();
                        if (codeOf(result) == 0) {
                            urls.add(strAt(result, "$.data.imageUrl"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("并发上传线程异常", e);
                    }
                });
            }
            ready.await();
            start.countDown();
            long awaitStart = System.currentTimeMillis();
            while (urls.size() < 3 && System.currentTimeMillis() - awaitStart < 30_000L) {
                Thread.sleep(50L);
            }
            assertEquals(3, urls.size(), "3 笔并发均应成功");
            assertEquals(3, new java.util.HashSet<>(urls).size(), "3 个文件名互不相同（UUID 唯一性）");
            for (String url : urls) {
                Path file = Paths.get(imageRootPath, url.substring("/images/".length()));
                assertTrue(Files.exists(file), "并发文件均应落盘：" + file);
                uploadedFiles.add(file);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * TC143 幂等-失败重试（失败无残留）。
     * 规则1 ①.txt 上传 → 1020 无落盘；②修正为 png 重试 → code=0 落盘。
     */
    @Test
    void tc143FailedThenFixedRetry() throws Exception {
        long before = dirFileCount();
        assertEquals(1020, uploadExpectCode("it_retry.txt", bytesOf(128)), ".txt 应 1020");
        assertEquals(before, dirFileCount(), "失败无落盘");
        uploadOk("it_retry.png", bytesOf(128));
    }
}
