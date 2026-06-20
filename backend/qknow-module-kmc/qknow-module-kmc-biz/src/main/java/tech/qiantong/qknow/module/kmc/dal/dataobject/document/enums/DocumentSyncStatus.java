package tech.qiantong.qknow.module.kmc.dal.dataobject.document.enums;

/**
 * 知识文件跟踪
 * @author jw
 * @date 2025/06/26 16:54
 * @return
 */
public enum DocumentSyncStatus {
    STAY(0, "待同步"),
    IN(1, "同步中"),
    SUCCESS(2, "同步成功"),
    ERROR(3, "同步失败"),

    ;

    public final Integer code;
    public final String info;

    DocumentSyncStatus(Integer code, String info) {
        this.code = code;
        this.info = info;
    }

    public static DocumentSyncStatus get(Integer code) {
        for (DocumentSyncStatus v : values()) {
            if (v.eq(code)) {
                return v;
            }
        }
        return null;
    }

    public boolean eq(Integer code) {
        return this.code.equals(code);
    }

    public static String getInfo(Integer code) {
        return DocumentSyncStatus.get(code).getInfo();
    }

    public static Integer getCode(String info) {
        for (DocumentSyncStatus v : values()) {
            if (v.getInfo().equals(info)) {
                return v.getCode();
            }
        }
        return null;
    }

    public Integer getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

}
