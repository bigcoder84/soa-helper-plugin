package cn.bigcoder.soa.helper.search;

import com.intellij.util.xmlb.annotations.OptionTag;
import java.io.Serializable;
import java.util.Objects;

public class RpcMethodHistoryInfo implements Serializable {

    private static final long serialVersionUID = 7891236782163789L;

    @OptionTag
    private String methodName;
    @OptionTag
    private String className;

    public RpcMethodHistoryInfo(String methodName, String className) {
        this.methodName = methodName;
        this.className = className;
    }

    public RpcMethodHistoryInfo() {
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RpcMethodHistoryInfo other = (RpcMethodHistoryInfo) obj;
        return Objects.equals(methodName, other.methodName) && Objects.equals(className, other.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, className);
    }
}
