package net.linlan.dsh.domain.tool.fs.adapter.port;

import java.util.List;

/**
 * IFsPort 定义IFsF列表的领域端口，由基础设施层适配。
 */
public interface IFsPort {

    String read(String path, int offset, int limit);

    void write(String path, String content);

    List<String> search(String directory, String pattern, int maxResults);

    boolean exists(String path);

    List<DirEntry> listDir(String directory);

    /**
     * 按内容搜索（grep 语义）。
     *
     * @param directory  根目录
     * @param pattern    正则或字面量
     * @param include    可选 glob 限定文件（如 *.java），null 表示全部文本文件
     * @param isRegex    是否按正则解释 pattern
     * @param maxResults 最大匹配行数
     * @return 匹配行列表，格式 path:line:content
     */
    List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults);

    /**
     * 读取二进制文件字节（用于图片等多模态内容）。
     */
    byte[] readBytes(String path);

    /**
     * DirEntry 定义当前模块中的record契约与职责。
     */
    record DirEntry(String name, String type, long size) {}
}
