package cn.bigcoder.soa.helper.variable;

/**
 * 变量解析异常 —— 用于区分网络错误、超时、配置缺失等情况
 */
public class VariableResolveException extends Exception {
    public VariableResolveException(String message) {
        super(message);
    }

    public VariableResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
