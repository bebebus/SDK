package cloud.cniia.openapi.sdk;

/** v2 请求绑定：HTTP method（大写）+ 规范路径。 */
public final class SignBinding {
    private final String method;
    private final String path;

    public SignBinding(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }
}
