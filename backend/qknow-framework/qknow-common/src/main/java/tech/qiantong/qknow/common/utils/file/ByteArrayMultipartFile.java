package tech.qiantong.qknow.common.utils.file;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 内存字节数组 MultipartFile 实现
 * 替代测试框架中的 MockMultipartFile，避免生产依赖泄露
 *
 * @author qknow
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /**
     * 构造函数
     *
     * @param name 表单字段名
     * @param originalFilename 原始文件名
     * @param contentType 内容类型
     * @param content 字节内容
     */
    public ByteArrayMultipartFile(String name, @Nullable String originalFilename, @Nullable String contentType, @Nullable byte[] content) {
        this.name = (name != null ? name : "");
        this.originalFilename = (originalFilename != null ? originalFilename : "");
        this.contentType = contentType;
        this.content = (content != null ? content : new byte[0]);
    }

    /**
     * 便捷构造函数（表单名与原始文件名相同）
     */
    public ByteArrayMultipartFile(String filename, @Nullable String contentType, @Nullable byte[] content) {
        this(filename, filename, contentType, content);
    }

    @Override
    @NonNull
    public String getName() {
        return this.name;
    }

    @Override
    @NonNull
    public String getOriginalFilename() {
        return this.originalFilename;
    }

    @Override
    @Nullable
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public boolean isEmpty() {
        return this.content.length == 0;
    }

    @Override
    public long getSize() {
        return this.content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return this.content;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(this.content);
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        try (OutputStream out = new FileOutputStream(dest)) {
            FileCopyUtils.copy(this.content, out);
        }
    }

    @Override
    public void transferTo(@NonNull Path dest) throws IOException, IllegalStateException {
        Files.write(dest, this.content);
    }
}
