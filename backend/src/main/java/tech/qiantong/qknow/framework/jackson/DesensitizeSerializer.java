package tech.qiantong.qknow.framework.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * 针对 @Desensitize 注解实现 ContextualSerializer 的通用脱敏序列化器
 */
public class DesensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private DesensitizeType type;

    public DesensitizeSerializer() {}

    public DesensitizeSerializer(DesensitizeType type) {
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isEmpty()) {
            gen.writeString(value);
            return;
        }
        String desensitizedValue = value;
        if (type != null) {
            switch (type) {
                case PHONE:
                    desensitizedValue = value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
                    break;
                case ID_CARD:
                    desensitizedValue = value.replaceAll("(\\d{4})\\d{10}(\\w{4})", "$1**********$2");
                    break;
                case API_KEY:
                case PASSWORD:
                    desensitizedValue = "******";
                    break;
                case EMAIL:
                    int index = value.indexOf("@");
                    if (index > 1) {
                        desensitizedValue = value.charAt(0) + "***" + value.substring(index - 1);
                    } else if (index == 1) {
                        desensitizedValue = value.charAt(0) + "***" + value.substring(index);
                    }
                    break;
            }
        }
        gen.writeString(desensitizedValue);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            Desensitize annotation = property.getAnnotation(Desensitize.class);
            if (annotation == null) {
                annotation = property.getContextAnnotation(Desensitize.class);
            }
            if (annotation != null) {
                return new DesensitizeSerializer(annotation.type());
            }
        }
        return this;
    }
}
