package cn.bigcoder.soa.helper.search;

/**
 * @param methodName 方法名称
 * @param className 类全限定名称
 * @param filePath 文件路径
 * @param textOffset 方法在文件中的偏移量
 */
public record RpcMethodInfo(String methodName,
                            String className,
                            String filePath,
                            int textOffset) {

}
